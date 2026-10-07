package com.spenditos.android.feature.home

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
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.feature.home.insights.HomeInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate,
    val month: YearMonth,
    val currency: String = "USD",
    val transactionCount: Int = 0,
    val status: CashFlowStatus? = null,
    val breakdown: CashFlowBreakdown? = null,
    val insights: HomeInsights? = null,
    val history: List<DayFlow> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val isOffline: Boolean = false,
    val refreshError: String? = null,
) {
    val isLoading: Boolean get() = isRefreshing && !hasLoaded && transactionCount == 0
    val isEmpty: Boolean get() = hasLoaded && transactionCount == 0
    val showingSavedData: Boolean get() = isOffline
    val upcomingBills: List<CashFlowBreakdown.Line> get() = breakdown?.billsLeft?.take(5).orEmpty()
}

private data class HomeData(val transactions: List<TransactionEntity>, val week: List<TransactionEntity>, val categories: List<Category>)
private data class HomeSettings(val currency: String, val refresh: HomeRefreshState)
private data class HomeRefreshState(
    val isRefreshing: Boolean = true,
    val hasLoaded: Boolean = false,
    val isOffline: Boolean = false,
    val error: Throwable? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val month = YearMonth.from(today)
    private val refreshState = MutableStateFlow(HomeRefreshState())
    private val monthRows = transactions.observeRange(month.atDay(1), month.atEndOfMonth())
    // The last seven days can start in the previous month; the sparkline reads them from the same cache.
    private val weekRows = transactions.observeRange(today.minusDays(6), today)

    val state: StateFlow<HomeUiState> = combine(
        combine(monthRows, weekRows, categories.observe()) { rows, week, categoryRows -> HomeData(rows, week, categoryRows) },
        combine(settings.currency(), refreshState) { currency, refresh -> HomeSettings(currency, refresh) },
    ) { data, settingsState ->
        val models = data.transactions.map { it.toModel() }
        val weekModels = data.week.map { it.toModel() }
        val status = CashFlowStatus.make(models, data.categories, today)
        HomeUiState(
            today = today,
            month = month,
            currency = settingsState.currency,
            transactionCount = models.size,
            status = status,
            breakdown = CashFlowBreakdown.make(models, data.categories, today),
            history = CashFlowHistory.make(weekModels, data.categories, today),
            insights = models.takeIf { it.isNotEmpty() }?.let { HomeInsights.make(it, data.categories, today) },
            isRefreshing = settingsState.refresh.isRefreshing,
            hasLoaded = settingsState.refresh.hasLoaded,
            isOffline = settingsState.refresh.isOffline,
            refreshError = settingsState.refresh.error?.let(::refreshMessage),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(today = today, month = month, isRefreshing = true),
    )

    init {
        refresh()
        invalidations.events
            .filter { Aggregate.TRANSACTION in it || Aggregate.CATEGORY in it }
            .debounce(250)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /** Refreshes the Room-backed current month; Room remains the source shown while offline. */
    fun refresh() {
        viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = true, isOffline = false, error = null) }
            val error = runCatching {
                transactions.refreshRange(month.atDay(1), month.atEndOfMonth())
            }.exceptionOrNull()
            // Early in a month the sparkline's week starts in the previous one; best effort only.
            if (error == null && today.minusDays(6) < month.atDay(1)) {
                runCatching { transactions.refreshRange(today.minusDays(6), month.atDay(1).minusDays(1)) }
            }
            refreshState.value = HomeRefreshState(
                isRefreshing = false,
                hasLoaded = true,
                isOffline = error is ApiError.Network,
                error = error?.takeUnless { it is ApiError.Network },
            )
        }
    }

    private fun refreshMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String =
        "Couldn't refresh Home. Pull down to try again."

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
