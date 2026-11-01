package com.spenditos.android.feature.home

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.feature.home.pulse.CashFlowHistory
import com.spenditos.android.feature.home.pulse.DayFlow
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.monthbalance.MonthBalanceActions
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.feature.debts.add.DebtAddText
import com.spenditos.android.feature.home.monthbalance.AnswerOutcome
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCard
import com.spenditos.android.feature.home.monthbalance.MonthBalanceFollowUp
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCopy
import com.spenditos.android.feature.home.monthbalance.MonthBalanceTracker
import com.spenditos.android.feature.home.insights.HomeInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import com.spenditos.android.feature.transactions.toEditTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate,
    val month: YearMonth,
    val currency: String = "USD",
    val displayName: String = "",
    val transactionCount: Int = 0,
    val status: CashFlowStatus? = null,
    val breakdown: CashFlowBreakdown? = null,
    val insights: HomeInsights? = null,
    val history: List<DayFlow> = emptyList(),
    val recent: List<RecentActivity> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val isOffline: Boolean = false,
    val refreshError: String? = null,
    /** The month-start card (docs/features/month-balance.md); null when there is nothing to ask. Home only. */
    val monthBalance: MonthBalanceCard? = null,
    /** The math sheet's "Not included" / "Not tracked" row; null when there is nothing to act on. */
    val monthBalanceFollowUp: MonthBalanceFollowUp? = null,
    /** An answer is being saved: the card, Remove and the follow-up buttons wait for it. */
    val monthBalanceSaving: Boolean = false,
) {
    /** Remove is offered while a carry-over is adding to left after bills. */
    val canRemoveCarriedIn: Boolean get() = status?.carriedIn?.signum() == 1

    val isLoading: Boolean get() = isRefreshing && !hasLoaded && transactionCount == 0
    val isEmpty: Boolean get() = hasLoaded && transactionCount == 0
    val showingSavedData: Boolean get() = isOffline
    val upcomingBills: List<CashFlowBreakdown.Line> get() = breakdown?.billsLeft?.take(5).orEmpty()
}

/** One row of Home's "Recent activity" (iOS: the last few transactions up to today). */
data class RecentActivity(
    val id: String,
    val title: String,
    val category: String,
    val date: LocalDate,
    val amount: Double,
    val isIncome: Boolean,
    val colorHex: String?,
    val icon: String? = null,
)

private data class HomeData(val transactions: List<TransactionEntity>, val week: List<TransactionEntity>, val categories: List<Category>)
private data class HomeSettings(val currency: String, val displayName: String, val refresh: HomeRefreshState)
private data class HomeRefreshState(
    val isRefreshing: Boolean = true,
    val hasLoaded: Boolean = false,
    val isOffline: Boolean = false,
    val error: Throwable? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
    private val clock: Clock,
    monthBalanceActions: MonthBalanceActions,
    connectivity: ConnectivityStatus,
) : ViewModel() {
    // Best-effort side panel: a failed load hides the card and never blocks Home.
    private val monthBalance = MonthBalanceTracker(monthBalanceActions) { connectivity.isOnline.value }

    // Today advances with the clock (refresh, invalidation, a midnight rollover while Home
    // stays alive) so a ViewModel created yesterday never keeps yesterday's month and ranges.
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The saved transaction a Home pin stands for, as the edit form opens it (null if it is gone). */
    suspend fun editTargetFor(id: String, date: LocalDate): com.spenditos.android.feature.transactions.EditTarget? =
        transactions.observeRange(date, date).first().firstOrNull { it.id == id }
            ?.toEditTarget()

    private val refreshState = MutableStateFlow(HomeRefreshState())
    private val monthRows = today.flatMapLatest { t ->
        val month = YearMonth.from(t)
        transactions.observeRange(month.atDay(1), month.atEndOfMonth())
    }

    // The last seven days can start in the previous month; the sparkline reads them from the same cache.
    private val weekRows = today.flatMapLatest { t -> transactions.observeRange(t.minusDays(6), t) }

    // Re-reads the clock on subscribe and at each local midnight. It is a source of the state
    // flow below, so it is collected only while Home is observed (WhileSubscribed): no idle
    // timer runs in the background, and a re-subscribed Home catches up on the spot. The date
    // is also re-read on every refresh().
    private val dayTicks: Flow<Unit> = flow {
        today.value = LocalDate.now(clock)
        emit(Unit)
        while (currentCoroutineContext().isActive) {
            delay(clock.millisUntilNextDayStart())
            today.value = LocalDate.now(clock)
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        today,
        dayTicks,
        combine(monthRows, weekRows, categories.observe()) { rows, week, categoryRows -> HomeData(rows, week, categoryRows) },
        combine(settings.currency(), settings.displayName(), refreshState) { currency, name, refresh -> HomeSettings(currency, name, refresh) },
        monthBalance.state,
    ) { today, _, data, settingsState, monthBalanceState ->
        // The card (and its carried-in amount) belongs to the LOCAL month it was loaded for; at the month
        // rollover an unanswered card simply stops being shown.
        val monthKey = MonthBalanceCopy.monthKey(today)
        val models = data.transactions.map { it.toModel() }
        val weekModels = data.week.map { it.toModel() }
        HomeUiState(
            today = today,
            month = YearMonth.from(today),
            currency = settingsState.currency,
            displayName = settingsState.displayName,
            transactionCount = models.size,
            status = CashFlowStatus.make(
                models, data.categories, today,
                carriedIn = CashFlowStatus.cashAmount(monthBalanceState.carriedInFor(monthKey)),
            ),
            breakdown = CashFlowBreakdown.make(models, data.categories, today),
            history = CashFlowHistory.make(weekModels, data.categories, today),
            recent = recentActivity(models, data.categories, today),
            insights = models.takeIf { it.isNotEmpty() }?.let { HomeInsights.make(it, data.categories, today) },
            isRefreshing = settingsState.refresh.isRefreshing,
            hasLoaded = settingsState.refresh.hasLoaded,
            isOffline = settingsState.refresh.isOffline,
            refreshError = settingsState.refresh.error?.let(::refreshMessage),
            monthBalance = monthBalanceState.cardFor(monthKey),
            monthBalanceFollowUp = monthBalanceState.followUpFor(monthKey),
            monthBalanceSaving = monthBalanceState.isSaving,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(today = today.value, month = YearMonth.from(today.value), isRefreshing = true),
    )

    init {
        refresh()
        invalidations.events
            .filter { Aggregate.TRANSACTION in it || Aggregate.CATEGORY in it }
            .debounce(250)
            .onEach { refresh() }
            .launchIn(viewModelScope)
        // The new day's ranges need fetching, not just rebinding (Room has no rows for a new month).
        today.drop(1).onEach { refresh() }.launchIn(viewModelScope)
    }

    /** The device's local month, `YYYY-MM` (never UTC's), read from the clock. */
    private fun currentMonthKey(): String = MonthBalanceCopy.monthKey(LocalDate.now(clock))

    /** "Add debt" on a shortfall card finished: ask again, the prompt is answered by the new debt. */
    fun onDebtCreated() {
        viewModelScope.launch { monthBalance.load(currentMonthKey()) }
    }

    /** Answers the month-start card ("Carry over", "No thanks", "Not needed"). Ignored for an expired card. */
    fun answerMonthBalance(choice: MonthBalanceChoice) {
        viewModelScope.launch { monthBalance.answer(currentMonthKey(), choice) }
    }

    /**
     * "Pay it in <Month>": one expense entry for the whole shortfall on the next income still to come this month
     * (else the last day). On success a toast confirms it and Home refreshes so it shows in bills left.
     */
    fun payShortfallThisMonth() {
        val current = state.value
        val data = current.monthBalance?.data ?: return
        val name = MonthBalanceCopy.shortfallPrefill(data)?.name ?: return
        val today = LocalDate.now(clock)
        val date = MonthBalanceCopy.paymentDate(current.breakdown?.expected?.map { it.date }.orEmpty(), today)
        viewModelScope.launch {
            val outcome = monthBalance.pay(currentMonthKey(), date.toString(), name)
            if (outcome is AnswerOutcome.Saved) {
                val amount = DebtAddText.money(kotlin.math.abs(data.balance), current.currency, Locale.getDefault())
                messageChannel.send(MonthBalanceCopy.paidToast(name, amount, date))
                refresh()
            }
        }
    }

    /**
     * "Remove" on the Carried in line: saves `dismissed` for the month, applied on screen at once and rolled back with a
     * message if the save fails. Re-adding is [carryOverAgain].
     */
    fun removeCarriedIn() = changeCarry(MonthBalanceChoice.DISMISSED, optimistic = true)

    /** "Carry over" on the math sheet's "Not included" row (the answer is saved first, then the line appears). */
    fun carryOverAgain() = changeCarry(MonthBalanceChoice.CARRIED, optimistic = false)

    private fun changeCarry(choice: MonthBalanceChoice, optimistic: Boolean) {
        viewModelScope.launch {
            val outcome = monthBalance.answer(currentMonthKey(), choice, optimistic = optimistic, errorOnCard = false)
            if (outcome is AnswerOutcome.Failed) messageChannel.send(outcome.message)
        }
    }

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-shot plain-language messages for a snackbar (a failed Remove / re-add, which have no card to show them on, and the "Pay it in <Month>" confirmation). */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** Refreshes the Room-backed current month; Room remains the source shown while offline. */
    fun refresh() {
        viewModelScope.launch { monthBalance.load(currentMonthKey()) }
        viewModelScope.launch {
            advanceToday()
            refreshState.update { it.copy(isRefreshing = true, isOffline = false, error = null) }
            val current = today.value
            val month = YearMonth.from(current)
            val error = runCatching {
                transactions.refreshRange(month.atDay(1), month.atEndOfMonth())
            }.exceptionOrNull()
            // Early in a month the sparkline's week starts in the previous one; best effort only.
            if (error == null && current.minusDays(6) < month.atDay(1)) {
                runCatching { transactions.refreshRange(current.minusDays(6), month.atDay(1).minusDays(1)) }
            }
            refreshState.value = HomeRefreshState(
                isRefreshing = false,
                hasLoaded = true,
                isOffline = error is ApiError.Network,
                error = error?.takeUnless { it is ApiError.Network },
            )
        }
    }

    private fun advanceToday() {
        val now = LocalDate.now(clock)
        if (now != today.value) today.value = now
    }

    private fun refreshMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String =
        Strings.get(R.string.home_couldn_t_refresh_home_pull)

    private fun TransactionEntity.toModel() = Transaction(
        id = id,
        userId = userId,
        categoryId = categoryId,
        templateId = templateId,
        description = description,
        amount = amount,
        date = date,
    )
}

internal fun recentActivity(transactions: List<Transaction>, categories: List<Category>, today: LocalDate, limit: Int = 3): List<RecentActivity> {
    val byId = categories.associateBy { it.id }
    return transactions
        .map { it to LocalDate.parse(it.date) }
        .filter { (_, date) -> !date.isAfter(today) }
        .sortedWith(compareByDescending<Pair<Transaction, LocalDate>> { it.second }.thenByDescending { it.first.id })
        .take(limit)
        .map { (tx, date) ->
            val category = byId[tx.categoryId]
            RecentActivity(tx.id, tx.description.ifBlank { category?.name ?: Strings.get(R.string.common_transaction) }, category?.name ?: Strings.get(R.string.common_uncategorized), date, tx.amount, category?.isIncome == true, category?.color, category?.icon)
        }
}

/** Milliseconds from the clock's instant until the next local midnight in its zone. */
internal fun Clock.millisUntilNextDayStart(): Long {
    val zone = zone
    val now = ZonedDateTime.ofInstant(instant(), zone)
    return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis()
}
