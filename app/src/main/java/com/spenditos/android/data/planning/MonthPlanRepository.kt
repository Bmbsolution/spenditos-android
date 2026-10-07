package com.spenditos.android.data.planning

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.MonthlyPlanData
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanDraftEntry
import com.spenditos.android.data.model.MonthlyPlanTransactionUpdate
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.SkipMonthlyPlanOccurrenceRequest
import com.spenditos.android.data.model.SaveMonthlyPlanDraftRequest
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.core.network.apiCallUnit
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class MonthPlanSnapshot(
    val plan: MonthlyPlanData,
    val draft: MonthlyPlanDraftData?,
    val categories: List<Category>,
    val recurring: List<RecurringTemplate>,
    val transactions: List<Transaction> = emptyList(),
)

/** Month plan reads and draft persistence; iOS `MonthlyPlanningService`. */
@Singleton
class MonthPlanRepository @Inject constructor(private val api: PlanningApi) {
    suspend fun load(month: YearMonth): MonthPlanSnapshot {
        val monthKey = month.toString()
        val plan = apiCall { api.monthlyPlan(monthKey) }
        val draft = apiCall { api.draft(monthKey) }.draft
        val categories = apiCall { api.categories() }
        val recurring = apiCall { api.recurring() }
        val transactions = transactions(month)
        return MonthPlanSnapshot(plan, draft, categories, recurring, transactions)
    }

    /** iOS Month plan "More tools" > Plan checkup; [draftItems] keep unsaved drafts from being suggested again. */
    suspend fun checkup(month: YearMonth, draftItems: List<com.spenditos.android.data.model.MonthlyPlanCheckupDraftItem>) =
        apiCall { api.checkup(com.spenditos.android.data.model.MonthlyPlanCheckupRequest(month.toString(), draftItems)) }

    /** Used by the Plan badge and Home reminder without loading the full plan. */
    suspend fun pendingChangeCount(month: YearMonth): Int {
        val draft = apiCall { api.draft(month.toString()) }.draft ?: return 0
        return draft.entries.size + draft.pendingEdits.size + draft.pendingLogs.size +
            draft.pendingDeletes.size + draft.pendingSkips.size
    }

    suspend fun saveDraft(body: SaveMonthlyPlanDraftRequest): MonthlyPlanDraftData =
        apiCall { api.saveDraft(body) }

    suspend fun deleteDraft(month: YearMonth) {
        apiCall { api.deleteDraft(month.toString()) }
    }

    suspend fun skipOccurrence(templateId: String, date: String) {
        apiCall { api.skipOccurrence(templateId, SkipMonthlyPlanOccurrenceRequest(date)) }
    }

    suspend fun generate(month: YearMonth, templateId: String): GenerateTransactionsResult = apiCall {
        api.generate(GenerateTransactionsRequest(targetDate = "${month}-01", templateIds = listOf(templateId)))
    }

    suspend fun updateTransaction(transactionId: String, update: MonthlyPlanTransactionUpdate): Transaction =
        apiCall { api.updateTransaction(transactionId, update) }

    suspend fun deleteTransaction(transactionId: String) {
        apiCallUnit { api.deleteTransaction(transactionId) }
    }

    suspend fun createOneTime(entry: MonthlyPlanDraftEntry): BulkCreateResult = apiCall {
        api.createBulk(
            BulkCreateRequest(
                transactions = listOf(
                    BulkTransactionItem(
                        amount = entry.amount,
                        date = entry.date,
                        categoryId = entry.categoryId,
                        description = entry.description,
                        idempotencyKeyId = entry.idempotencyKeyId,
                    ),
                ),
            ),
        )
    }

    /** All transaction pages for a month; used to resume edits, deletes and logged overrides safely. */
    suspend fun transactions(month: YearMonth): List<Transaction> {
        val all = mutableListOf<Transaction>()
        var cursor: String? = null
        while (true) {
            val page = apiCall {
                api.transactions(
                    startDate = month.atDay(1).toString(),
                    endDate = month.atEndOfMonth().toString(),
                    cursor = cursor,
                )
            }
            all += page.transactions
            if (!page.pagination.hasMore) return all
            val next = page.pagination.nextCursor
            if (next.isNullOrBlank() || next == cursor) {
                throw ApiError.Decoding(IllegalStateException("Transaction cursor did not advance"))
            }
            cursor = next
        }
    }
}
