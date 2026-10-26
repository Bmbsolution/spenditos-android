package com.spenditos.android.feature.home.monthbalance

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.monthbalance.MonthBalanceActions
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import com.spenditos.android.data.monthbalance.MonthBalanceData
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
     * Saves [choice] for the card shown in [monthKey]. Ignored while a save is already running (a double tap
     * sends one request; the server write is idempotent anyway) and when there is no card for [monthKey]: an
     * expired card is not on screen.
     */
    suspend fun answer(monthKey: String, choice: MonthBalanceChoice) {
        val current = mutableState.value
        if (current.isSaving) return
        val month = current.dataFor(monthKey)?.month ?: return
        mutableState.update { it.copy(isSaving = true, error = null) }
        val id = ++requestId
        try {
            val result = actions.set(month, choice)
            if (id == requestId) mutableState.update { it.copy(loaded = result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(error = messageFor(error)) }
        } finally {
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    private fun messageFor(error: Throwable): String =
        if (error is ApiError.Network && !isOnline()) {
            MonthBalanceCopy.OFFLINE
        } else {
            DebtAddText.message(error, isWrite = true, online = true)
        }
}
