package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** `MonthlyPlanData` packages/types/src/api/index.ts:46; returned by GET /planning/month. */
@Serializable
data class MonthlyPlanData(
    val month: String,
    val totals: MonthlyPlanTotals = MonthlyPlanTotals(),
    val categories: List<MonthlyPlanCategory> = emptyList(),
    val scheduled: List<MonthlyPlanOccurrence> = emptyList(),
    val needsIncomeSetup: Boolean = false,
    val needsExpensePlan: Boolean = false,
)

/** `MonthlyPlanData.totals` packages/types/src/api/index.ts:46. */
@Serializable
data class MonthlyPlanTotals(
    val expectedIncome: Double = 0.0,
    val committedExpenses: Double = 0.0,
    val flexibleExpenses: Double = 0.0,
    val plannedExpenses: Double = 0.0,
    val availableToAllocate: Double = 0.0,
)

/** `MonthlyPlanCategory` packages/types/src/api/index.ts:62. */
@Serializable
data class MonthlyPlanCategory(
    val categoryId: String,
    val name: String,
    val isIncome: Boolean,
    val recurringAmount: Double = 0.0,
    val committedAmount: Double = 0.0,
    val plannedAmount: Double = 0.0,
)

/** `MonthlyPlanOccurrence` packages/types/src/api/index.ts:71. */
@Serializable
data class MonthlyPlanOccurrence(
    val templateId: String,
    val categoryId: String,
    val description: String,
    val date: String,
    val amount: Double,
    val isIncome: Boolean = false,
    val type: String = "fixed",
)

/** `MonthlyPlanDraftEntry` packages/types/src/api/index.ts:129. */
@Serializable
data class MonthlyPlanDraftEntry(
    val id: String,
    val idempotencyKeyId: String,
    val date: String,
    val description: String,
    val amount: Double,
    val categoryId: String,
    /** JsonNull is non-null so the required nullable wire field is sent even with explicitNulls=false. */
    val recurring: JsonElement = JsonNull,
)

/** `MonthlyPlanPendingEdit` packages/types/src/api/index.ts:151. */
@Serializable
data class MonthlyPlanPendingEdit(
    val transactionId: String,
    val amount: Double? = null,
    val date: String? = null,
    val description: String? = null,
    val categoryId: String? = null,
)

/** `MonthlyPlanLogOverride` packages/types/src/api/index.ts:165. */
@Serializable
data class MonthlyPlanLogOverride(
    val occurrenceDate: String,
    val amount: Double? = null,
    val date: String? = null,
)

/** `MonthlyPlanPendingLog` packages/types/src/api/index.ts:174. */
@Serializable
data class MonthlyPlanPendingLog(
    val templateId: String,
    val month: String,
    val overrides: List<MonthlyPlanLogOverride> = emptyList(),
)

/** `MonthlyPlanPendingDelete` packages/types/src/api/index.ts:183. */
@Serializable
data class MonthlyPlanPendingDelete(val transactionId: String)

/** `MonthlyPlanPendingSkip` packages/types/src/api/index.ts:188. */
@Serializable
data class MonthlyPlanPendingSkip(
    val templateId: String,
    val month: String,
    val dates: List<String>,
)

/** `MonthlyPlanDraftData` packages/types/src/api/index.ts:191. */
@Serializable
data class MonthlyPlanDraftData(
    val month: String,
    val entries: List<MonthlyPlanDraftEntry> = emptyList(),
    val transactionDelta: Map<String, Double> = emptyMap(),
    val pendingEdits: List<MonthlyPlanPendingEdit> = emptyList(),
    val pendingLogs: List<MonthlyPlanPendingLog> = emptyList(),
    val pendingDeletes: List<MonthlyPlanPendingDelete> = emptyList(),
    val pendingSkips: List<MonthlyPlanPendingSkip> = emptyList(),
    val updatedAt: String = "",
)

/** `GET /planning/draft` response wrapper in packages/functions/src/api/planning.ts:266. */
@Serializable
data class MonthlyPlanDraftLookup(val draft: MonthlyPlanDraftData? = null)

/** `SaveMonthlyPlanDraftRequest` packages/types/src/api/index.ts:206. */
@Serializable
data class SaveMonthlyPlanDraftRequest(
    val month: String,
    val entries: List<MonthlyPlanDraftEntry>,
    val transactionDelta: Map<String, Double> = emptyMap(),
    val pendingEdits: List<MonthlyPlanPendingEdit> = emptyList(),
    val pendingLogs: List<MonthlyPlanPendingLog> = emptyList(),
    val pendingDeletes: List<MonthlyPlanPendingDelete> = emptyList(),
    val pendingSkips: List<MonthlyPlanPendingSkip> = emptyList(),
)

/** Handler response from DELETE /planning/draft in packages/functions/src/api/planning.ts:346. */
@Serializable
data class DeleteMonthlyPlanDraftResult(val month: String, val deleted: Boolean)

/** Request body for POST /recurring/{id}/skip in packages/functions/src/api/recurring.ts:782. */
@Serializable
data class SkipMonthlyPlanOccurrenceRequest(val date: String)

/** `UpdateTransactionRequest` packages/types/src/api/index.ts:399. */
@Serializable
data class MonthlyPlanTransactionUpdate(
    val amount: Double? = null,
    val date: String? = null,
    val description: String? = null,
    val categoryId: String? = null,
)

/** `MonthlyPlanCheckupEvidence` packages/types/src/api/index.ts:102. */
@kotlinx.serialization.Serializable
data class MonthlyPlanCheckupEvidence(
    val month: String,
    val amount: Double,
    val transactionCount: Int = 0,
    val dates: List<String> = emptyList(),
)

/** `MonthlyPlanCheckupSuggestion` api/index.ts:109; `strength` is "possible" or "likely". */
@kotlinx.serialization.Serializable
data class MonthlyPlanCheckupSuggestion(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val description: String,
    val amount: Double,
    val recurringType: String = "fixed",
    val observedMonths: List<String> = emptyList(),
    val evidence: List<MonthlyPlanCheckupEvidence> = emptyList(),
    val strength: String = "possible",
)

/** `MonthlyPlanCheckupRequest` api/index.ts:121. */
@kotlinx.serialization.Serializable
data class MonthlyPlanCheckupRequest(
    val month: String,
    val draftItems: List<MonthlyPlanCheckupDraftItem> = emptyList(),
)

@kotlinx.serialization.Serializable
data class MonthlyPlanCheckupDraftItem(val categoryId: String, val description: String)

/** `MonthlyPlanCheckupData` api/index.ts:127. */
@kotlinx.serialization.Serializable
data class MonthlyPlanCheckupData(
    val month: String,
    val historyMonths: List<String> = emptyList(),
    val monthsWithExpenseHistory: Int = 0,
    val suggestions: List<MonthlyPlanCheckupSuggestion> = emptyList(),
)
