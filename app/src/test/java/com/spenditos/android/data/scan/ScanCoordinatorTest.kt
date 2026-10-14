package com.spenditos.android.data.scan

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanProgress
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.RealtimeEvents
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.testing.FakeScanActions
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.InMemoryScanStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanCoordinatorTest {
    private lateinit var scans: FakeScanActions
    private lateinit var store: InMemoryScanStore
    private lateinit var session: FakeScanSession
    private lateinit var time: FakeScanTime
    private lateinit var events: RealtimeEvents
    private val reconnectFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    private val foreground = MutableStateFlow(false)
    private lateinit var coordinator: ScanCoordinator

    private fun summary(id: String, status: String, items: Int = 0, label: String = "3 files", done: Int = 0, total: Int = 0) =
        ScanSummary(scanId = id, status = status, itemCount = items, sourceLabel = label, progress = ScanProgress(done, total))

    @Before
    fun setUp() {
        scans = FakeScanActions()
        store = InMemoryScanStore()
        session = FakeScanSession("user-a")
        time = FakeScanTime()
        events = RealtimeEvents(FinanceInvalidationBus())
    }

    private fun TestScope.newCoordinator(): ScanCoordinator {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator = ScanCoordinator(
            scans = scans,
            store = store,
            session = session,
            events = events,
            reconnects = object : ReconnectSignals {
                override val reconnects: SharedFlow<Unit> = reconnectFlow
            },
            foreground = object : ForegroundMonitor {
                override val isForeground: StateFlow<Boolean> = foreground
            },
            time = time,
            scope = scope.backgroundScope,
        )
        return coordinator
    }

    // region Cards

    @Test
    fun `a processing scan reads and a ready scan asks for review`() {
        val cards = ScanCardState.cards(
            listOf(
                summary("a", "processing", label = "3 screenshots", done = 1, total = 3),
                summary("b", "ready", items = 6, label = "Visa.pdf"),
                summary("c", "ready", items = 1, label = ""),
            ),
        )
        assertEquals(listOf("b", "c", "a"), cards.map { it.scanId })
        assertEquals("6 transactions ready to review", cards[0].title)
        assertEquals("From Visa.pdf", cards[0].subtitle)
        assertEquals("1 transaction ready to review", cards[1].title)
        assertNull(cards[1].subtitle)
        assertEquals("Reading 3 screenshots…", cards[2].title)
        assertEquals("1 of 3", cards[2].subtitle)
        assertEquals(ScanCardState.Phase.READY, cards[0].phase)
        assertEquals(ScanCardState.Phase.READING, cards[2].phase)
    }

    @Test
    fun `failed saved discarded unknown and hidden scans show no card and only three show at once`() {
        val list = listOf(
            summary("f", "failed"), summary("s", "saved"), summary("d", "discarded"), summary("u", "from-the-future"),
            summary("hidden", "ready", 2), summary("r1", "ready", 1), summary("r2", "ready", 1), summary("r3", "ready", 1),
            summary("r4", "ready", 1), summary("p", "processing"),
        )
        assertEquals(listOf("r1", "r2", "r3"), ScanCardState.cards(list, hidden = setOf("hidden")).map { it.scanId })
        assertEquals("Reading your scan…", ScanCardState.card(summary("x", "processing", label = " ")).title)
        assertNull(ScanCardState.card(summary("x", "processing")).subtitle)
    }

    // endregion

    @Test
    fun `refresh loads the list and a failure keeps the previous one`() = runTest {
        val c = newCoordinator()
        scans.listResult = { listOf(summary("a", "ready", 2)) }
        c.refresh()
        assertEquals(listOf("a"), c.state.value.scans.map { it.scanId })

        scans.listResult = { throw ApiError.Network(java.io.IOException("offline")) }
        c.refresh()
        assertEquals(listOf("a"), c.state.value.scans.map { it.scanId })
        assertFalse(c.hasProcessing)
    }

    @Test
    fun `a scan left in the background shows at once and survives a list that does not know it yet`() = runTest {
        val c = newCoordinator()
        c.trackBackground("new", "Visa.pdf", ScanProgress(2, 9))
        assertEquals("Reading Visa.pdf…", ScanCardState.cards(c.state.value.scans).single().title)
        assertTrue(c.hasProcessing)

        scans.listResult = { emptyList() }
        time.now = 5_000
        c.refresh()
        assertEquals(listOf("new"), c.state.value.scans.map { it.scanId })

        // After the grace period an unknown scan is dropped.
        time.now = 30_000
        c.refresh()
        assertTrue(c.state.value.scans.isEmpty())
    }

    @Test
    fun `a scan the server now lists replaces its placeholder`() = runTest {
        val c = newCoordinator()
        c.trackBackground("new", "Visa.pdf", ScanProgress(0, 3))
        scans.listResult = { listOf(summary("new", "ready", 5, "Visa.pdf")) }
        c.refresh()
        assertEquals(listOf(ScanStatus.READY), c.state.value.scans.map { it.state })
        // The placeholder is gone for good: a later empty list does not resurrect it.
        scans.listResult = { emptyList() }
        c.refresh()
        assertTrue(c.state.value.scans.isEmpty())
    }

    @Test
    fun `a cancelled scan is hidden and discarded once it is ready`() = runTest {
        val c = newCoordinator()
        c.dismiss("gone")
        assertEquals(setOf("gone"), store.dismissedByUser["user-a"])
        scans.listResult = { listOf(summary("gone", "processing"), summary("other", "ready", 1)) }
        c.refresh()
        assertEquals(listOf("other"), ScanCardState.cards(c.state.value.scans, c.state.value.dismissed).map { it.scanId })
        assertFalse(c.hasProcessing)
        assertTrue(scans.completed.isEmpty())

        scans.listResult = { listOf(summary("gone", "ready", 4)) }
        c.refresh()
        assertEquals(listOf("gone" to ScanOutcome.DISCARDED), scans.completed)
        assertTrue(c.state.value.scans.isEmpty())
    }

    @Test
    fun `complete removes the card and an unsent completion is retried on the next refresh`() = runTest {
        val c = newCoordinator()
        scans.listResult = { listOf(summary("a", "ready", 2)) }
        c.refresh()

        scans.completeError = ApiError.Network(java.io.IOException("offline"))
        c.complete("a", ScanOutcome.SAVED)
        assertTrue(c.state.value.scans.isEmpty())
        assertEquals(mapOf("a" to "saved"), store.pendingByUser["user-a"])

        scans.completeError = null
        scans.listResult = { emptyList() }
        c.refresh()
        assertEquals(listOf("a" to ScanOutcome.SAVED), scans.completed)
        assertTrue(store.pendingByUser["user-a"].orEmpty().isEmpty())
    }

    @Test
    fun `a pending completion the server no longer accepts is dropped`() = runTest {
        store.pendingByUser["user-a"] = mapOf("old" to "saved", "junk" to "???")
        val c = newCoordinator()
        scans.completeError = ApiError.Server(404, "Scan not found")
        c.refresh()
        assertTrue(store.pendingByUser["user-a"].orEmpty().isEmpty())
    }

    @Test
    fun `state is per account`() = runTest {
        store.dismissedByUser["user-b"] = setOf("b-scan")
        val c = newCoordinator()
        scans.listResult = { listOf(summary("a", "ready", 2)) }
        c.refresh()
        c.dismiss("a-dismissed")
        assertEquals(1, c.state.value.scans.size)

        session.signIn("user-b")
        scans.listResult = { emptyList() }
        c.refresh()
        assertTrue(c.state.value.scans.isEmpty())
        assertEquals(setOf("b-scan"), c.state.value.dismissed)
        assertEquals(setOf("a-dismissed"), store.dismissedByUser["user-a"])

        val callsBeforeSignOut = scans.listCalls
        session.signIn(null)
        c.refresh()
        assertTrue(c.state.value.scans.isEmpty())
        // Signed out: no request at all.
        assertEquals(callsBeforeSignOut, scans.listCalls)
    }

    @Test
    fun `poll until idle keeps refreshing every five seconds while a scan is processing`() = runTest {
        val c = newCoordinator()
        var round = 0
        scans.listResult = { if (++round < 4) listOf(summary("a", "processing", done = round, total = 5)) else listOf(summary("a", "ready", 5)) }
        c.pollUntilIdle()
        assertEquals(4, scans.listCalls)
        assertEquals(listOf(5_000L, 5_000L, 5_000L), time.sleeps)
        assertFalse(c.hasProcessing)
    }

    @Test
    fun `poll until idle returns at once when nothing is processing`() = runTest {
        val c = newCoordinator()
        scans.listResult = { listOf(summary("a", "ready", 2)) }
        c.pollUntilIdle()
        assertEquals(1, scans.listCalls)
        assertTrue(time.sleeps.isEmpty())
    }

    // region Triggers

    private fun scanEvent(id: String, scanId: String = "scn_1", mutation: String = "updated", type: String = "scan") =
        """{"type":"user.data_changed","data":{"eventId":"$id","aggregateType":"$type","aggregateId":"$scanId","mutationType":"$mutation","occurredAt":"2026-10-11T10:00:00.000Z","version":1}}"""

    @Test
    fun `a realtime scan event re-reads the list once even if delivered twice`() = runTest {
        val c = newCoordinator()
        c.start()
        scans.listResult = { listOf(summary("scn_1", "ready", 3)) }
        runCurrent()
        val before = scans.listCalls

        events.accept(scanEvent("evt-1"))
        events.accept(scanEvent("evt-1"))
        advanceTimeBy(300)
        runCurrent()

        assertEquals(before + 1, scans.listCalls)
        assertEquals(listOf(ScanStatus.READY), c.state.value.scans.map { it.state })
    }

    @Test
    fun `other aggregates and unknown mutations do not refresh scans`() = runTest {
        val c = newCoordinator()
        c.start()
        runCurrent()
        val before = scans.listCalls
        events.accept(scanEvent("evt-2", type = "transaction"))
        events.accept(scanEvent("evt-3", mutation = "exploded"))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(before, scans.listCalls)
    }

    @Test
    fun `a socket reconnect and the app coming to the foreground re-read the list`() = runTest {
        val c = newCoordinator()
        c.start()
        runCurrent()
        val before = scans.listCalls

        reconnectFlow.tryEmit(Unit)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(before + 1, scans.listCalls)

        foreground.value = true
        advanceTimeBy(300)
        runCurrent()
        assertEquals(before + 2, scans.listCalls)
    }

    @Test
    fun `triggers that arrive together coalesce into one request`() = runTest {
        val c = newCoordinator()
        c.start()
        runCurrent()
        val before = scans.listCalls
        reconnectFlow.tryEmit(Unit)
        events.accept(scanEvent("evt-9"))
        foreground.value = true
        advanceTimeBy(300)
        runCurrent()
        assertEquals(before + 1, scans.listCalls)
    }

    // endregion

    // region Opening

    @Test
    fun `resolving a list scan keeps its detail for the review screen once`() = runTest {
        val c = newCoordinator()
        val detail = ScanDetail(summary("scn_2", "ready", 2), listOf(ScanItem("a"), ScanItem("b")).let { it })
        scans.getResult = { detail }
        val resolution = c.resolve("scn_2")
        assertTrue(resolution is ScanResolution.Review)
        assertEquals(detail, c.takeDetail("scn_2"))
        assertNull(c.takeDetail("scn_2"))
    }

    @Test
    fun `resolving a single receipt returns its item and a saved scan says so`() = runTest {
        val c = newCoordinator()
        scans.getResult = { ScanDetail(ScanSummary("scn_3", "ready", kind = "receipt", itemCount = 1), listOf(ScanItem("only", amount = 38.74))) }
        val receipt = c.resolve("scn_3") as ScanResolution.Receipt
        assertEquals(38.74, receipt.item.amount!!, 0.0)
        assertNull(c.takeDetail("scn_3"))

        scans.getResult = { ScanDetail(summary("scn_4", "saved"), null) }
        assertEquals(ScanFailure.AlreadySaved, runCatching { c.resolve("scn_4") }.exceptionOrNull())
    }

    // endregion
}
