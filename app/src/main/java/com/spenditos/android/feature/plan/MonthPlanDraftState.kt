package com.spenditos.android.feature.plan

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanDraftEntry
import com.spenditos.android.data.model.MonthlyPlanPendingDelete
import com.spenditos.android.data.model.MonthlyPlanPendingEdit
import com.spenditos.android.data.model.MonthlyPlanPendingLog
import com.spenditos.android.data.model.MonthlyPlanPendingSkip
import com.spenditos.android.data.model.SaveMonthlyPlanDraftRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.JsonNull

data class MonthPlanDraftState(
    val entries: List<MonthlyPlanDraftEntry> = emptyList(),
    val pendingEdits: List<MonthlyPlanPendingEdit> = emptyList(),
    val pendingLogs: List<MonthlyPlanPendingLog> = emptyList(),
    val pendingDeletes: List<MonthlyPlanPendingDelete> = emptyList(),
    val pendingSkips: List<MonthlyPlanPendingSkip> = emptyList(),
) {
    val changeCount: Int get() = entries.size + pendingEdits.size + pendingLogs.size + pendingDeletes.size + pendingSkips.size
    val isEmpty: Boolean get() = changeCount == 0

    fun toSaveRequest(month: YearMonth) = SaveMonthlyPlanDraftRequest(
        month = month.toString(),
        entries = entries,
        transactionDelta = emptyMap(),
        pendingEdits = pendingEdits,
        pendingLogs = pendingLogs,
        pendingDeletes = pendingDeletes,
        pendingSkips = pendingSkips,
    )

    fun toData(month: YearMonth, updatedAt: String = "") = MonthlyPlanDraftData(
        month = month.toString(),
        entries = entries,
        transactionDelta = emptyMap(),
        pendingEdits = pendingEdits,
        pendingLogs = pendingLogs,
        pendingDeletes = pendingDeletes,
        pendingSkips = pendingSkips,
        updatedAt = updatedAt,
    )

    /** Mirrors packages/core/src/schemas/monthly-plan-draft.ts:1-240 before every PUT. */
    fun validationError(month: YearMonth): String? {
        if (entries.size > 100) return Strings.get(R.string.plan_draft_a_draft_can_have_up)
        if (pendingEdits.size > 100) return Strings.get(R.string.plan_draft_a_draft_has_too_many)
        if (pendingLogs.size > 50 || pendingSkips.size > 50) return Strings.get(R.string.plan_draft_a_draft_has_too_many)
        if (pendingDeletes.size > 100) return Strings.get(R.string.plan_draft_a_draft_has_too_many)

        if (entries.map { it.id }.toSet().size != entries.size) return Strings.get(R.string.plan_each_one_time_item_needs)
        for (entry in entries) {
            if (entry.id.isBlank() || entry.id.length > 100) return Strings.get(R.string.plan_draft_a_one_time_item_has)
            if (entry.idempotencyKeyId.isBlank() || entry.idempotencyKeyId.length > 200) return Strings.get(R.string.plan_draft_a_one_time_item_has)
            if (!entry.date.isDateIn(month)) return Strings.get(R.string.plan_one_time_item_dates_must)
            if (entry.description.trim().isEmpty() || entry.description.length > 200) return Strings.get(R.string.plan_enter_a_description_of_up)
            if (!entry.amount.isValidAmount()) return Strings.get(R.string.plan_enter_a_valid_amount)
            if (entry.categoryId.isBlank() || entry.categoryId.length > 200) return Strings.get(R.string.plan_choose_a_category)
            if (entry.recurring !is JsonNull) return Strings.get(R.string.plan_only_one_time_items_can)
        }

        val editIds = pendingEdits.map { it.transactionId }
        if (editIds.toSet().size != editIds.size) return Strings.get(R.string.plan_each_transaction_can_have_one)
        for (edit in pendingEdits) {
            if (edit.transactionId.isBlank() || edit.transactionId.length > 200) return Strings.get(R.string.plan_draft_a_transaction_change_has_an)
            if (edit.amount == null && edit.date == null && edit.description == null && edit.categoryId == null) return Strings.get(R.string.plan_draft_a_transaction_change_is_empty)
            if (edit.amount != null && !edit.amount.isValidAmount()) return Strings.get(R.string.plan_enter_a_valid_amount)
            if (edit.date != null && !edit.date.isDateIn(month)) return Strings.get(R.string.plan_changed_dates_must_be_in)
            if (edit.description?.length?.let { it > 200 } == true) return Strings.get(R.string.plan_descriptions_can_have_up_to)
            if (edit.categoryId?.let { it.isBlank() || it.length > 200 } == true) return Strings.get(R.string.plan_choose_a_category)
        }

        val logKeys = pendingLogs.map { "${it.templateId}:${it.month}" }
        if (logKeys.toSet().size != logKeys.size) return Strings.get(R.string.plan_each_scheduled_item_can_have)
        for (log in pendingLogs) {
            if (log.templateId.isBlank() || log.templateId.length > 200 || log.month != month.toString()) return Strings.get(R.string.plan_draft_a_scheduled_change_must_target)
            if (log.overrides.size > 10) return Strings.get(R.string.plan_this_schedule_has_too_many)
            val dates = log.overrides.map { it.occurrenceDate }
            if (dates.toSet().size != dates.size) return Strings.get(R.string.plan_each_scheduled_date_can_have)
            for (override in log.overrides) {
                if (!override.occurrenceDate.isDateIn(month) || override.date?.isDateIn(month) == false) return Strings.get(R.string.plan_changed_dates_must_be_in)
                if (override.amount == null && override.date == null) return Strings.get(R.string.plan_draft_a_scheduled_change_is_empty)
                if (override.amount != null && !override.amount.isValidAmount()) return Strings.get(R.string.plan_enter_a_valid_amount)
            }
        }

        val deleteIds = pendingDeletes.map { it.transactionId }
        if (deleteIds.any { it.isBlank() || it.length > 200 }) return Strings.get(R.string.plan_draft_a_deletion_has_an_invalid)
        if (deleteIds.toSet().size != deleteIds.size) return Strings.get(R.string.plan_draft_a_transaction_can_only_be)
        if (deleteIds.any { it in editIds }) return Strings.get(R.string.plan_draft_a_transaction_cannot_have_a)

        val skipKeys = pendingSkips.map { "${it.templateId}:${it.month}" }
        if (skipKeys.toSet().size != skipKeys.size) return Strings.get(R.string.plan_each_scheduled_item_can_be)
        for (skip in pendingSkips) {
            if (skip.templateId.isBlank() || skip.templateId.length > 200 || skip.month != month.toString()) return Strings.get(R.string.plan_draft_a_skip_must_target_this)
            if (skip.dates.isEmpty() || skip.dates.size > 31) return Strings.get(R.string.plan_choose_at_least_one_date)
            if (skip.dates.toSet().size != skip.dates.size) return Strings.get(R.string.plan_skip_dates_must_be_unique)
            if (skip.dates.any { !it.isDateIn(month) }) return Strings.get(R.string.plan_skip_dates_must_be_in)
        }
        return null
    }
}

internal fun parsePlanAmount(text: String): BigDecimal? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    return normalized.toBigDecimalOrNull()?.takeIf { it.scale() <= 2 && it.signum() > 0 }
}

private fun String.isDateIn(month: YearMonth): Boolean = runCatching {
    val date = LocalDate.parse(this)
    date.toString() == this && YearMonth.from(date) == month
}.getOrDefault(false)

private fun Double.isValidAmount(): Boolean = isFinite() && this > 0.0 && this <= 1_000_000_000.0
