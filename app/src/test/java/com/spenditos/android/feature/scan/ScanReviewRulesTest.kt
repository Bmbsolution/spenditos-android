package com.spenditos.android.feature.scan

import androidx.lifecycle.SavedStateHandle
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.merchants.RuleLearner
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.RealtimeEvents
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanSaveItem
import com.spenditos.android.data.scan.ScanSaveOutcome
import com.spenditos.android.testing.FakeMerchantMappings
import com.spenditos.android.testing.FakeScanActions
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.InMemoryScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Always / Just this once in the scan review (iOS `TransactionReviewListView` rules section, Rules "A" + "C"). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanReviewRulesTest {
    private lateinit var scans: FakeScanActions
    private lateinit var mappings: FakeMerchantMappings
    private val online = MutableStateFlow(true)
    private val categories = MutableStateFlow(
        listOf(
            Category("cat_dining", "u", "Dining", "#000000", isIncome = false),
            Category("cat_groc", "u", "Groceries", "#000000", isIncome = false),
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

    @After fun tearDown() = Dispatchers.resetMain()

    private fun tim(id: String, date: String = "2026-10-09", category: String? = null, skip: String? = null, ruleId: String? = null) =
        ScanItem(id = id, amount = 6.0, date = date, merchantName = "Tim Hortons", description = "Tim Hortons #$id", categoryId = category, skipReason = skip, ruleId = ruleId)

    private fun uber(id: String) = ScanItem(id = id, amount = 14.0, date = "2026-10-08", merchantName = "Uber", description = "Uber trip")

    private fun detail(vararg items: ScanItem) =
        ScanDetail(ScanSummary("scn_1", "ready", kind = "list", itemCount = items.size), items.toList())

    private fun TestScope.newViewModel(scan: ScanDetail): ScanReviewViewModel {
        val coordinator = ScanCoordinator(
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
        coordinator.handOff(scan)
        return ScanReviewViewModel(
            scans, coordinator, dataSource, mappings, RuleLearner(mappings),
            object : ConnectivityStatus { override val isOnline: StateFlow<Boolean> = online },
            SavedStateHandle(mapOf(ScanReviewViewModel.ARG_SCAN_ID to "scn_1")),
        ).also { runCurrent() }
    }

    private fun rule(id: String, pattern: String, category: String? = "cat_dining", action: RuleAction = RuleAction.CATEGORIZE) =
        MerchantMapping(id, "u", pattern, pattern, category.takeIf { action == RuleAction.CATEGORIZE }, action = action.wire)

    private val ScanReviewViewModel.keys get() = state.value.items.map { it.idempotencyKeyId }

    // region Choosing a category

    @Test
    fun `a merchant seen twice in a month shows as one group and the rows keep their scan ids`() = runTest {
        val vm = newViewModel(detail(tim("a"), uber("u"), tim("b", date = "2026-10-02"), tim("c", date = "2026-09-20")))
        val section = ScanReviewGrouping.monthSections(vm.state.value.items, locale = java.util.Locale.US, grouping = true) {
            vm.state.value.merchantKeys[it.idempotencyKeyId].orEmpty()
        }
        val october = section[0].entries
        assertEquals(2, october.size)
        assertEquals(listOf("a", "b"), (october[0] as ReviewEntry.Group).group.itemKeys)
        assertTrue(section[1].entries.single() is ReviewEntry.Single)
    }

    @Test
    fun `picking a category for a group sets it on every row and asks once`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b"), tim("c")))

        vm.onGroupCategoryChosen(listOf("a", "b", "c"), "cat_dining")
        runCurrent()

        assertEquals(listOf("cat_dining", "cat_dining", "cat_dining"), vm.state.value.items.map { it.categoryId })
        val ask = vm.state.value.ruleAsk
        assertNotNull(ask)
        assertEquals("Tim Hortons", ask!!.merchant)
        assertEquals("Dining", ask.categoryName)
        assertEquals(listOf("a", "b", "c"), ask.itemKeys)
        assertEquals(listOf("Tim Hortons" to "cat_dining"), mappings.matchCounts)
        // Nothing is written before the user answers.
        assertTrue(mappings.alwaysCalls.isEmpty() && mappings.upserts.isEmpty())
    }

    @Test
    fun `the editor's category choice for one row asks too, and clearing a category does not`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onCategoryChange("a", "cat_groc")
        runCurrent()
        assertEquals("cat_groc", vm.state.value.items[0].categoryId)
        assertNull(vm.state.value.items[1].categoryId)
        assertEquals(listOf("a"), vm.state.value.ruleAsk?.itemKeys)

        vm.answerOnce()
        vm.onCategoryChange("a", null)
        runCurrent()
        assertNull(vm.state.value.items[0].categoryId)
        assertNull(vm.state.value.ruleAsk)
    }

    @Test
    fun `a category of the other kind is refused and no rule is offered for it`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_salary")
        runCurrent()
        assertTrue(vm.state.value.items.all { it.categoryId == null })
        // iOS offers by merchant and category only, but the row never took the category, so nothing to confirm.
        assertTrue(mappings.alwaysCalls.isEmpty())
    }

    @Test
    fun `no question for rows without a merchant name`() = runTest {
        val vm = newViewModel(detail(ScanItem(id = "a", amount = 3.0, date = "2026-10-01", description = "Coffee"), ScanItem(id = "b", amount = 3.0, date = "2026-10-01", merchantName = "  ")))
        vm.onCategoryChange("a", "cat_dining")
        vm.onGroupCategoryChosen(listOf("b"), "cat_dining")
        runCurrent()
        assertNull(vm.state.value.ruleAsk)
        assertEquals("cat_dining", vm.state.value.items[0].categoryId)
    }

    @Test
    fun `no question while offline`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        online.value = false
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertNull(vm.state.value.ruleAsk)
        assertEquals("cat_dining", vm.state.value.items[0].categoryId)
        assertTrue(mappings.matchCounts.isEmpty())
    }

    @Test
    fun `an identical rule means no question and the rows read as your rule`() = runTest {
        mappings.rows += rule("r1", "TIM HORTONS")
        val vm = newViewModel(detail(tim("a"), tim("b", skip = "rule"), uber("u")))
        assertEquals(1, vm.state.value.userRules.size)

        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()

        assertNull(vm.state.value.ruleAsk)
        assertEquals("r1", vm.state.value.items[0].ruleId)
        // A row a skip rule left unchecked keeps that reason: the category does not undo the skip.
        assertNull(vm.state.value.items[1].ruleId)
    }

    @Test
    fun `a rule that files it elsewhere or skips does not stop the question`() = runTest {
        mappings.rows += rule("r1", "Tim Hortons", category = "cat_groc")
        val vm = newViewModel(detail(tim("a", ruleId = "r1", category = "cat_groc"), tim("b", ruleId = "r1", category = "cat_groc")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertNotNull(vm.state.value.ruleAsk)
        // The rows no longer follow the rule that filed them under Groceries.
        assertTrue(vm.state.value.items.all { it.ruleId == null })
    }

    @Test
    fun `rules that cannot be loaded only mean the question is asked anyway`() = runTest {
        mappings.failure = ApiError.Network(java.io.IOException("offline"))
        val vm = newViewModel(detail(tim("a"), tim("b")))
        assertTrue(vm.state.value.userRules.isEmpty())
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertNotNull(vm.state.value.ruleAsk)
    }

    // endregion

    // region Answering

    @Test
    fun `Always upserts the rule, marks the rows and says so`() = runTest {
        val vm = newViewModel(detail(tim("a", skip = "rule", ruleId = "old"), tim("b"), uber("u")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()

        vm.answerAlways()
        runCurrent()

        assertEquals(listOf(Triple("Tim Hortons", "cat_dining", false)), mappings.alwaysCalls)
        val state = vm.state.value
        assertNull(state.ruleAsk)
        assertFalse(state.isSavingRule)
        assertEquals("rule-1", state.items[0].ruleId)
        assertEquals("rule-1", state.items[1].ruleId)
        // The category rule replaces a skip rule on the row.
        assertNull(state.items[0].skipReason)
        assertNull(state.items[2].ruleId)
        assertEquals("rule-1", state.userRules.single().id)
        assertEquals(
            RuleMessage(state.ruleMessage!!.id, "Rule saved: Tim Hortons → Dining. Change it anytime in Settings › Rules.", isError = false),
            state.ruleMessage,
        )
    }

    @Test
    fun `the past transactions box only exists when there is something to update, and is on by default`() = runTest {
        mappings.matchCountResult = 7
        mappings.applyResult = 7
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertEquals(7, vm.state.value.askPastCount)
        assertTrue(vm.state.value.askAlsoUpdate)

        vm.answerAlways()
        runCurrent()

        assertEquals(listOf(Triple("Tim Hortons", "cat_dining", true)), mappings.alwaysCalls)
        assertEquals(listOf("rule-1"), mappings.applies)
        assertEquals(
            "Rule saved: Tim Hortons → Dining. 7 past transactions updated. Change it anytime in Settings › Rules.",
            vm.state.value.ruleMessage?.text,
        )
    }

    @Test
    fun `unchecking the box saves the rule without touching the past`() = runTest {
        mappings.matchCountResult = 3
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        vm.toggleAskUpdatePast()
        assertFalse(vm.state.value.askAlsoUpdate)
        vm.answerAlways()
        runCurrent()
        assertEquals(listOf(Triple("Tim Hortons", "cat_dining", false)), mappings.alwaysCalls)
        assertTrue(mappings.applies.isEmpty())
    }

    @Test
    fun `with nothing to update there is no box even if the flag is on`() = runTest {
        mappings.matchCountResult = 0
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertEquals(0, vm.state.value.askPastCount)
        vm.answerAlways()
        runCurrent()
        assertEquals(listOf(Triple("Tim Hortons", "cat_dining", false)), mappings.alwaysCalls)

        // A failed count is the same as nothing to update.
        mappings.rows.clear()
        mappings.failMatchCount = ApiError.Server(500, "boom")
        val other = newViewModel(detail(tim("a"), tim("b")))
        other.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        assertEquals(0, other.state.value.askPastCount)
    }

    @Test
    fun `a failed past update still reports the saved rule`() = runTest {
        mappings.matchCountResult = 2
        mappings.failApply = ApiError.Server(500, "boom")
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        vm.answerAlways()
        runCurrent()
        assertEquals(
            "Rule saved: Tim Hortons → Dining. Past transactions couldn't be updated. Change it anytime in Settings › Rules.",
            vm.state.value.ruleMessage?.text,
        )
        assertFalse(vm.state.value.ruleMessage!!.isError)
        assertEquals("rule-1", vm.state.value.items[0].ruleId)
    }

    @Test
    fun `Just this once keeps the choice and writes no rule`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()

        vm.answerOnce()
        runCurrent()

        assertNull(vm.state.value.ruleAsk)
        assertNull(vm.state.value.ruleMessage)
        assertEquals(listOf("cat_dining", "cat_dining"), vm.state.value.items.map { it.categoryId })
        assertTrue(mappings.upserts.isEmpty() && mappings.alwaysCalls.isEmpty() && mappings.applies.isEmpty())
    }

    @Test
    fun `the message goes away by itself after six seconds`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()
        vm.answerAlways()
        runCurrent()
        assertNotNull(vm.state.value.ruleMessage)
        advanceTimeBy(ScanReviewViewModel.RULE_MESSAGE_MILLIS - 1)
        assertNotNull(vm.state.value.ruleMessage)
        advanceTimeBy(2)
        runCurrent()
        assertNull(vm.state.value.ruleMessage)

        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_groc")
        runCurrent()
        vm.answerAlways()
        runCurrent()
        vm.dismissRuleMessage()
        assertNull(vm.state.value.ruleMessage)
    }

    // endregion

    // region A failed rule never blocks the save

    @Test
    fun `a failed rule write shows a light message and the scan still saves`() = runTest {
        mappings.failUpsert = ApiError.Network(java.io.IOException("offline"))
        var sent: List<ScanSaveItem> = emptyList()
        scans.saveResult = { _, items -> sent = items; ScanSaveOutcome(saved = items.size) }
        val vm = newViewModel(detail(tim("a"), tim("b"), uber("u")))
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        runCurrent()

        vm.answerAlways()
        runCurrent()

        val message = vm.state.value.ruleMessage
        assertEquals("Couldn't save the rule. Your transactions will still be saved.", message?.text)
        assertTrue(message!!.isError)
        assertNull(vm.state.value.ruleAsk)
        assertFalse(vm.state.value.isSavingRule)
        assertTrue("rows are not marked as filed by a rule that was not saved", vm.state.value.items.all { it.ruleId == null })
        assertTrue(vm.state.value.canSave)

        vm.save()
        runCurrent()
        advanceUntilIdle()

        assertEquals(ScanReviewPhase.DONE, vm.state.value.phase)
        assertEquals(listOf("a", "b", "u"), sent.map { it.idempotencyKeyId })
        assertEquals(listOf("cat_dining", "cat_dining", null), sent.map { it.categoryId })
    }

    @Test
    fun `saving a group sends every row with the group category and its own stable key`() = runTest {
        var sent: List<ScanSaveItem> = emptyList()
        scans.saveResult = { _, items -> sent = items; ScanSaveOutcome(saved = items.size) }
        val vm = newViewModel(detail(tim("scn_x-0"), uber("scn_x-1"), tim("scn_x-2", date = "2026-10-07"), tim("scn_x-3", date = "2026-09-30"), tim("scn_x-4", date = "2026-09-02")))
        // The user picks Dining for the October group only.
        vm.onGroupCategoryChosen(listOf("scn_x-0", "scn_x-2"), "cat_dining")
        runCurrent()
        vm.answerOnce()

        vm.save()
        runCurrent()
        advanceUntilIdle()

        assertEquals(5, sent.size)
        assertEquals(listOf("scn_x-0", "scn_x-1", "scn_x-2", "scn_x-3", "scn_x-4"), sent.map { it.idempotencyKeyId })
        assertEquals(mapOf("scn_x-0" to "cat_dining", "scn_x-2" to "cat_dining"), sent.filter { it.categoryId != null }.associate { it.idempotencyKeyId to it.categoryId })
    }

    // endregion

    // region Group controls

    @Test
    fun `one checkbox for the whole group turns everything on unless it all is`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b", skip = "duplicate"), uber("u")))
        assertEquals(listOf(true, false, true), vm.state.value.items.map { it.include })

        vm.toggleGroup(listOf("a", "b"))
        assertEquals(listOf(true, true, true), vm.state.value.items.map { it.include })
        vm.toggleGroup(listOf("a", "b"))
        assertEquals(listOf(false, false, true), vm.state.value.items.map { it.include })
        vm.toggleGroup(listOf("a", "b"))
        assertEquals(listOf(true, true, true), vm.state.value.items.map { it.include })
    }

    @Test
    fun `a group expands and collapses`() = runTest {
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.toggleGroupExpanded("2026-10|timhortons")
        assertEquals(setOf("2026-10|timhortons"), vm.state.value.expandedGroups)
        vm.toggleGroupExpanded("2026-10|timhortons")
        assertTrue(vm.state.value.expandedGroups.isEmpty())
    }

    @Test
    fun `nothing changes while the scan is saving`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        scans.saveResult = { _, items -> gate.await(); ScanSaveOutcome(saved = items.size) }
        val vm = newViewModel(detail(tim("a"), tim("b")))
        vm.save()
        runCurrent()
        vm.onGroupCategoryChosen(listOf("a", "b"), "cat_dining")
        vm.toggleGroup(listOf("a", "b"))
        runCurrent()
        assertTrue(vm.state.value.items.all { it.categoryId == null && it.include })
        assertNull(vm.state.value.ruleAsk)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `rows read the merchant name and the rule that handled them from the scan`() = runTest {
        val vm = newViewModel(detail(tim("a", ruleId = "r9"), tim("b", skip = "rule", ruleId = "r8")))
        val items = vm.state.value.items
        assertEquals("Tim Hortons", items[0].merchantName)
        assertEquals("r9", items[0].ruleId)
        assertTrue(items[0].include)
        assertEquals("rule", items[1].skipReason)
        assertFalse(items[1].include)
        assertEquals(2, vm.state.value.ruledCount)
    }

    // endregion
}
