package com.spenditos.android.feature.plan

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
        if (entries.size > 100) return "A draft can have up to 100 one-time items."
        if (pendingEdits.size > 100) return "A draft has too many changes."
        if (pendingLogs.size > 50 || pendingSkips.size > 50) return "A draft has too many scheduled changes."
        if (pendingDeletes.size > 100) return "A draft has too many deletions."

        if (entries.map { it.id }.toSet().size != entries.size) return "Each one-time item needs a unique id."
        for (entry in entries) {
            if (entry.id.isBlank() || entry.id.length > 100) return "A one-time item has an invalid id."
            if (entry.idempotencyKeyId.isBlank() || entry.idempotencyKeyId.length > 200) return "A one-time item has an invalid id."
            if (!entry.date.isDateIn(month)) return "One-time item dates must be in this month."
            if (entry.description.trim().isEmpty() || entry.description.length > 200) return "Enter a description of up to 200 characters."
            if (!entry.amount.isValidAmount()) return "Enter a valid amount."
            if (entry.categoryId.isBlank() || entry.categoryId.length > 200) return "Choose a category."
            if (entry.recurring !is JsonNull) return "Only one-time items can be added here."
        }

        val editIds = pendingEdits.map { it.transactionId }
        if (editIds.toSet().size != editIds.size) return "Each transaction can have one pending change."
        for (edit in pendingEdits) {
            if (edit.transactionId.isBlank() || edit.transactionId.length > 200) return "A transaction change has an invalid id."
            if (edit.amount == null && edit.date == null && edit.description == null && edit.categoryId == null) return "A transaction change is empty."
            if (edit.amount != null && !edit.amount.isValidAmount()) return "Enter a valid amount."
            if (edit.date != null && !edit.date.isDateIn(month)) return "Changed dates must be in this month."
            if (edit.description?.length?.let { it > 200 } == true) return "Descriptions can have up to 200 characters."
            if (edit.categoryId?.let { it.isBlank() || it.length > 200 } == true) return "Choose a category."
        }

        val logKeys = pendingLogs.map { "${it.templateId}:${it.month}" }
        if (logKeys.toSet().size != logKeys.size) return "Each scheduled item can have one change per month."
        for (log in pendingLogs) {
            if (log.templateId.isBlank() || log.templateId.length > 200 || log.month != month.toString()) return "A scheduled change must target this month."
            if (log.overrides.size > 10) return "This schedule has too many amount changes for one month."
            val dates = log.overrides.map { it.occurrenceDate }
            if (dates.toSet().size != dates.size) return "Each scheduled date can have one amount change."
            for (override in log.overrides) {
                if (!override.occurrenceDate.isDateIn(month) || override.date?.isDateIn(month) == false) return "Changed dates must be in this month."
                if (override.amount == null && override.date == null) return "A scheduled change is empty."
                if (override.amount != null && !override.amount.isValidAmount()) return "Enter a valid amount."
            }
        }

        val deleteIds = pendingDeletes.map { it.transactionId }
        if (deleteIds.any { it.isBlank() || it.length > 200 }) return "A deletion has an invalid transaction id."
        if (deleteIds.toSet().size != deleteIds.size) return "A transaction can only be deleted once."
        if (deleteIds.any { it in editIds }) return "A transaction cannot have a change and a deletion."

        val skipKeys = pendingSkips.map { "${it.templateId}:${it.month}" }
        if (skipKeys.toSet().size != skipKeys.size) return "Each scheduled item can be skipped once per month."
        for (skip in pendingSkips) {
            if (skip.templateId.isBlank() || skip.templateId.length > 200 || skip.month != month.toString()) return "A skip must target this month."
            if (skip.dates.isEmpty() || skip.dates.size > 31) return "Choose at least one date to skip."
            if (skip.dates.toSet().size != skip.dates.size) return "Skip dates must be unique."
            if (skip.dates.any { !it.isDateIn(month) }) return "Skip dates must be in this month."
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
