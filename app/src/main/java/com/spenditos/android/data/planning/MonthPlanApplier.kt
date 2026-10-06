package com.spenditos.android.data.planning

import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanLogOverride
import com.spenditos.android.data.model.MonthlyPlanPendingEdit
import com.spenditos.android.data.model.MonthlyPlanPendingLog
import com.spenditos.android.data.model.MonthlyPlanTransactionUpdate
import com.spenditos.android.data.model.Transaction
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MonthPlanApplyResult {
    data object Applied : MonthPlanApplyResult
    data class Failed(val operation: String) : MonthPlanApplyResult
}

/** Client-orchestrated, retry-safe writes for one saved month draft. */
@Singleton
class MonthPlanApplier @Inject constructor(
    private val repository: MonthPlanRepository,
    private val invalidations: FinanceInvalidationBus,
) {
    suspend fun apply(month: YearMonth, draft: MonthlyPlanDraftData): MonthPlanApplyResult {
        if (draft.entries.isEmpty() && draft.pendingSkips.isEmpty() && draft.pendingLogs.isEmpty() &&
            draft.pendingEdits.isEmpty() && draft.pendingDeletes.isEmpty()) {
            return if (attempt { repository.deleteDraft(month) }) {
                MonthPlanApplyResult.Applied
            } else {
                MonthPlanApplyResult.Failed("draft")
            }
        }

        // A skip is a no-op when repeated. Stop before starting later groups if one date fails.
        for (skip in draft.pendingSkips) {
            for (date in skip.dates) {
                if (!attempt {
                    repository.skipOccurrence(skip.templateId, date)
                    invalidations.invalidate(Aggregate.RECURRING)
                }) return MonthPlanApplyResult.Failed("skip")
            }
        }

        // Generation is idempotent per recurring item and scheduled date on the server.
        for (log in draft.pendingLogs) {
            if (!attempt {
                // Retry guard: the server dedupes generation by (item, SCHEDULED date). If an earlier
                // attempt already moved an occurrence off its scheduled date, generating again would
                // re-create the scheduled-date row and the override would then move THAT one too,
                // leaving two entries at the new date. So skip generation when the items already show
                // an applied move (the earlier attempt generated every occurrence before moving any).
                if (!alreadyGenerated(log, repository.transactions(month))) {
                    val result = repository.generate(month, log.templateId)
                    invalidations.invalidate(Aggregate.TRANSACTION)
                    if (result.errors > 0) throw IllegalStateException("Generation reported an error")
                }
                if (!applyOverrides(month, log)) throw IllegalStateException("A logged occurrence is not available")
            }) return MonthPlanApplyResult.Failed("log")
        }

        if (draft.pendingEdits.isNotEmpty()) {
            val records = try {
                repository.transactions(month)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return MonthPlanApplyResult.Failed("edit")
            }
            for (edit in draft.pendingEdits) {
                val current = records.firstOrNull { it.id == edit.transactionId }
                    ?: return MonthPlanApplyResult.Failed("edit")
                if (isEditApplied(current, edit)) continue
                if (!attempt {
                    repository.updateTransaction(edit.transactionId, edit.toRequest())
                    invalidations.invalidate(Aggregate.TRANSACTION)
                }) return MonthPlanApplyResult.Failed("edit")
            }
        }

        if (draft.pendingDeletes.isNotEmpty()) {
            val records = try {
                repository.transactions(month)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return MonthPlanApplyResult.Failed("delete")
            }
            for (pendingDelete in draft.pendingDeletes) {
                // A missing row means a prior DELETE committed before its response was lost.
                if (records.none { it.id == pendingDelete.transactionId }) continue
                if (!attempt {
                    repository.deleteTransaction(pendingDelete.transactionId)
                    invalidations.invalidate(Aggregate.TRANSACTION)
                }) return MonthPlanApplyResult.Failed("delete")
            }
        }

        for (entry in draft.entries) {
            if (!attempt {
                val result = repository.createOneTime(entry)
                val failed = result.failed.isNotEmpty() || result.results.any { it.error != null }
                val saved = result.duplicates > 0 || result.results.any { it.transaction != null }
                if (failed || !saved) throw IllegalStateException("Bulk create did not confirm the item")
                invalidations.invalidate(Aggregate.TRANSACTION)
            }) return MonthPlanApplyResult.Failed("one-time")
        }

        return if (attempt { repository.deleteDraft(month) }) {
            MonthPlanApplyResult.Applied
        } else {
            MonthPlanApplyResult.Failed("draft")
        }
    }

    /**
     * True when this item's occurrence was already moved by a previous attempt: no entry remains on
     * its scheduled date, and an entry of the item exists on the date it was moved to.
     */
    private fun alreadyGenerated(log: MonthlyPlanPendingLog, records: List<Transaction>): Boolean {
        val own = records.filter { it.templateId == log.templateId }
        return log.overrides.any { override ->
            val movedTo = override.date
            movedTo != null && movedTo != override.occurrenceDate &&
                own.none { it.date.take(10) == override.occurrenceDate } &&
                own.any { it.date.take(10) == movedTo && (override.amount == null || it.amount.sameAmount(override.amount)) }
        }
    }

    private suspend fun applyOverrides(month: YearMonth, log: MonthlyPlanPendingLog): Boolean {
        if (log.overrides.isEmpty()) return true
        val records = repository.transactions(month)
        for (override in log.overrides) {
            val current = resolveOverride(log, override, records) ?: return false
            if (isOverrideApplied(current, override)) continue
            repository.updateTransaction(current.id, override.toRequest())
            invalidations.invalidate(Aggregate.TRANSACTION)
        }
        return true
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun resolveOverride(
        log: MonthlyPlanPendingLog,
        override: MonthlyPlanLogOverride,
        records: List<Transaction>,
    ): Transaction? {
        val matching = records.filter { it.templateId == log.templateId }
        return matching.firstOrNull { it.date.take(10) == override.occurrenceDate }
            ?: override.date?.let { date -> matching.firstOrNull { it.date.take(10) == date } }
    }

    private fun isEditApplied(current: Transaction, edit: MonthlyPlanPendingEdit): Boolean =
        (edit.amount == null || current.amount.sameAmount(edit.amount)) &&
            (edit.date == null || current.date.take(10) == edit.date) &&
            (edit.description == null || current.description == edit.description) &&
            (edit.categoryId == null || current.categoryId == edit.categoryId)

    private fun isOverrideApplied(current: Transaction, override: MonthlyPlanLogOverride): Boolean =
        (override.amount == null || current.amount.sameAmount(override.amount)) &&
            (override.date == null || current.date.take(10) == override.date)

    private fun MonthlyPlanPendingEdit.toRequest() = MonthlyPlanTransactionUpdate(amount, date, description, categoryId)

    private fun MonthlyPlanLogOverride.toRequest() = MonthlyPlanTransactionUpdate(amount = amount, date = date)

    private fun Double.sameAmount(other: Double): Boolean =
        BigDecimal.valueOf(this).compareTo(BigDecimal.valueOf(other)) == 0
}
