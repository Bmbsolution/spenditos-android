package com.spenditos.android.data.scan

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanProgress
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.data.realtime.RealtimeEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level owner of "scans that outlive the Scan screen" (iOS `ScanCoordinator`):
 *  - the list behind Home's scan card (`GET /scans?status=processing,ready`);
 *  - scans the user left to finish in the background ("Notify me when it's ready");
 *  - scans the user cancelled while still reading (hidden, then discarded once they are ready);
 *  - `POST /scans/{id}/complete` calls that could not be sent yet.
 *
 * How it learns a scan is ready (the server sends no Android push): it re-reads the list when the realtime socket
 * reports `user.data_changed` for `aggregateType == "scan"`, when the socket reconnects, and when the app comes to the
 * foreground; Home also polls while a scan is processing ([pollUntilIdle]).
 *
 * Everything is per account: the in-memory list and the persisted sets are reset when another user signs in.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val scans: ScanActions,
    private val store: ScanStateStore,
    private val session: ScanSession,
    private val events: RealtimeEvents,
    private val reconnects: ReconnectSignals,
    private val foreground: ForegroundMonitor,
    private val time: ScanTimeSource,
    @ScanScope private val scope: CoroutineScope,
) {
    data class State(
        /** Scans to show on Home (processing and ready). */
        val scans: List<ScanSummary> = emptyList(),
        /** Scans the user cancelled while reading. Hidden from Home and discarded as soon as they are ready. */
        val dismissed: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The cards Home shows, in order. */
    val cards: Flow<List<ScanCardState>> = _state.map { ScanCardState.cards(it.scans, it.dismissed) }

    val hasProcessing: Boolean
        get() = _state.value.let { s -> s.scans.any { it.state == ScanStatus.PROCESSING && it.scanId !in s.dismissed } }

    private val refreshLock = Mutex()
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private var owner: String? = null
    private var pending: Map<String, String> = emptyMap()

    /** Scans started on this device that the list endpoint has not returned yet (scanId -> started at). */
    private val placeholders = ConcurrentHashMap<String, Long>()

    /** The detail a Home card just resolved, so the review screen opens without a second request. */
    private val handoff = ConcurrentHashMap<String, ScanDetail>()

    /** Starts the listeners once per process. Safe to call from every screen that shows scans. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch { session.userIds.collect { adoptOwner() } }
        scope.launch { events.scanUpdates.collect { schedule() } }
        scope.launch { reconnects.reconnects.collect { schedule() } }
        scope.launch { foreground.isForeground.collect { if (it) schedule() } }
        scope.launch { triggers.receiveAsFlow().debounce(COALESCE_MILLIS).collect { refresh() } }
    }

    internal fun schedule() {
        triggers.trySend(Unit)
    }

    /** Resets everything when the signed-in account changed (or signed out). */
    @Synchronized
    private fun adoptOwner(): String? {
        val current = session.userId
        if (current != owner) {
            owner = current
            placeholders.clear()
            handoff.clear()
            pending = current?.let(store::pendingCompletions).orEmpty()
            _state.value = State(dismissed = current?.let(store::dismissed).orEmpty())
        }
        return current
    }

    // region Refresh

    /** Reload the Home list. Failures keep the previous list (the card is a convenience, never a reason to show an error). */
    suspend fun refresh() = refreshLock.withLock {
        val user = adoptOwner() ?: return@withLock
        flushPendingCompletions(user)

        val latest = try {
            scans.list(listOf(ScanStatus.PROCESSING, ScanStatus.READY))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock
        }
        if (session.userId != user) return@withLock

        val merged = latest.toMutableList()
        val now = time.elapsedMillis()
        val known = latest.mapTo(HashSet()) { it.scanId }
        for ((scanId, startedAt) in placeholders.entries.toList()) {
            if (scanId in known) {
                placeholders.remove(scanId)
            } else if (now - startedAt > PLACEHOLDER_GRACE_MILLIS) {
                placeholders.remove(scanId)
            } else {
                _state.value.scans.firstOrNull { it.scanId == scanId }?.let { merged.add(0, it) }
            }
        }
        _state.update { it.copy(scans = merged) }

        // Cancelled scans clean themselves up once they finish.
        val dismissed = _state.value.dismissed
        for (scan in latest) {
            if (scan.state == ScanStatus.READY && scan.scanId in dismissed) complete(scan.scanId, ScanOutcome.DISCARDED)
        }
        if (dismissed.size > PRUNE_DISMISSED_AT) {
            val keep = dismissed intersect latest.mapTo(HashSet()) { it.scanId }
            _state.update { it.copy(dismissed = keep) }
            store.saveDismissed(user, keep)
        }
    }

    /** Refresh, then keep refreshing every few seconds while a scan is still being read. Cancel the surrounding job to stop. */
    suspend fun pollUntilIdle() {
        while (currentCoroutineContext().isActive) {
            refresh()
            if (!hasProcessing) return
            time.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    // endregion

    // region Starting / leaving a scan

    /** The user left the Scan screen while a scan is still reading: show it on Home right away. */
    fun trackBackground(scanId: String, sourceLabel: String, progress: ScanProgress) {
        val user = adoptOwner() ?: return
        val summary = ScanSummary(scanId = scanId, status = "processing", progress = progress, sourceLabel = sourceLabel)
        _state.update { it.copy(scans = listOf(summary) + it.scans.filterNot { s -> s.scanId == scanId }, dismissed = it.dismissed - scanId) }
        placeholders[scanId] = time.elapsedMillis()
        store.saveDismissed(user, _state.value.dismissed)
    }

    /** The user cancelled while the scan was still being read: hide it, and discard it once it finishes. */
    fun dismiss(scanId: String) {
        val user = adoptOwner() ?: return
        _state.update { it.copy(scans = it.scans.filterNot { s -> s.scanId == scanId }, dismissed = it.dismissed + scanId) }
        placeholders.remove(scanId)
        store.saveDismissed(user, _state.value.dismissed)
    }

    // endregion

    // region Completion

    /**
     * Close a scan on the server (`saved` after the last chunk, `discarded` on cancel). Best effort: if the call cannot
     * be sent it is remembered and retried on the next refresh, so a saved scan eventually leaves Home.
     */
    suspend fun complete(scanId: String, outcome: ScanOutcome) {
        val user = adoptOwner()
        _state.update { it.copy(scans = it.scans.filterNot { s -> s.scanId == scanId }) }
        placeholders.remove(scanId)
        handoff.remove(scanId)
        try {
            scans.complete(scanId, outcome)
            if (user != null) forgetPending(user, scanId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (user != null) rememberPending(user, scanId, outcome)
        }
    }

    /** [complete] that survives the calling screen going away (the review screen closes right after saving). */
    fun completeInBackground(scanId: String, outcome: ScanOutcome) {
        scope.launch { complete(scanId, outcome) }
    }

    private suspend fun flushPendingCompletions(user: String) {
        if (pending.isEmpty()) return
        for ((scanId, wire) in pending.entries.toList()) {
            val outcome = ScanOutcome.fromWire(wire)
            if (outcome == null) {
                forgetPending(user, scanId)
                continue
            }
            try {
                scans.complete(scanId, outcome)
                forgetPending(user, scanId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiError.Server) {
                // Expired or in a state that no longer accepts it: nothing to retry.
                if (error.status == 404 || error.status == 409) forgetPending(user, scanId)
            } catch (_: Exception) {
                continue
            }
        }
    }

    @Synchronized
    private fun rememberPending(user: String, scanId: String, outcome: ScanOutcome) {
        if (session.userId != user) return
        pending = pending + (scanId to outcome.wire)
        store.savePendingCompletions(user, pending)
    }

    @Synchronized
    private fun forgetPending(user: String, scanId: String) {
        if (session.userId != user || scanId !in pending) return
        pending = pending - scanId
        store.savePendingCompletions(user, pending)
    }

    // endregion

    // region Opening

    /**
     * Fetch a scan and decide where it leads (single-receipt prefill or the review list). A list scan is kept for
     * [takeDetail] so the review screen does not fetch it again. Throws a [ScanFailure] the screen can show.
     */
    suspend fun resolve(scanId: String): ScanResolution {
        adoptOwner()
        val resolution = scans.get(scanId).resolveForOpening()
        if (resolution is ScanResolution.Review) handoff[scanId] = resolution.detail
        return resolution
    }

    /** The detail [resolve] fetched for this scan, once. */
    fun takeDetail(scanId: String): ScanDetail? = handoff.remove(scanId)

    // endregion

    private companion object {
        const val COALESCE_MILLIS = 250L
        const val POLL_INTERVAL_MILLIS = 5_000L
        const val PLACEHOLDER_GRACE_MILLIS = 20_000L
        const val PRUNE_DISMISSED_AT = 50
    }
}
