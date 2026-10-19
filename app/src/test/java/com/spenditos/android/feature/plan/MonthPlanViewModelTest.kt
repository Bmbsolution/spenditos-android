package com.spenditos.android.feature.plan

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.BulkTransactionResult
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.DeleteMonthlyPlanDraftResult
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.MonthlyPlanCategory
import com.spenditos.android.data.model.MonthlyPlanData
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanDraftLookup
import com.spenditos.android.data.model.MonthlyPlanOccurrence
import com.spenditos.android.data.model.MonthlyPlanPendingDelete
import com.spenditos.android.data.model.MonthlyPlanPendingEdit
import com.spenditos.android.data.model.MonthlyPlanPendingLog
import com.spenditos.android.data.model.MonthlyPlanPendingSkip
import com.spenditos.android.data.model.MonthlyPlanTransactionUpdate
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.SaveMonthlyPlanDraftRequest
import com.spenditos.android.data.model.SkipMonthlyPlanOccurrenceRequest
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.planning.MonthPlanApplier
import com.spenditos.android.data.planning.MonthPlanRepository
import com.spenditos.android.data.planning.PlanningApi
import com.spenditos.android.data.user.UserSettingsActions
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class MonthPlanViewModelTest {
    private lateinit var api: FakePlanningApi
    private lateinit var online: MutableStateFlow<Boolean>
    private lateinit var viewModel: MonthPlanViewModel
    private val bus = FinanceInvalidationBus()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakePlanningApi()
        online = MutableStateFlow(true)
        viewModel = MonthPlanViewModel(
            MonthPlanRepository(api),
            MonthPlanApplier(MonthPlanRepository(api), bus),
            object : ConnectivityStatus { override val isOnline: StateFlow<Boolean> = online.asStateFlow() },
            FakeSettings(),
            bus,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `skip replaces amount change and amount change replaces skip`() = runTest {
        viewModel.load()
        val rent = LocalDate.parse("${viewModel.state.value.month}-03")
        viewModel.stageAmountChange("fake-rent", listOf(rent), BigDecimal("500.00"))
        assertEquals(500.0, viewModel.rows().first { it.templateId == "fake-rent" }.total.toDouble(), 0.0)

        viewModel.stageSkip("fake-rent", listOf(rent), skipped = true)
        assertTrue(viewModel.rows().first { it.templateId == "fake-rent" }.isSkipped)
        assertTrue(viewModel.state.value.draft.pendingLogs.isEmpty())
        assertEquals(listOf(rent.toString()), viewModel.state.value.draft.pendingSkips.single().dates)

        viewModel.stageSkip("fake-rent", listOf(rent), skipped = false)
        assertTrue(viewModel.state.value.draft.pendingSkips.isEmpty())
        assertFalse(viewModel.rows().first { it.templateId == "fake-rent" }.isSkipped)

        viewModel.stageSkip("fake-rent", listOf(rent), skipped = true)
        viewModel.stageAmountChange("fake-rent", listOf(rent), BigDecimal("510.00"))
        assertTrue(viewModel.state.value.draft.pendingSkips.isEmpty())
        assertEquals(510.0, viewModel.rows().first { it.templateId == "fake-rent" }.total.toDouble(), 0.0)
    }

    @Test
    fun `scheduled rows group income by item and bills by date`() = runTest {
        viewModel.load()
        val rows = viewModel.rows()
        assertEquals(1, rows.count { it.isIncome })
        assertEquals(2, rows.first { it.isIncome }.occurrenceCount)
        assertEquals(BigDecimal("2000.00"), rows.first { it.isIncome }.total)
        assertEquals(listOf("Fake rent", "Fake phone"), rows.filterNot { it.isIncome }.map { it.description })
    }

    @Test
    fun `summary uses BigDecimal and category income for one-time items`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        val rentDate = month.atDay(3)
        viewModel.stageSkip("fake-rent", listOf(rentDate), skipped = true)
        assertTrue(viewModel.addOneTime("Fake repair", BigDecimal("100.00"), "fake-expense", month.atDay(12)))
        assertTrue(viewModel.addOneTime("Fake extra income", BigDecimal("200.00"), "fake-income", month.atDay(14)))

        val summary = viewModel.summary()!!
        assertEquals(BigDecimal("1540.00"), summary.recordsLeft)
        assertEquals(BigDecimal("2200.00"), summary.draftIncome)
        assertEquals(BigDecimal("60.00"), summary.draftBills)
        assertEquals(BigDecimal("100.00"), summary.draftOneTime)
        assertEquals(BigDecimal("2040.00"), summary.draftLeft)
        assertEquals(BigDecimal("500.00"), summary.delta)
        assertEquals(3, viewModel.state.value.draftChangeCount)
    }

    @Test
    fun `saved one-time entries count in both summaries and bill entries do not`() = runTest {
        val month = viewModel.state.value.month
        api.records += Transaction("saved-gift", "fake-user", "fake-expense", null, "Gift", 200.0, month.atDay(10).toString())
        api.records += Transaction("saved-bonus", "fake-user", "fake-income", null, "Bonus", 500.0, month.atDay(12).toString())
        // A bill's own entry is already counted through its schedule.
        api.records += Transaction("rent-entry", "fake-user", "fake-expense", "fake-rent", "Fake rent", 400.0, month.atDay(3).toString())
        viewModel.load()
        val without = viewModel.summary()!!

        assertEquals(listOf("saved-gift", "saved-bonus"), viewModel.savedEntries().map { it.transaction.id })
        assertEquals(BigDecimal("2500.00"), without.draftIncome)
        assertEquals(BigDecimal("200.00"), without.draftOneTime)
        assertEquals(BigDecimal("1840.00"), without.recordsLeft)
        assertEquals(without.recordsLeft, without.draftLeft)
    }

    @Test
    fun `draft hydration restores skip and month-only amount changes`() = runTest {
        val month = viewModel.state.value.month
        api.savedDraft = MonthlyPlanDraftData(
            month = month.toString(),
            pendingLogs = listOf(MonthlyPlanPendingLog(
                "fake-rent", month.toString(), listOf(com.spenditos.android.data.model.MonthlyPlanLogOverride(month.atDay(3).toString(), amount = 450.0)),
            )),
            pendingSkips = listOf(MonthlyPlanPendingSkip("fake-phone", month.toString(), listOf(month.atDay(12).toString()))),
        )

        viewModel.load()

        assertTrue(viewModel.rows().first { it.templateId == "fake-phone" }.isSkipped)
        assertEquals(BigDecimal("450.00"), viewModel.rows().first { it.templateId == "fake-rent" }.total)
        assertEquals(2, viewModel.state.value.draftChangeCount)
    }

    @Test
    fun `change log applies per date and draft rows are marked`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        val rent = viewModel.rows().first { it.templateId == "fake-rent" }
        viewModel.stageAmountChange(rent.templateId, rent.dates, BigDecimal("450.00"))

        val row = viewModel.rows().first { it.templateId == "fake-rent" }
        assertTrue(row.hasDraftChange)
        assertEquals(BigDecimal("450.00"), row.total)
        assertEquals(listOf(month.atDay(3).toString()), viewModel.state.value.draft.pendingLogs.single().overrides.map { it.occurrenceDate })
    }

    @Test
    fun `scheduled records can be staged for removal and restored`() = runTest {
        val month = viewModel.state.value.month
        api.records += Transaction("fake-record", "fake-user", "fake-expense", "fake-rent", "Fake rent", 400.0, month.atDay(3).toString())
        viewModel.load()

        assertEquals(1, viewModel.transactionRows(isIncome = false).size)
        viewModel.stageTransactionDelete("fake-record")
        assertTrue(viewModel.transactionRows(isIncome = false).single().isDeleted)
        assertEquals(1, viewModel.state.value.draftChangeCount)
        viewModel.undoTransactionDelete("fake-record")
        assertFalse(viewModel.transactionRows(isIncome = false).single().isDeleted)
        assertEquals(0, viewModel.state.value.draftChangeCount)
    }

    @Test
    fun `draft validation enforces server counts ids and month dates`() {
        val month = YearMonth.of(2026, 2)
        val duplicateEntries = MonthPlanDraftState(entries = listOf(
            entry("same", "2026-02-01"), entry("same", "2026-02-02"),
        ))
        assertNotNull(duplicateEntries.validationError(month))
        assertNotNull(MonthPlanDraftState(entries = listOf(entry("outside", "2026-03-01"))).validationError(month))
        assertNotNull(MonthPlanDraftState(pendingEdits = listOf(
            MonthlyPlanPendingEdit("same-txn", amount = 1.0), MonthlyPlanPendingEdit("same-txn", description = "Fake"),
        )).validationError(month))
        assertNotNull(MonthPlanDraftState(
            pendingEdits = listOf(MonthlyPlanPendingEdit("same-txn", amount = 1.0)),
            pendingDeletes = listOf(MonthlyPlanPendingDelete("same-txn")),
        ).validationError(month))
        assertNotNull(MonthPlanDraftState(pendingLogs = listOf(
            MonthlyPlanPendingLog("fake-template", "2026-02"), MonthlyPlanPendingLog("fake-template", "2026-02"),
        )).validationError(month))
        assertNotNull(MonthPlanDraftState(entries = listOf(entry("invalid-day", "2026-02-29"))).validationError(month))
    }

    @Test
    fun `autosave waits 900 milliseconds and debounces newer edits`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        advanceTimeBy(700)
        viewModel.stageSkip("fake-phone", listOf(month.atDay(12)), skipped = true)
        advanceTimeBy(899)
        runCurrent()
        assertEquals(0, api.saveCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, api.saveCalls)
        assertEquals(2, api.savedDraft!!.pendingSkips.size)
    }

    @Test
    fun `month switch flushes the old month before loading the new one`() = runTest {
        viewModel.load()
        val previous = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(previous.atDay(3)), skipped = true)

        viewModel.selectMonth(previous.plusMonths(1))

        assertEquals(previous.toString(), api.savedMonths.first())
        assertEquals(previous.plusMonths(1), viewModel.state.value.month)
        assertEquals(previous.plusMonths(1).toString(), api.lastPlanMonth)
        assertEquals(1, api.saveCalls)
    }

    @Test
    fun `month bounds cover current through three ahead and past months are discard only`() = runTest {
        viewModel.load()
        val current = YearMonth.now()
        viewModel.selectMonth(current)
        assertFalse(viewModel.state.value.canGoToPreviousMonth)
        assertTrue(viewModel.state.value.canGoToNextMonth)

        viewModel.selectMonth(current.plusMonths(3))
        assertFalse(viewModel.state.value.canGoToNextMonth)
        assertTrue(viewModel.state.value.canGoToPreviousMonth)

        viewModel.selectMonth(current.minusMonths(1))
        assertTrue(viewModel.state.value.isPast)
        viewModel.stageSkip("fake-rent", listOf(current.minusMonths(1).atDay(3)), skipped = true)
        assertEquals(0, viewModel.state.value.draftChangeCount)
    }

    @Test
    fun `autosave failure keeps the draft and a later flush retries`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        api.failures["save"] = ApiError.Server(400, "InvalidDraft")

        assertFalse(viewModel.flushAutosave())
        assertEquals(1, viewModel.state.value.draftChangeCount)
        assertNotNull(viewModel.state.value.draftMessage)

        api.failures.remove("save")
        assertTrue(viewModel.flushAutosave())
        assertEquals(month.toString(), api.savedDraft!!.month)
        assertNull(viewModel.state.value.draftMessage)
    }

    @Test
    fun `apply stops after a network failure keeps the draft and retry clears it`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        api.failures["skip"] = ApiError.Network(IOException("offline test"))
        assertTrue(viewModel.flushAutosave())

        viewModel.applyAll()

        assertEquals(1, viewModel.state.value.draftChangeCount)
        assertTrue(viewModel.state.value.applyMessage!!.contains("Try again"))
        assertEquals(0, api.draftDeleteCalls)
        api.failures.remove("skip")
        viewModel.applyAll()
        assertEquals(0, viewModel.state.value.draftChangeCount)
        assertEquals(1, api.draftDeleteCalls)
    }

    @Test
    fun `after an unfinished apply one-time entries are frozen until it succeeds`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        val category = viewModel.state.value.snapshot!!.categories.first { !it.isIncome }.id
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        assertTrue(viewModel.addOneTime("Fake gift", java.math.BigDecimal("25.00"), category, month.atDay(5)))
        val entryId = viewModel.state.value.draft.entries.single().id
        api.failures["skip"] = ApiError.Network(IOException("offline test"))
        assertTrue(viewModel.flushAutosave())

        viewModel.applyAll() // fails: the entry may or may not already be saved on the server

        assertTrue(viewModel.state.value.applyIncomplete)
        viewModel.removeOneTime(entryId)
        assertEquals(1, viewModel.state.value.draft.entries.size) // removing it could orphan a saved copy
        assertFalse(viewModel.addOneTime("Another", java.math.BigDecimal("5.00"), category, month.atDay(6)))
        assertEquals(1, viewModel.state.value.draft.entries.size)

        api.failures.remove("skip")
        viewModel.applyAll()
        assertFalse(viewModel.state.value.applyIncomplete)
    }

    @Test
    fun `load and discard failures show errors and keep visible data`() = runTest {
        api.failures["monthlyPlan"] = ApiError.Server(503, "Unavailable")
        viewModel.load()
        assertNotNull(viewModel.state.value.error)
        api.failures.remove("monthlyPlan")
        viewModel.load()
        val month = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        viewModel.flushAutosave()
        api.failures["deleteDraft"] = ApiError.Network(IOException("offline test"))

        viewModel.discardAllChanges()

        assertEquals(1, viewModel.state.value.draftChangeCount)
        assertTrue(viewModel.state.value.applyMessage!!.startsWith("Couldn't discard"))
    }

    @Test
    fun `apply and discard are online only`() = runTest {
        viewModel.load()
        val month = viewModel.state.value.month
        viewModel.stageSkip("fake-rent", listOf(month.atDay(3)), skipped = true)
        viewModel.flushAutosave()
        online.value = false
        runCurrent()

        viewModel.applyAll()
        viewModel.discardAllChanges()

        assertEquals(1, viewModel.state.value.draftChangeCount)
        assertEquals(0, api.draftDeleteCalls)
        assertTrue(api.operations.none { it == "skip" })
    }

    private fun entry(id: String, date: String) = com.spenditos.android.data.model.MonthlyPlanDraftEntry(
        id = id, idempotencyKeyId = "planning-$id", date = date, description = "Fake item", amount = 1.0, categoryId = "fake-expense",
    )
}

private class FakeSettings : UserSettingsActions {
    override fun currency() = flowOf("CAD")
    override suspend fun refresh() = UserSettings("fake-user", currency = "CAD")
    override suspend fun updateSettings(update: UserSettingsUpdate) = UserSettings("fake-user", currency = "CAD")
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
}

private class FakePlanningApi : PlanningApi {
        override suspend fun checkup(body: com.spenditos.android.data.model.MonthlyPlanCheckupRequest) =
            com.spenditos.android.core.network.ApiEnvelope(data = com.spenditos.android.data.model.MonthlyPlanCheckupData(body.month))

    val failures = mutableMapOf<String, Throwable>()
    val operations = mutableListOf<String>()
    val savedMonths = mutableListOf<String>()
    val records = mutableListOf<Transaction>()
    var savedDraft: MonthlyPlanDraftData? = null
    var saveCalls = 0
    var draftDeleteCalls = 0
    var lastPlanMonth: String? = null

    private fun fail(operation: String) { failures[operation]?.let { throw it } }

    override suspend fun monthlyPlan(month: String): ApiEnvelope<MonthlyPlanData> {
        fail("monthlyPlan")
        lastPlanMonth = month
        return ApiEnvelope(plan(month))
    }
    override suspend fun draft(month: String): ApiEnvelope<MonthlyPlanDraftLookup> {
        fail("draft")
        return ApiEnvelope(MonthlyPlanDraftLookup(savedDraft?.takeIf { it.month == month }))
    }
    override suspend fun saveDraft(body: SaveMonthlyPlanDraftRequest): ApiEnvelope<MonthlyPlanDraftData> {
        saveCalls++
        fail("save")
        savedMonths += body.month
        savedDraft = MonthlyPlanDraftData(
            body.month, body.entries, body.transactionDelta, body.pendingEdits, body.pendingLogs, body.pendingDeletes, body.pendingSkips, "fake-updated-at",
        )
        return ApiEnvelope(savedDraft!!)
    }
    override suspend fun deleteDraft(month: String): ApiEnvelope<DeleteMonthlyPlanDraftResult> {
        fail("deleteDraft")
        operations += "draft-delete"
        draftDeleteCalls++
        savedDraft = null
        return ApiEnvelope(DeleteMonthlyPlanDraftResult(month, true))
    }
    override suspend fun categories(): ApiEnvelope<List<Category>> {
        fail("categories")
        return ApiEnvelope(listOf(
            Category("fake-income", "fake-user", "Income", "#008800", isIncome = true),
            Category("fake-expense", "fake-user", "Bills", "#880000", isIncome = false),
        ))
    }
    override suspend fun recurring(): ApiEnvelope<List<RecurringTemplate>> {
        fail("recurring")
        return ApiEnvelope(emptyList())
    }
    override suspend fun transactions(startDate: String, endDate: String, sortBy: String, sortOrder: String, limit: Int, cursor: String?): ApiEnvelope<TransactionsPage> {
        fail("transactions")
        return ApiEnvelope(TransactionsPage(records.filter { it.date >= startDate && it.date <= endDate }))
    }
    override suspend fun skipOccurrence(templateId: String, body: SkipMonthlyPlanOccurrenceRequest): ApiEnvelope<RecurringTemplate> {
        fail("skip")
        operations += "skip"
        return ApiEnvelope(fakeRecurring(templateId))
    }
    override suspend fun generate(body: GenerateTransactionsRequest): ApiEnvelope<GenerateTransactionsResult> {
        fail("log")
        operations += "generate"
        return ApiEnvelope(GenerateTransactionsResult(generated = 1))
    }
    override suspend fun updateTransaction(transactionId: String, body: MonthlyPlanTransactionUpdate): ApiEnvelope<Transaction> {
        fail("edit")
        operations += "update:$transactionId"
        val old = records.first { it.id == transactionId }
        val updated = old.copy(amount = body.amount ?: old.amount, date = body.date ?: old.date, description = body.description ?: old.description, categoryId = body.categoryId ?: old.categoryId)
        records[records.indexOf(old)] = updated
        return ApiEnvelope(updated)
    }
    override suspend fun deleteTransaction(transactionId: String) {
        fail("delete")
        operations += "delete:$transactionId"
        records.removeAll { it.id == transactionId }
    }
    override suspend fun createBulk(body: BulkCreateRequest): ApiEnvelope<BulkCreateResult> {
        fail("one-time")
        operations += "bulk"
        val item = body.transactions.single()
        val transaction = Transaction("fake-${item.idempotencyKeyId}", "fake-user", item.categoryId, null, item.description, item.amount, item.date)
        records += transaction
        return ApiEnvelope(BulkCreateResult(results = listOf(BulkTransactionResult(0, transaction))))
    }

    private fun plan(month: String): MonthlyPlanData {
        val incomeDate = "$month-01"
        val rentDate = "$month-03"
        val phoneDate = "$month-12"
        return MonthlyPlanData(
            month = month,
            categories = listOf(
                MonthlyPlanCategory("fake-income", "Income", true),
                MonthlyPlanCategory("fake-expense", "Bills", false),
            ),
            scheduled = listOf(
                MonthlyPlanOccurrence("fake-pay", "fake-income", "Fake income", incomeDate, 1000.0, true),
                MonthlyPlanOccurrence("fake-pay", "fake-income", "Fake income", "$month-15", 1000.0, true),
                MonthlyPlanOccurrence("fake-rent", "fake-expense", "Fake rent", rentDate, 400.0, false),
                MonthlyPlanOccurrence("fake-phone", "fake-expense", "Fake phone", phoneDate, 60.0, false),
            ),
        )
    }
}

private fun fakeRecurring(id: String) = RecurringTemplate(id, "fake-user", "fake-category", "Fake scheduled", 10.0, "fixed", "monthly", "2026-02-01")
