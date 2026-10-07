package com.spenditos.android.data.planning

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.BulkTransactionResult
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.DeleteMonthlyPlanDraftResult
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.YearMonth

class MonthPlanApplierTest {
    @Test
    fun `apply writes skips logs edits deletes and one-time items in order`() = runTest {
        val api = FakePlanningApi()
        api.records += transaction("edit-1", amount = 5.0)
        api.records += transaction("delete-1", amount = 6.0)
        val applier = applier(api)

        val result = applier.apply(YearMonth.of(2026, 2), fullDraft())

        assertEquals(MonthPlanApplyResult.Applied, result)
        assertEquals(listOf("skip", "generate", "update:edit-1", "delete:delete-1", "bulk", "draft-delete"), api.operations)
        assertEquals(1, api.draftDeleteCount)
    }

    @Test
    fun `failure at each operation group keeps the draft and retry resumes safely`() = runTest {
        val expectedFailedOperation = listOf("skip", "log", "edit", "delete", "one-time", "draft")
        for (failedOperation in expectedFailedOperation) {
            val api = FakePlanningApi().apply {
                records += transaction("edit-1", amount = 5.0)
                records += transaction("delete-1", amount = 6.0)
                failOnceAt = failedOperation
            }
            val applier = applier(api)
            val draft = fullDraft()

            val first = applier.apply(YearMonth.of(2026, 2), draft)

            assertEquals(MonthPlanApplyResult.Failed(failedOperation), first)
            assertEquals("draft remains available after $failedOperation", 0, api.draftDeleteCount)
            api.failOnceAt = null
            assertEquals(MonthPlanApplyResult.Applied, applier.apply(YearMonth.of(2026, 2), draft))
            assertEquals(1, api.draftDeleteCount)
            assertEquals(1, api.records.count { it.templateId == "logged-template" && it.date == "2026-02-01" })
            assertEquals(1, api.createdOneTimeCount)
            assertEquals(1, api.skippedDateCount)
        }
    }

    @Test
    fun `lost one-time bulk response retries with the same idempotency key`() = runTest {
        val api = FakePlanningApi().apply { loseFirstBulkReply = true }
        val applier = applier(api)
        val draft = fullDraft().copy(
            pendingSkips = emptyList(), pendingLogs = emptyList(), pendingEdits = emptyList(), pendingDeletes = emptyList(),
        )

        assertEquals(MonthPlanApplyResult.Failed("one-time"), applier.apply(YearMonth.of(2026, 2), draft))
        assertEquals(0, api.draftDeleteCount)
        assertEquals(MonthPlanApplyResult.Applied, applier.apply(YearMonth.of(2026, 2), draft))
        assertEquals(listOf("planning-entry-1", "planning-entry-1"), api.bulkKeys)
        assertEquals(1, api.createdOneTimeCount)
        assertEquals(1, api.draftDeleteCount)
    }

    @Test
    fun `logged amount override locates an already moved transaction and skips an applied update`() = runTest {
        val api = FakePlanningApi().apply {
            planDates["logged-template"] = listOf("2026-02-03")
            records += transaction("logged-1", templateId = "logged-template", date = "2026-02-10", amount = 20.0)
        }
        val applier = applier(api)
        val log = MonthlyPlanPendingLog(
            "logged-template", "2026-02",
            listOf(com.spenditos.android.data.model.MonthlyPlanLogOverride("2026-02-03", amount = 20.0, date = "2026-02-10")),
        )

        assertEquals(MonthPlanApplyResult.Applied, applier.apply(YearMonth.of(2026, 2), fullDraft().copy(
            pendingSkips = emptyList(), pendingLogs = listOf(log), pendingEdits = emptyList(), pendingDeletes = emptyList(), entries = emptyList(),
        )))
        assertTrue(api.operations.none { it.startsWith("update:") })
    }

    @Test
    fun `already updated edits and deleted rows are skipped on retry`() = runTest {
        val api = FakePlanningApi().apply {
            records += transaction("edit-1", amount = 12.0, description = "Already edited")
        }
        val draft = fullDraft().copy(
            pendingSkips = emptyList(), pendingLogs = emptyList(),
            pendingEdits = listOf(MonthlyPlanPendingEdit("edit-1", amount = 12.0, description = "Already edited")),
            pendingDeletes = listOf(MonthlyPlanPendingDelete("deleted-before-retry")), entries = emptyList(),
        )

        assertEquals(MonthPlanApplyResult.Applied, applier(api).apply(YearMonth.of(2026, 2), draft))
        assertTrue(api.operations.none { it.startsWith("update:") || it.startsWith("delete:") })
    }

    @Test
    fun `empty draft deletes the server draft`() = runTest {
        val api = FakePlanningApi()
        assertEquals(MonthPlanApplyResult.Applied, applier(api).apply(YearMonth.of(2026, 2), MonthlyPlanDraftData("2026-02")))
        assertEquals(listOf("draft-delete"), api.operations)
        assertEquals(1, api.draftDeleteCount)
    }

    private fun applier(api: FakePlanningApi) = MonthPlanApplier(MonthPlanRepository(api), FinanceInvalidationBus())

    private fun fullDraft() = MonthlyPlanDraftData(
        month = "2026-02",
        entries = listOf(com.spenditos.android.data.model.MonthlyPlanDraftEntry(
            id = "entry-1", idempotencyKeyId = "planning-entry-1", date = "2026-02-20", description = "Fake item", amount = 7.0, categoryId = "fake-category",
        )),
        pendingEdits = listOf(MonthlyPlanPendingEdit("edit-1", amount = 10.0)),
        pendingLogs = listOf(MonthlyPlanPendingLog("logged-template", "2026-02")),
        pendingDeletes = listOf(MonthlyPlanPendingDelete("delete-1")),
        pendingSkips = listOf(MonthlyPlanPendingSkip("skip-template", "2026-02", listOf("2026-02-02"))),
    )

    private fun transaction(
        id: String,
        amount: Double,
        templateId: String? = null,
        date: String = "2026-02-05",
        description: String = "Fake transaction",
    ) = Transaction(id, "fake-user", "fake-category", templateId, description, amount, date)
}

private class FakePlanningApi : PlanningApi {
        override suspend fun checkup(body: com.spenditos.android.data.model.MonthlyPlanCheckupRequest) =
            com.spenditos.android.core.network.ApiEnvelope(data = com.spenditos.android.data.model.MonthlyPlanCheckupData(body.month))

    val operations = mutableListOf<String>()
    val records = mutableListOf<Transaction>()
    val bulkKeys = mutableListOf<String>()
    val planDates = mutableMapOf<String, List<String>>()
    private val createdKeys = mutableSetOf<String>()
    private val skipped = mutableSetOf<Pair<String, String>>()
    var failOnceAt: String? = null
    var loseFirstBulkReply = false
    var draftDeleteCount = 0
    val createdOneTimeCount: Int get() = records.count { it.id.startsWith("bulk-") }
    val skippedDateCount: Int get() = skipped.size

    private fun check(operation: String) {
        if (failOnceAt == operation) {
            failOnceAt = null
            throw ApiError.Server(503, "Unavailable")
        }
    }

    override suspend fun monthlyPlan(month: String) = ApiEnvelope(MonthlyPlanData(month))
    override suspend fun draft(month: String) = ApiEnvelope(MonthlyPlanDraftLookup())
    override suspend fun saveDraft(body: SaveMonthlyPlanDraftRequest) = ApiEnvelope(MonthlyPlanDraftData(body.month, body.entries))
    override suspend fun deleteDraft(month: String): ApiEnvelope<DeleteMonthlyPlanDraftResult> {
        check("draft")
        operations += "draft-delete"
        draftDeleteCount++
        return ApiEnvelope(DeleteMonthlyPlanDraftResult(month, true))
    }
    override suspend fun categories() = ApiEnvelope(emptyList<Category>())
    override suspend fun recurring() = ApiEnvelope(emptyList<RecurringTemplate>())
    override suspend fun transactions(startDate: String, endDate: String, sortBy: String, sortOrder: String, limit: Int, cursor: String?) = ApiEnvelope(
        TransactionsPage(transactions = records.toList()),
    )
    override suspend fun skipOccurrence(templateId: String, body: SkipMonthlyPlanOccurrenceRequest): ApiEnvelope<RecurringTemplate> {
        check("skip")
        operations += "skip"
        skipped += templateId to body.date
        return ApiEnvelope(fakeRecurring(templateId))
    }
    override suspend fun generate(body: GenerateTransactionsRequest): ApiEnvelope<GenerateTransactionsResult> {
        check("log")
        operations += "generate"
        for (templateId in body.templateIds.orEmpty()) {
            for (date in planDates[templateId].orEmpty().ifEmpty { listOf("2026-02-01") }) {
                if (records.none { it.templateId == templateId && it.date.take(10) == date }) {
                    records += transaction("generated-${templateId}-$date", templateId, date)
                }
            }
        }
        return ApiEnvelope(GenerateTransactionsResult(generated = 1))
    }
    override suspend fun updateTransaction(transactionId: String, body: MonthlyPlanTransactionUpdate): ApiEnvelope<Transaction> {
        check("edit")
        operations += "update:$transactionId"
        val old = records.first { it.id == transactionId }
        val updated = old.copy(
            amount = body.amount ?: old.amount,
            date = body.date ?: old.date,
            description = body.description ?: old.description,
            categoryId = body.categoryId ?: old.categoryId,
        )
        records[records.indexOf(old)] = updated
        return ApiEnvelope(updated)
    }
    override suspend fun deleteTransaction(transactionId: String) {
        check("delete")
        operations += "delete:$transactionId"
        records.removeAll { it.id == transactionId }
    }
    override suspend fun createBulk(body: BulkCreateRequest): ApiEnvelope<BulkCreateResult> {
        check("one-time")
        operations += "bulk"
        val item = body.transactions.single()
        bulkKeys += item.idempotencyKeyId
        if (createdKeys.add(item.idempotencyKeyId)) {
            records += Transaction("bulk-${item.idempotencyKeyId}", "fake-user", item.categoryId, item.templateId, item.description, item.amount, item.date)
            if (loseFirstBulkReply) {
                loseFirstBulkReply = false
                throw ApiError.Network(IOException("response lost"))
            }
            return ApiEnvelope(BulkCreateResult(results = listOf(BulkTransactionResult(index = 0, transaction = records.last()))))
        }
        return ApiEnvelope(BulkCreateResult(duplicates = 1))
    }

    private fun transaction(id: String, templateId: String, date: String) = Transaction(id, "fake-user", "fake-category", templateId, "Fake scheduled", 10.0, date)
}

private fun fakeRecurring(id: String) = RecurringTemplate(id, "fake-user", "fake-category", "Fake scheduled", 10.0, "fixed", "monthly", "2026-02-01")
