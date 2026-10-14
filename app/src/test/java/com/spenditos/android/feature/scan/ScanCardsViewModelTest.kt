package com.spenditos.android.feature.scan

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.RealtimeEvents
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.testing.FakeScanActions
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.InMemoryScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanCardsViewModelTest {
    private lateinit var scans: FakeScanActions
    private lateinit var coordinator: ScanCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        scans = FakeScanActions()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel(): ScanCardsViewModel {
        coordinator = ScanCoordinator(
            scans, InMemoryScanStore(), FakeScanSession(), RealtimeEvents(FinanceInvalidationBus()),
            object : ReconnectSignals {
                override val reconnects: SharedFlow<Unit> = MutableSharedFlow()
            },
            object : ForegroundMonitor {
                override val isForeground: StateFlow<Boolean> = MutableStateFlow(false)
            },
            FakeScanTime(), backgroundScope,
        )
        return ScanCardsViewModel(coordinator)
    }

    @Test
    fun `a ready receipt opens the add form values and closes the scan as saved`() = runTest {
        val vm = newViewModel()
        scans.getResult = {
            ScanDetail(ScanSummary("scn_3", "ready", kind = "receipt", itemCount = 1), listOf(ScanItem("only", amount = 38.74, merchantName = "Metro", description = "groceries", date = "2026-10-10", categoryHint = "groceries")))
        }
        val event = async { vm.events.first() }

        vm.open("scn_3")
        advanceUntilIdle()
        runCurrent()

        val prefill = (event.await() as ScanCardEvent.OpenReceipt).prefill
        assertEquals(38.74, prefill.amount!!, 0.0)
        assertEquals("Metro", prefill.description)
        assertEquals("groceries", prefill.categoryHint)
        assertEquals(listOf("scn_3" to ScanOutcome.SAVED), scans.completed)
    }

    @Test
    fun `a ready list opens its review and is not completed`() = runTest {
        val vm = newViewModel()
        scans.getResult = { ScanDetail(ScanSummary("scn_2", "ready", kind = "list", itemCount = 2), listOf(ScanItem("a"), ScanItem("b"))) }
        val event = async { vm.events.first() }

        vm.open("scn_2")
        advanceUntilIdle()

        assertEquals(ScanCardEvent.OpenReview("scn_2"), event.await())
        assertTrue(scans.completed.isEmpty())
        assertEquals(2, coordinator.takeDetail("scn_2")!!.items!!.size)
    }

    @Test
    fun `a scan that cannot be opened says why and re-reads the list`() = runTest {
        val vm = newViewModel()
        scans.getResult = { ScanDetail(ScanSummary("scn_4", "saved"), null) }
        val event = async { vm.events.first() }
        val before = scans.listCalls

        vm.open("scn_4")
        advanceUntilIdle()

        assertEquals(ScanCardEvent.Message("Those transactions were already added."), event.await())
        assertEquals(before + 1, scans.listCalls)

        scans.getResult = { throw ApiError.Network(java.io.IOException("offline")) }
        val offline = async { vm.events.first() }
        vm.open("scn_4")
        advanceUntilIdle()
        assertEquals(ScanCardEvent.Message("You're offline. Reconnect to open this scan."), offline.await())
    }

    @Test
    fun `cards follow the coordinator and only processing scans keep Home polling`() = runTest {
        val vm = newViewModel()
        val seen = async { vm.cards.first { it.isNotEmpty() } }
        val polling = async { vm.hasProcessing.first { it } }
        scans.listResult = { listOf(ScanSummary("a", "processing", sourceLabel = "Visa.pdf")) }
        coordinator.refresh()
        runCurrent()
        assertEquals("Reading Visa.pdf…", seen.await().single().title)
        assertTrue(polling.await())
    }
}
