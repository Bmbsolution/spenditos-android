package com.spenditos.android.feature.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.debts.DebtActions
import com.spenditos.android.data.debts.DebtWithPlan
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** State of the Debts section of Plan -> Income & bills (iOS `DebtListViewModel`). Reads only. */
data class DebtListUiState(
    val debts: List<DebtWithPlan> = emptyList(),
    val currency: String = "USD",
    val isLoading: Boolean = false,
    /** True once a load finished, so the empty state does not flash while loading. */
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    val hasDebts: Boolean get() = debts.isNotEmpty()

    /** Debts still being paid, in the order the server returned them. */
    val activeDebts: List<DebtWithPlan> get() = debts.filter { !it.summary.isPaidOff }

    /** Active debts first, paid-off debts last (dimmed in the list). Each group keeps the server's order. */
    val sortedDebts: List<DebtWithPlan> get() = activeDebts + debts.filter { it.summary.isPaidOff }

    /** What is left to pay across the debts still open. */
    val totalOwed: Double get() = DebtFormat.cents(activeDebts.sumOf { it.summary.balance })

    val allPaidOff: Boolean get() = hasDebts && activeDebts.isEmpty()

    /** Header subtitle: "$16,540 owed" or "All paid off". Null without debts. */
    val headerSubtitle: String?
        get() = when {
            !hasDebts -> null
            allPaidOff -> "All paid off"
            else -> "${DebtFormat.money(totalOwed, currency)} owed"
        }

    fun debt(withId: String): DebtWithPlan? = debts.firstOrNull { it.id == withId }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtListViewModel @Inject constructor(
    private val debts: DebtActions,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DebtListUiState())
    val state: StateFlow<DebtListUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.currency().collect { currency -> mutableState.update { it.copy(currency = currency) } }
        }
        viewModelScope.launch {
            // A debt write (or a bill / transaction change) moves balances: reload quietly.
            invalidations.events.debounce(250).collect { changed ->
                if (Aggregate.TRANSACTION in changed || Aggregate.RECURRING in changed) refresh()
            }
        }
        viewModelScope.launch { load() }
    }

    /** First load: shows the loading flag. */
    suspend fun load() {
        mutableState.update { it.copy(isLoading = true) }
        try {
            fetch()
        } finally {
            mutableState.update { it.copy(isLoading = false, hasLoaded = true) }
        }
    }

    /** Reload without the loading flag (after a change, retry). */
    suspend fun refresh() {
        fetch()
        mutableState.update { it.copy(hasLoaded = true) }
    }

    fun retry() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun fetch() {
        try {
            val list = debts.list(LocalDate.now(clock))
            mutableState.update { it.copy(debts = list, error = null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Keep what is on screen: a failed refresh must not blank the list.
            mutableState.update { it.copy(error = DebtErrorMessage.message(error)) }
        }
    }
}
