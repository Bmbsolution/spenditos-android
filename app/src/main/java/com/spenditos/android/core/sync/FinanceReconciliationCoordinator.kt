package com.spenditos.android.core.sync

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.ReconnectSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lifetime reconciliation, port of iOS `FinanceDataReconciliationCoordinator`: coming
 * back to the foreground or regaining the realtime socket invalidates every finance
 * aggregate so changes made elsewhere while the app was disconnected (the socket is
 * foreground-only and closes in the background) cannot leave screens like Home stale.
 * The shared category cache Home reads is refreshed here because no screen owns it.
 *
 * Works without any WebSocket configuration — the foreground trigger is independent of the
 * socket. Duplicate triggers coalesce (250 ms) and every refresh is best-effort and
 * failure-isolated: reconciliation never throws and never blocks the UI.
 */
@Singleton
class FinanceReconciliationCoordinator @Inject constructor(
    private val session: SessionManager,
    private val foreground: ForegroundMonitor,
    private val reconnects: ReconnectSignals,
    private val invalidations: FinanceInvalidationBus,
    private val categories: CategoryRepository,
    @ReconciliationScope private val scope: CoroutineScope,
) {
    // Conflated: triggers that arrive while a flush is pending merge into one reconciliation.
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    /** Starts once per process. The initial foreground counts, like iOS's scenePhase change at launch. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            foreground.isForeground.collect { isForeground -> if (isForeground) schedule() }
        }
        scope.launch {
            reconnects.reconnects.collect { schedule() }
        }
        scope.launch {
            triggers.receiveAsFlow().debounce(COALESCE_MS).collect { flush() }
        }
    }

    internal fun schedule() {
        triggers.trySend(Unit)
    }

    private suspend fun flush() {
        if (session.currentUserId == null) return
        runCatching { categories.refresh() }
        invalidations.invalidate(*Aggregate.entries.toTypedArray())
    }

    private companion object {
        const val COALESCE_MS = 250L
    }
}
