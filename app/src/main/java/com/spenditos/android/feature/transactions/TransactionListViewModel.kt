package com.spenditos.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class TransactionRowUi(
    val id: String,
    val title: String,
    val categoryName: String,
    val categoryColor: String?,
    val amount: Double,
    val isIncome: Boolean,
    val date: LocalDate,
    val syncStatus: SyncStatus,
)

data class TransactionListUiState(
    val month: YearMonth = YearMonth.now(),
    val currency: String = "USD",
    val days: List<Pair<LocalDate, List<TransactionRowUi>>> = emptyList(),
    val moneyIn: BigDecimal = BigDecimal.ZERO,
    val moneyOut: BigDecimal = BigDecimal.ZERO,
    val waitingCount: Int = 0,
    val failedCount: Int = 0,
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
) {
    val isEmpty: Boolean get() = days.isEmpty()
}

private data class RefreshState(val isRefreshing: Boolean = false, val error: String? = null)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    settings: UserSettingsRepository,
    connectivity: ConnectivityStatus,
    invalidation: FinanceInvalidationBus,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val refresh = MutableStateFlow(RefreshState())

    private val rows = month.flatMapLatest { m -> transactions.observeRange(m.atDay(1), m.atEndOfMonth()) }

    val state: StateFlow<TransactionListUiState> = combine(
        combine(month, rows, categories.observe(), settings.currency()) { m, t, c, cur -> Quad(m, t, c, cur) },
        transactions.observeQueue(),
        connectivity.isOnline,
        refresh,
    ) { (m, txns, cats, currency), queue, online, refreshState ->
        val byId = cats.associateBy(Category::id)
        val ui = txns.map { it.toRow(byId[it.categoryId]) }
        TransactionListUiState(
            month = m,
            currency = currency,
            days = ui.groupBy { it.date }.toSortedMap(compareByDescending { it }).toList(),
            moneyIn = ui.filter { it.isIncome }.sumOf { BigDecimal.valueOf(it.amount) },
            moneyOut = ui.filterNot { it.isIncome }.sumOf { BigDecimal.valueOf(it.amount) },
            waitingCount = queue.count { it.status != MutationStatus.FAILED },
            failedCount = queue.count { it.status == MutationStatus.FAILED },
            isOnline = online,
            isRefreshing = refreshState.isRefreshing,
            loadError = refreshState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListUiState())

    init {
        refresh(includeCategories = true)
        invalidation.events
            .filter { Aggregate.TRANSACTION in it || Aggregate.CATEGORY in it }
            .debounce(250)
            .onEach { refresh(includeCategories = Aggregate.CATEGORY in it) }
            .launchIn(viewModelScope)
        connectivity.isOnline.filter { it }.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun previousMonth() = changeMonth(month.value.minusMonths(1))

    fun nextMonth() = changeMonth(month.value.plusMonths(1))

    private fun changeMonth(target: YearMonth) {
        month.value = target
        refresh()
    }

    fun refresh(includeCategories: Boolean = false) {
        val m = month.value
        viewModelScope.launch {
            refresh.update { it.copy(isRefreshing = true) }
            val error = runCatching {
                if (includeCategories) categories.refresh()
                transactions.refreshRange(m.atDay(1), m.atEndOfMonth())
            }.exceptionOrNull()
            refresh.value = RefreshState(isRefreshing = false, error = error?.let(::messageFor))
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is ApiError.Network -> "You're offline. Showing what's saved on this device."
        else -> "Couldn't refresh transactions. Pull down to try again."
    }

    private fun TransactionEntity.toRow(category: Category?) = TransactionRowUi(
        id = id,
        title = description.ifBlank { category?.name ?: "Transaction" },
        categoryName = category?.name ?: "Uncategorized",
        categoryColor = category?.color,
        amount = amount,
        isIncome = category?.isIncome == true,
        date = LocalDate.parse(date),
        syncStatus = syncStatus,
    )
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
