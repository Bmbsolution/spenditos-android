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
    /** When the bill was paused (ISO 8601). With `isActive == false` it is Paused (#403). */
    val pausedAt: String? = null,
    /** When the bill ended for good (ISO 8601). With `isActive == false` it is Ended (#403). */
    val endedAt: String? = null,
    val skippedDates: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Set on a debt's payment bill (docs/features/debts.md). Absent on older servers. */
    val debtId: String? = null,
) {
    /** A stored "variable" estimate (#336): shown as "Old estimate · not logged", delete only. */
    val isLegacyEstimate: Boolean get() = type != "fixed"

    /** Active, Paused (coming back) or Ended (done): issue #403, same rule as iOS `RecurringTemplate.lifecycle`. */
    val lifecycle: BillLifecycle
        get() = when {
            isActive -> BillLifecycle.ACTIVE
            endedAt != null -> BillLifecycle.ENDED
            // A finished N-payment bill from before `endedAt` existed.
            pausedAt == null && occurrencesRemaining == 0 -> BillLifecycle.ENDED
            // Any other switched-off bill, including old ones that never got a `pausedAt`, stays
            // Paused: visible, and the user can end it.
            else -> BillLifecycle.PAUSED
        }
}

/** Where a bill stands (#403): Paused bills come back, Ended ones live in the Ended archive. */
enum class BillLifecycle { ACTIVE, PAUSED, ENDED }

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
    /** Set when pausing; JsonNull clears it (ending). Omitted when null, so resuming leaves it to the server. */
    val pausedAt: JsonElement? = null,
    /** Set when ending (`isActive: false`); the bill moves to Ended. JsonNull clears it. */
    val endedAt: JsonElement? = null,
    val amountVaries: Boolean? = null,
    /**
     * `"sync"`: the bill's upcoming entries follow the change (pause and end remove them, resume
     * creates them from today). Omitted (`keep`) leaves them alone. api/index.ts:796.
     */
    val upcomingEntries: String? = null,
)

/** `BillHistoryEntry` packages/types/src/api/index.ts:820. */
@Serializable
data class BillHistoryEntry(
    val id: String,
    /** YYYY-MM-DD */
    val date: String,
    val amount: Double,
    val description: String = "",
)

/**
 * `BillHistorySummary` packages/types/src/api/index.ts:832: what a bill has logged so far (entries
 * dated today or earlier), the Ended archive's "12 payments · $1,378.32 · Jan - Oct 2026".
 */
@Serializable
data class BillHistorySummary(
    val count: Int = 0,
    val total: Double = 0.0,
    val firstDate: String? = null,
    val lastDate: String? = null,
    /** Newest first; only with `includeEntries=true` and a single id. */
    val entries: List<BillHistoryEntry>? = null,
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
