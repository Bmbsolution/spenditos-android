package com.spenditos.android.feature.home.monthbalance

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.monthbalance.MonthBalanceActions
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import com.spenditos.android.data.monthbalance.MonthBalanceData
import com.spenditos.android.data.monthbalance.MonthBalanceKind
import com.spenditos.android.feature.debts.add.DebtAddText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the tracker holds: the last answer from the server, whether a save is running, and the last save error. */
data class MonthBalanceState(
    val loaded: MonthBalanceData? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    /**
     * The card's data, only while it is for [monthKey] (the device's current local month). The card lives
     * until the user answers it or the month ends: once the month rolls over, an unanswered card has expired
     * and is never shown (or answered) again; the new month evaluates its own previous month.
     */
    fun dataFor(monthKey: String): MonthBalanceData? = loaded?.takeIf { it.month == monthKey }

    /** The card for [monthKey], or null when there is nothing to ask. */
    fun cardFor(monthKey: String): MonthBalanceCard? =
        dataFor(monthKey)?.takeIf { it.showPrompt }?.let { MonthBalanceCard(it, isSaving, error) }

    /** Added to "left after bills" (0 unless the user chose "Carry over" for [monthKey]). */
    fun carriedInFor(monthKey: String): Double = dataFor(monthKey)?.carriedIn ?: 0.0

    /**
     * The "Not included" / "Not tracked" row of the math sheet: the user answered "No thanks" / "Not needed"
     * (or removed a carry-over) for [monthKey] and there is still something to act on. Nothing while the card
     * is showing (unanswered), after a carry-over, or for a shortfall that already has a debt.
     */
    fun followUpFor(monthKey: String): MonthBalanceFollowUp? {
        val data = dataFor(monthKey) ?: return null
        if (data.choice != MonthBalanceChoice.DISMISSED || data.kind == MonthBalanceKind.NONE) return null
        if (data.kind == MonthBalanceKind.SHORTFALL && data.shortfallDebtId != null) return null
        return MonthBalanceFollowUp(data)
    }
}

/** An answered, still actionable month-start balance (see [MonthBalanceState.followUpFor]). Never logged: it holds amounts. */
data class MonthBalanceFollowUp(val data: MonthBalanceData)

/** How an answer ended. */
sealed interface AnswerOutcome {
    /** Saved (or a newer read replaced it); nothing to tell the user. */
    data object Saved : AnswerOutcome

    /** Nothing was sent: no card for that month, or a save is already running. */
    data object Ignored : AnswerOutcome

    /** Not saved; [message] is plain language. A [MonthBalanceTracker.answer] with `optimistic` has already rolled back. */
    data class Failed(val message: String) : AnswerOutcome
}

/** What the Home card shows. Never logged: it holds amounts. */
data class MonthBalanceCard(val data: MonthBalanceData, val isSaving: Boolean, val error: String?)

/**
 * The month-start card's state (docs/features/month-balance.md; iOS `MonthBalanceViewModel`). A best-effort
 * side panel: a failed load hides the card and never blocks Home. The answer is an online-only write of one
 * record per user and month, so a repeat is harmless. Nothing here is logged (amounts and names).
 *
 * Not thread-safe by itself: call it from one dispatcher (the ViewModel's main scope), like the iOS main actor.
 */
class MonthBalanceTracker(
    private val actions: MonthBalanceActions,
    private val isOnline: () -> Boolean,
) {
    private val mutableState = MutableStateFlow(MonthBalanceState())
    val state: StateFlow<MonthBalanceState> = mutableState.asStateFlow()

    private var requestId = 0

    /** `GET /month-balance?month=[monthKey]`. A newer load or answer wins over a slower, older one. */
    suspend fun load(monthKey: String) {
        val id = ++requestId
        try {
            val result = actions.get(monthKey)
            if (id != requestId) return
            mutableState.update { it.copy(loaded = result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (id != requestId) return
            mutableState.update { it.copy(loaded = null) }
        }
    }

    /**
     * Saves [choice] for the month shown in [monthKey]. Ignored while a save is already running (a double tap
     * sends one request; the server write is idempotent anyway) and when there is no data for [monthKey]: an
     * expired card is not on screen.
     *
     * With [optimistic] the new state shows at once ("Remove" on a carry-over: left after bills updates before
     * the answer is saved) and is rolled back if the save fails. With [errorOnCard] a failure is also kept in
     * [MonthBalanceState.error] for the card; otherwise it is only returned.
     */
    suspend fun answer(
        monthKey: String,
        choice: MonthBalanceChoice,
        optimistic: Boolean = false,
        errorOnCard: Boolean = true,
    ): AnswerOutcome = save(monthKey, choice, optimistic, errorOnCard) { month -> actions.set(month, choice) }

    /**
     * "Pay it in <Month>": one expense entry for the whole shortfall on [date] (`YYYY-MM-DD`), named [description].
     * The card hides at once and comes back with the error if the save fails. Ignored unless the card for
     * [monthKey] is a shortfall.
     */
    suspend fun pay(monthKey: String, date: String, description: String): AnswerOutcome {
        if (mutableState.value.dataFor(monthKey)?.kind != MonthBalanceKind.SHORTFALL) return AnswerOutcome.Ignored
        return save(monthKey, MonthBalanceChoice.PAYING, optimistic = true, errorOnCard = true) { month ->
            actions.pay(month, date, description)
        }
    }

    private suspend fun save(
        monthKey: String,
        choice: MonthBalanceChoice,
        optimistic: Boolean,
        errorOnCard: Boolean,
        send: suspend (month: String) -> MonthBalanceData,
    ): AnswerOutcome {
        val current = mutableState.value
        if (current.isSaving) return AnswerOutcome.Ignored
        val before = current.dataFor(monthKey) ?: return AnswerOutcome.Ignored
        val month = before.month
        mutableState.update {
            it.copy(
                isSaving = true,
                error = null,
                loaded = if (optimistic) before.answeredAs(choice) else it.loaded,
            )
        }
        val id = ++requestId
        var outcome: AnswerOutcome = AnswerOutcome.Saved
        try {
            val result = send(month)
            if (id == requestId) {
                mutableState.update { it.copy(loaded = result) }
            } else {
                // A read that started meanwhile may predate this write: ask again so the screen ends on the saved answer.
                load(monthKey)
            }
        } catch (cancelled: CancellationException) {
            if (optimistic && id == requestId) mutableState.update { it.copy(loaded = before) }
            throw cancelled
        } catch (error: Exception) {
            val message = messageFor(error)
            outcome = AnswerOutcome.Failed(message)
            mutableState.update {
                it.copy(
                    // A newer read already decided what is on screen; only undo our own guess.
                    loaded = if (optimistic && id == requestId) before else it.loaded,
                    error = if (errorOnCard) message else it.error,
                )
            }
        } finally {
            mutableState.update { it.copy(isSaving = false) }
        }
        return outcome
    }

    /** What the card state looks like once [choice] is saved, before the server has said so. */
    private fun MonthBalanceData.answeredAs(choice: MonthBalanceChoice): MonthBalanceData = copy(
        choice = choice,
        showPrompt = false,
        carriedIn = if (choice == MonthBalanceChoice.CARRIED && kind == MonthBalanceKind.SURPLUS) balance else 0.0,
    )

    private fun messageFor(error: Throwable): String =
        if (error is ApiError.Network && !isOnline()) {
            MonthBalanceCopy.OFFLINE
        } else {
            DebtAddText.message(error, isWrite = true, online = true)
        }
}
