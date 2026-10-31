package com.spenditos.android.feature.recurring

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.BillLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.recurring.BillLifecycleRequests
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.recurring.RecurringCategorySource
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class IncomeAndBillsUiState(
    val templates: List<RecurringTemplate> = emptyList(),
    val categories: List<Category> = emptyList(),
    val mainIncomeTemplateId: String? = null,
    val currency: String = "USD",
    val isLoading: Boolean = true,
    val isWriting: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    /** This month's logged entries, for the summary strip. Null until loaded (or when loading failed): the strip then leaves out "paid". */
    val monthEntries: List<Transaction>? = null,
    /** What each ended bill has logged, for the Ended archive rows (#403). */
    val endedHistory: Map<String, BillHistorySummary> = emptyMap(),
) {
    private val categoryById get() = categories.associateBy { it.id }
    fun isIncome(template: RecurringTemplate): Boolean = categoryById[template.categoryId]?.isIncome == true

    /** Active income. Paused and ended items have their own places (#403). */
    val incomeTemplates: List<RecurringTemplate>
        get() = templates.filter { it.lifecycle == BillLifecycle.ACTIVE && isIncome(it) }
    /** Active bills. A debt's payment bill lives under Debts, not Bills (docs/features/debts.md "Clients"). */
    val billTemplates: List<RecurringTemplate>
        get() = templates.filter { it.lifecycle == BillLifecycle.ACTIVE && it.debtId == null && !isIncome(it) }
    /** Paused income and bills: the collapsed group at the bottom of the list. */
    val pausedTemplates: List<RecurringTemplate>
        get() = templates.filter { it.lifecycle == BillLifecycle.PAUSED && it.debtId == null }
    /** Ended income and bills, newest end first: the Ended archive. A debt's bill stays with its debt. */
    val endedTemplates: List<RecurringTemplate>
        get() = templates.filter { it.lifecycle == BillLifecycle.ENDED && it.debtId == null }
            .sortedByDescending { BillText.endedDate(it) ?: java.time.Instant.MIN }
    val isEmpty: Boolean get() = !isLoading && error == null && templates.isEmpty()

    /** The strip at the top (#403). Null when there are no active bills. */
    fun monthSummary(today: LocalDate = LocalDate.now()): BillsMonthSummary? =
        billsMonthSummary(templates, this::isIncome, monthEntries, today)
}

sealed interface IncomeAndBillsEvent {
    data object Saved : IncomeAndBillsEvent
    data object Deleted : IncomeAndBillsEvent
    data class Generated(val result: GenerateTransactionsResult) : IncomeAndBillsEvent
    /** A pause, resume, end or start-again went through: "Bell internet ended" with an optional Undo. */
    data class Lifecycle(val text: String, val undo: (() -> Unit)?) : IncomeAndBillsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeAndBillsViewModel @Inject constructor(
    private val recurring: RecurringActions,
    private val categories: RecurringCategorySource,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(IncomeAndBillsUiState())
    val state: StateFlow<IncomeAndBillsUiState> = mutableState.asStateFlow()
    private val _events = Channel<IncomeAndBillsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(recurring.observe(), recurring.observeMainIncomeId(), categories.observe(), settings.currency()) { templates, mainIncomeId, categoryRows, currency ->
                mutableState.update { it.copy(templates = templates, mainIncomeTemplateId = mainIncomeId, categories = categoryRows, currency = currency) }
            }.collect {}
        }
        viewModelScope.launch {
            invalidations.events.debounce(250).collect { changed ->
                if (Aggregate.RECURRING in changed) {
                    refreshFromInvalidation()
                } else if (Aggregate.TRANSACTION in changed) {
                    loadMonthEntries()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            try {
                recurring.refresh()
                categories.refresh()
                mutableState.update { it.copy(isLoading = false) }
                loadMonthEntries()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showLoadFailure(error)
            }
        }
    }

    fun create(request: CreateRecurringTemplateRequest) = write {
        recurring.create(request)
        _events.send(IncomeAndBillsEvent.Saved)
    }

    fun update(id: String, request: UpdateRecurringTemplateRequest) = write {
        recurring.update(id, request)
        _events.send(IncomeAndBillsEvent.Saved)
    }

    fun delete(id: String) = write {
        recurring.delete(id)
        _events.send(IncomeAndBillsEvent.Deleted)
    }

    fun setMainIncome(id: String?) {
        if (id != null) {
            val template = state.value.templates.firstOrNull { it.id == id } ?: return
            if (state.value.categories.firstOrNull { it.id == template.categoryId }?.isIncome != true || !template.isActive) return
        }
        write { recurring.setMainIncome(id); _events.send(IncomeAndBillsEvent.Saved) }
    }

    fun generate(month: YearMonth, templateIds: List<String>? = null) = write {
        val result = recurring.generate(month, templateIds)
        _events.send(IncomeAndBillsEvent.Generated(result))
        loadMonthEntries()
    }

    // Month data (#403)

    /** Logged entries dated in [month] (the Generate sheet's "already logged" check). */
    suspend fun entriesIn(month: YearMonth): List<Transaction> = recurring.monthEntries(month)

    private suspend fun loadMonthEntries() {
        val loaded = try {
            recurring.monthEntries(YearMonth.now())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        mutableState.update { it.copy(monthEntries = loaded) }
    }

    // Lifecycle (#403)

    fun pause(template: RecurringTemplate) = lifecycle(
        text = Strings.get(R.string.recurring_x_paused, template.description),
        action = { recurring.pause(template.id) },
        undo = { recurring.resume(template.id) },
    )

    fun resume(template: RecurringTemplate) = lifecycle(
        text = Strings.get(R.string.recurring_x_resumed, template.description),
        action = { recurring.resume(template.id) },
        undo = { recurring.pause(template.id) },
    )

    /**
     * End now, or after one more payment ([oneMore]). A last payment due this month is already
     * logged, so the bill ends now and keeps it; a later one is made by the generator, which then
     * ends the bill.
     */
    fun end(template: RecurringTemplate, oneMore: Boolean, today: LocalDate = LocalDate.now()) {
        val next = BillSchedule.nextDate(template, today)
        val nextIsThisMonth = next?.let { YearMonth.from(it) == YearMonth.from(today) } ?: false

        if (oneMore && next != null && !nextIsThisMonth) {
            val previous = template.occurrencesRemaining
            lifecycle(
                text = Strings.get(R.string.recurring_x_ends_after, template.description, BillText.monthDay(next)),
                action = { recurring.endAfterNextPayment(template.id) },
                undo = { recurring.update(template.id, BillLifecycleRequests.restoreOccurrences(previous)) },
            )
            return
        }

        val wasPaused = template.lifecycle == BillLifecycle.PAUSED
        lifecycle(
            text = Strings.get(R.string.recurring_x_ended, template.description),
            action = { recurring.end(template.id, keepUpcoming = oneMore) },
            undo = { if (wasPaused) recurring.pause(template.id) else recurring.resume(template.id) },
        )
    }

    fun startAgain(template: RecurringTemplate, nextDate: LocalDate) = lifecycle(
        text = Strings.get(R.string.recurring_x_started_again, template.description),
        action = { recurring.startAgain(template.id, nextDate) },
        undo = null,
    )

    fun loadEndedHistory() {
        val ids = state.value.endedTemplates.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // If the history call fails the rows just leave the totals out.
            val history = try {
                recurring.history(ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            mutableState.update { it.copy(endedHistory = history) }
        }
    }

    /** One ended bill's history with its payments, for the detail (throws when the call fails). */
    suspend fun fetchHistory(id: String): BillHistorySummary =
        recurring.history(listOf(id), includeEntries = true)[id] ?: BillHistorySummary()

    private fun lifecycle(
        text: String,
        action: suspend () -> RecurringTemplate,
        undo: (suspend () -> RecurringTemplate)?,
    ) = write {
        action()
        // Undo is one more write; a failure shows the usual error and the bill keeps its new state.
        val undoAction: (() -> Unit)? = undo?.let { run -> { write { run(); loadMonthEntries() } } }
        _events.send(IncomeAndBillsEvent.Lifecycle(text, undoAction))
        loadMonthEntries()
    }

    private fun write(operation: suspend () -> Unit) {
        if (mutableState.value.isWriting) return
        mutableState.update { it.copy(isWriting = true, error = null, isOffline = false) }
        viewModelScope.launch {
            try {
                operation()
                mutableState.update { it.copy(isWriting = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(isWriting = false, isOffline = error is ApiError.Network, error = writeMessage(error))
                }
            }
        }
    }

    private suspend fun refreshFromInvalidation() {
        try {
            recurring.refresh()
            mutableState.update { it.copy(error = null, isOffline = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showLoadFailure(error)
        }
    }

    private fun showLoadFailure(error: Exception) {
        mutableState.update {
            it.copy(
                isLoading = false,
                isOffline = error is ApiError.Network || error is IOException,
                error = if (error is ApiError.Network || error is IOException) Strings.get(R.string.common_you_re_offline_check_your) else Strings.get(R.string.recurring_couldn_t_load_income_and),
            )
        }
    }

    private fun writeMessage(error: Throwable): String =
        if (error is ApiError.Network || error is IOException) Strings.get(R.string.recurring_you_re_offline_reconnect_to)
        else Strings.get(R.string.recurring_couldn_t_save_changes_please)
}
