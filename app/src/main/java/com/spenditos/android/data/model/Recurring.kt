package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `RecurringTemplate` packages/types/src/domain/transaction.ts:84. Strings preserve unknown legacy values safely. */
@Serializable
data class RecurringTemplate(
    val id: String,
    val userId: String,
    val categoryId: String,
    val description: String,
    val amount: Double,
    val type: String,
    val frequency: String,
    val firstOccurrenceDate: String,
    val occurrencesRemaining: Int? = null,
    val isActive: Boolean = true,
    val secondOccurrenceDay: Int? = null,
    val repeatInterval: Int? = null,
    val amountVaries: Boolean = false,
    val pausedAt: String? = null,
    val skippedDates: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Set on a debt's payment bill (docs/features/debts.md). Absent on older servers. */
    val debtId: String? = null,
)

/** `CreateRecurringTemplateRequest` packages/types/src/api/index.ts:443. */
@Serializable
data class CreateRecurringTemplateRequest(
    val categoryId: String,
    val description: String,
    val amount: Double,
    val type: String = "fixed",
    val frequency: String,
    val firstOccurrenceDate: String,
    /** JsonNull is sent explicitly for the server's unlimited occurrence cap. */
    val occurrencesRemaining: JsonElement,
    val isActive: Boolean = true,
    val secondOccurrenceDay: JsonElement,
    val repeatInterval: Int = 1,
    val amountVaries: Boolean = false,
)

/** `UpdateRecurringTemplateRequest` packages/types/src/api/index.ts:478. */
@Serializable
data class UpdateRecurringTemplateRequest(
    val categoryId: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val frequency: String? = null,
    val firstOccurrenceDate: String? = null,
    /** Use JsonNull to clear a saved cap; omitting the field keeps the old cap. */
    val occurrencesRemaining: JsonElement? = null,
    val isActive: Boolean? = null,
    val secondOccurrenceDay: JsonElement? = null,
    val repeatInterval: Int? = null,
    val pausedAt: String? = null,
    val amountVaries: Boolean? = null,
)

/** `PatchMainIncomeTemplateRequest` packages/types/src/api/index.ts:635. */
@Serializable
data class PatchMainIncomeTemplateRequest(val mainIncomeTemplateId: JsonElement)

/** Handler-only response from `packages/functions/src/api/transactions.ts:1356`. */
@Serializable
data class GenerateTransactionsResult(
    val message: String = "",
    val generated: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
)

/** Handler-only request to `POST /transactions/generate` (`transactions.ts:142-150`). */
@Serializable
data class GenerateTransactionsRequest(
    val targetDate: String,
    val templateIds: List<String>? = null,
    /** `YYYY-MM-DD`: only create entries dated on or after this day (`transactions.ts:146`). */
    val fromDate: String? = null,
)
