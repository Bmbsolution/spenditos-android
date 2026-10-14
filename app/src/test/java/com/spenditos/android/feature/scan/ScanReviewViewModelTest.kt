package com.spenditos.android.feature.scan

import androidx.lifecycle.SavedStateHandle
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.merchants.RuleLearner
import com.spenditos.android.testing.FakeMerchantMappings
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.RealtimeEvents
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanFailure
import com.spenditos.android.data.scan.ScanSaveFailure
import com.spenditos.android.data.scan.ScanSaveItem
import com.spenditos.android.data.scan.ScanSaveOutcome
import com.spenditos.android.testing.FakeScanActions
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.InMemoryScanStore
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ScanReviewViewModelTest {
    private lateinit var scans: FakeScanActions
    private lateinit var coordinator: ScanCoordinator
    private lateinit var mappings: FakeMerchantMappings
    private val online = MutableStateFlow(true)
    private val categories = MutableStateFlow(
        listOf(
            Category("cat_dining", "u", "Dining", "#000000", isIncome = false),
            Category("cat_salary", "u", "Salary", "#000000", isIncome = true),
        ),
    )
    private val dataSource = object : CaptureDataSource {
        override fun categories() = categories
        override fun currency() = MutableStateFlow("CAD")
        override val currentUserId = "user-a"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        scans = FakeScanActions()
        mappings = FakeMerchantMappings()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun row(id: String, amount: Double? = 10.0, date: String? = "2026-10-09", skip: String? = null, vararg more: Pair<String, Any?>) =
        ScanItem(id = id, amount = amount, date = date, description = "Row $id", skipReason = skip, categoryId = "cat_dining")

    private fun detail(vararg items: ScanItem, status: String = "ready", failedDocuments: Int = 0) =
        ScanDetail(ScanSummary("scn_1", status, kind = "list", itemCount = items.size, failedDocuments = failedDocuments), items.toList())

    private fun TestScope.newViewModel(handoff: ScanDetail? = null): ScanReviewViewModel {
        coordinator = ScanCoordinator(
            scans = scans,
            store = InMemoryScanStore(),
            session = FakeScanSession(),
            events = RealtimeEvents(FinanceInvalidationBus()),
            reconnects = object : ReconnectSignals {
                override val reconnects: SharedFlow<Unit> = MutableSharedFlow()
            },
            foreground = object : ForegroundMonitor {
                override val isForeground: StateFlow<Boolean> = MutableStateFlow(false)
            },
            time = FakeScanTime(),
            scope = backgroundScope,
        )
        handoff?.let(coordinator::handOff)
        return ScanReviewViewModel(
            scans, coordinator, dataSource, mappings, RuleLearner(mappings),
            object : ConnectivityStatus { override val isOnline: StateFlow<Boolean> = online },
            SavedStateHandle(mapOf(ScanReviewViewModel.ARG_SCAN_ID to "scn_1")),
        )
    }

    // region Loading

    @Test
    fun `the handed over scan opens without a second request and rows start checked or not`() = runTest {
        scans.getResult = { error("must not fetch") }
        val vm = newViewModel(detail(row("a"), row("b", skip = "duplicate"), row("c", skip = "rule"), failedDocuments = 2))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ScanReviewPhase.REVIEW, state.phase)
        assertEquals(listOf(true, false, false), state.items.map { it.include })
        assertEquals(3, state.foundCount)
        assertEquals(2, state.flaggedCount)
        assertEquals(2, state.failedDocuments)
        assertEquals("Add 1", state.saveButtonText)
        assertEquals("CAD", state.currency)
        // The hint resolved against the user's categories.
        assertEquals("cat_dining", state.items[0].categoryId)
    }

    @Test
    fun `without a handoff the scan is fetched, as after process death`() = runTest {
        scans.getResult = { detail(row("a")) }
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals(ScanReviewPhase.REVIEW, vm.state.value.phase)
    }

    @Test
    fun `a scan that cannot be reviewed explains why`() = runTest {
        scans.getResult = { detail(row("a"), status = "saved") }
        assertEquals("Those transactions were already added.", newViewModel().also { advanceUntilIdle() }.state.value.loadError)

        scans.getResult = { ScanDetail(ScanSummary("scn_1", "failed", error = "boom"), null) }
        assertEquals("boom", newViewModel().also { advanceUntilIdle() }.state.value.loadError)

        scans.getResult = { ScanDetail(ScanSummary("scn_1", "ready"), emptyList()) }
        assertEquals("No transactions were found in this scan.", newViewModel().also { advanceUntilIdle() }.state.value.loadError)

        scans.getResult = { throw ApiError.Network(java.io.IOException("offline")) }
        val vm = newViewModel().also { advanceUntilIdle() }
        assertEquals(ScanReviewPhase.ERROR, vm.state.value.phase)
        assertEquals("You're offline. Reconnect to open this scan.", vm.state.value.loadError)

        scans.getResult = { throw ApiError.Server(404, "Scan not found") }
        assertEquals("This scan is no longer available.", newViewModel().also { advanceUntilIdle() }.state.value.loadError)
    }

    @Test
    fun `retrying a failed open loads the scan`() = runTest {
        scans.getResult = { throw ApiError.Network(java.io.IOException("offline")) }
        val vm = newViewModel().also { advanceUntilIdle() }
        scans.getResult = { detail(row("a")) }
        vm.load()
        advanceUntilIdle()
        assertEquals(ScanReviewPhase.REVIEW, vm.state.value.phase)
    }

    // endregion

    // region Editing and validation

    @Test
    fun `a row without an amount or date blocks saving until it is fixed or unchecked`() = runTest {
        val vm = newViewModel(detail(row("a"), row("b", amount = null), row("c", date = null)))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.incompleteCount)
        assertFalse(vm.state.value.canSave)

        vm.onAmountChange("b", "12.5")
        vm.onDateChange("c", LocalDate.of(2026, 10, 1))
        assertEquals(0, vm.state.value.incompleteCount)
        assertTrue(vm.state.value.canSave)

        vm.onAmountChange("b", "")
        assertFalse(vm.state.value.canSave)
        vm.toggleInclude("b")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `nothing included cannot be saved and a switched type drops a mismatched category`() = runTest {
        val vm = newViewModel(detail(row("a")))
        advanceUntilIdle()
        vm.toggleInclude("a")
        assertFalse(vm.state.value.canSave)
        vm.toggleInclude("a")

        vm.onTypeChange("a", income = true)
        assertNull(vm.state.value.items[0].categoryId)
        vm.onCategoryChange("a", "cat_dining") // an expense category on an income row is refused
        assertNull(vm.state.value.items[0].categoryId)
        vm.onCategoryChange("a", "cat_salary")
        assertEquals("cat_salary", vm.state.value.items[0].categoryId)
        assertEquals(0, vm.state.value.missingCategoryCount)
    }

    @Test
    fun `picking another category clears the rule that filed the row`() = runTest {
        val vm = newViewModel(detail(ScanItem(id = "a", amount = 3.0, date = "2026-10-01", categoryId = "cat_dining", ruleId = "r1")))
        advanceUntilIdle()
        assertEquals("r1", vm.state.value.items[0].ruleId)
        vm.onCategoryChange("a", "cat_dining")
        assertEquals("r1", vm.state.value.items[0].ruleId)
        vm.onTypeChange("a", income = true)
        vm.onCategoryChange("a", "cat_salary")
        assertNull(vm.state.value.items[0].ruleId)
    }

    // endregion

    // region Saving

    @Test
    fun `saving sends the included rows with the scan item ids then completes the scan as saved`() = runTest {
        var sent: List<ScanSaveItem> = emptyList()
        scans.saveResult = { _, items -> sent = items; ScanSaveOutcome(saved = items.size - 1, failed = 1, failures = mapOf("c" to "bad")) }
        val vm = newViewModel(
            detail(
                row("a"), row("b", skip = "duplicate"),
                ScanItem(id = "c", amount = 250.0, date = "2026-10-07", description = " Pay ", transactionType = "income", categoryId = "cat_dining"),
                ScanItem(id = "d", amount = 4.0, date = "2026-10-06", categoryId = "cat_gone"),
            ),
        )
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf("a", "c", "d"), sent.map { it.idempotencyKeyId })
        assertEquals("cat_dining", sent[0].categoryId)
        // c is income with an expense category, d's category is gone: neither is sent, the server files them as Uncategorized.
        assertNull(sent[1].categoryId)
        assertTrue(sent[1].isIncome)
        assertEquals("Pay", sent[1].description)
        assertEquals(250.0, sent[1].amount, 0.0)
        assertEquals(LocalDate.of(2026, 10, 7), sent[1].date)
        assertNull(sent[2].categoryId)

        val state = vm.state.value
        assertEquals(ScanReviewPhase.DONE, state.phase)
        assertEquals(2, state.addedCount)
        assertEquals(1, state.failedCount)
        // 4 found, 2 added, 1 failed: the unchecked duplicate is the one skipped.
        assertEquals(1, state.skippedCount)
        assertEquals(listOf("scn_1" to ScanOutcome.SAVED), scans.completed)
    }

    @Test
    fun `saving reports progress and shows the saving title for big scans`() = runTest {
        val gate = CompletableDeferred<Unit>()
        scans.saveResult = { _, items -> gate.await(); ScanSaveOutcome(saved = items.size) }
        val vm = newViewModel(detail(row("a")))
        advanceUntilIdle()

        vm.save()
        runCurrent()
        assertEquals(ScanReviewPhase.SAVING, vm.state.value.phase)
        assertEquals("Saving…", vm.state.value.saveButtonText)
        assertTrue(vm.state.value.items.all { it.isFrozen })
        // Edits and cancel are ignored while saving.
        vm.onAmountChange("a", "99")
        assertEquals("10", vm.state.value.items[0].amountText)
        vm.requestClose()
        assertFalse(vm.state.value.showDiscardDialog)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ScanReviewPhase.DONE, vm.state.value.phase)
    }

    @Test
    fun `big scans show how far the save is`() {
        val state = ScanReviewUiState(
            phase = ScanReviewPhase.SAVING,
            items = listOf(com.spenditos.android.feature.capture.CaptureReviewItem("a", "1", "d", "2026-10-01")),
            saveProgress = 100 to 230,
        )
        assertEquals("Saving 100 of 230…", state.saveButtonText)
        assertEquals("Saving…", state.copy(saveProgress = 10 to 50).saveButtonText)
    }

    @Test
    fun `an offline save keeps everything and says to try again`() = runTest {
        scans.saveResult = { _, _ -> throw ScanSaveFailure(0, ScanSaveOutcome(), ApiError.Network(java.io.IOException("offline"))) }
        val vm = newViewModel(detail(row("a"), row("b")))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ScanReviewPhase.REVIEW, state.phase)
        assertEquals("You're offline. Your changes are kept — try again when you're connected.", state.saveError)
        // The outcome of the request is unknown: rows stay frozen so a retry resends the identical payload.
        assertTrue(state.items.all { it.isFrozen })
        assertTrue(scans.completed.isEmpty())

        vm.dismissSaveError()
        assertNull(vm.state.value.saveError)
    }

    @Test
    fun `a refused chunk frees the rows that were not saved and keeps the saved ones frozen`() = runTest {
        scans.saveResult = { _, _ -> throw ScanSaveFailure(1, ScanSaveOutcome(saved = 1), ApiError.Server(400, "ValidationError")) }
        val vm = newViewModel(detail(row("a"), row("b"), row("c")))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(listOf(true, false, false), vm.state.value.items.map { it.isFrozen })
        assertEquals("Couldn't save these transactions. Try again.", vm.state.value.saveError)
        vm.onAmountChange("b", "11")
        vm.onAmountChange("a", "11")
        assertEquals(listOf("10", "11", "10"), vm.state.value.items.map { it.amountText })
    }

    @Test
    fun `retrying after a failure resends the same keys`() = runTest {
        val calls = mutableListOf<List<String>>()
        scans.saveResult = { _, items ->
            calls += items.map { it.idempotencyKeyId }
            if (calls.size == 1) throw ScanSaveFailure(0, ScanSaveOutcome(), ApiError.Server(503, "unavailable"))
            ScanSaveOutcome(saved = items.size)
        }
        val vm = newViewModel(detail(row("a"), row("b")))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf(listOf("a", "b"), listOf("a", "b")), calls)
        assertEquals(ScanReviewPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.addedCount)
    }

    @Test
    fun `a scan that disappeared is explained`() {
        assertEquals("This scan is no longer available.", ScanReviewViewModel.saveErrorMessage(ApiError.Server(404, "ScanNotFound")))
        assertEquals(
            "The signed-in account changed, so this scan wasn't saved.",
            ScanReviewViewModel.saveErrorMessage(ApiError.Network(com.spenditos.android.core.auth.SessionChangedException())),
        )
        assertEquals("Couldn't open this scan. Try again.", ScanReviewViewModel.openErrorMessage(IllegalStateException("x")))
        assertEquals(ScanFailure.AlreadySaved.userMessage, ScanReviewViewModel.openErrorMessage(ScanFailure.AlreadySaved))
    }

    // endregion

    // region Leaving

    @Test
    fun `cancel asks first and discard completes the scan as discarded`() = runTest {
        val vm = newViewModel(detail(row("a")))
        advanceUntilIdle()
        val event = async { vm.events.first() }

        vm.requestClose()
        assertTrue(vm.state.value.showDiscardDialog)
        vm.keepReviewing()
        assertFalse(vm.state.value.showDiscardDialog)
        assertTrue(scans.completed.isEmpty())

        vm.requestClose()
        vm.discardScan()
        advanceUntilIdle()

        assertEquals(ScanReviewEvent.Close, event.await())
        assertEquals(listOf("scn_1" to ScanOutcome.DISCARDED), scans.completed)
        assertFalse(vm.state.value.showDiscardDialog)
    }

    @Test
    fun `review later closes and leaves the scan on Home`() = runTest {
        val vm = newViewModel(detail(row("a")))
        advanceUntilIdle()
        val event = async { vm.events.first() }

        vm.requestClose()
        vm.reviewLater()
        advanceUntilIdle()

        assertEquals(ScanReviewEvent.Close, event.await())
        assertTrue(scans.completed.isEmpty())
    }

    @Test
    fun `closing while loading or after an error needs no confirmation and done offers scan more`() = runTest {
        scans.getResult = { throw ApiError.Network(java.io.IOException("offline")) }
        val vm = newViewModel()
        advanceUntilIdle()
        val event = async { vm.events.first() }
        vm.requestClose()
        advanceUntilIdle()
        assertEquals(ScanReviewEvent.Close, event.await())

        val ok = newViewModel(detail(row("a")))
        advanceUntilIdle()
        ok.save()
        advanceUntilIdle()
        val more = async { ok.events.first() }
        ok.scanMore()
        advanceUntilIdle()
        assertEquals(ScanReviewEvent.ScanMore, more.await())
    }

    // endregion
}
