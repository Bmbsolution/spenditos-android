package com.spenditos.android.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.data.reports.ReportsDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import javax.inject.Inject

data class DailyAggregatesUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val aggregates: List<DailyAggregate> = emptyList(),
    val hasLoaded: Boolean = false,
    val currency: String = "USD",
    val isRefreshing: Boolean = false,
    val loadStatus: ReportLoadStatus? = null,
) {
    val isLoading: Boolean get() = !hasLoaded && loadStatus == null
    val isEmpty: Boolean get() = hasLoaded && aggregates.none { it.transactionCount > 0 }
    val summary: DailyAggregatesSummary get() = summarizeDailyAggregates(aggregates)
}

data class DailyAggregatesSummary(
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val averageDailyExpense: BigDecimal?,
    val highestSpendingDay: LocalDate?,
    val lowestSpendingDay: LocalDate?,
)

internal fun summarizeDailyAggregates(rows: List<DailyAggregate>): DailyAggregatesSummary {
    val income = rows.fold(BigDecimal.ZERO) { sum, row -> sum.add(BigDecimal.valueOf(row.income)) }
    val expenses = rows.fold(BigDecimal.ZERO) { sum, row -> sum.add(BigDecimal.valueOf(row.expenses)) }
    val average = if (rows.isEmpty()) null else expenses.divide(BigDecimal.valueOf(rows.size.toLong()), MathContext.DECIMAL64)
    val highest = rows.maxByOrNull { BigDecimal.valueOf(it.expenses) }?.date
    val lowest = rows.minByOrNull { BigDecimal.valueOf(it.expenses) }?.date
    return DailyAggregatesSummary(income, expenses, average, highest, lowest)
}

@HiltViewModel
class DailyAggregatesViewModel @Inject constructor(
    private val reports: ReportsDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(DailyAggregatesUiState())
    val state: StateFlow<DailyAggregatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reports.currency().collect { currency -> _state.update { it.copy(currency = currency) } }
        }
    }

    fun load() = refresh()

    fun refresh() {
        if (_state.value.isRefreshing) return
        fetch(_state.value.month)
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        if (_state.value.isRefreshing) return
        val month = _state.value.month.plusMonths(delta).withDayOfMonth(1)
        _state.update { it.copy(month = month, aggregates = emptyList(), hasLoaded = false, loadStatus = null) }
        fetch(month)
    }

    private fun fetch(month: LocalDate) {
        _state.update { it.copy(isRefreshing = true, loadStatus = null) }
        viewModelScope.launch {
            try {
                val rows = reports.getDailyAggregates(month, month.withDayOfMonth(month.lengthOfMonth()))
                _state.update { it.copy(aggregates = rows, hasLoaded = true, isRefreshing = false, loadStatus = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isRefreshing = false, loadStatus = reportLoadStatus(error)) }
            }
        }
    }

    fun movingAverageAt(index: Int): BigDecimal {
        val rows = _state.value.aggregates
        if (index !in rows.indices) return BigDecimal.ZERO
        val start = maxOf(0, index - 6)
        val slice = rows.subList(start, index + 1)
        val sum = slice.fold(BigDecimal.ZERO) { total, aggregate -> total.add(BigDecimal.valueOf(aggregate.expenses)) }
        return sum.divide(BigDecimal.valueOf(slice.size.toLong()), MathContext.DECIMAL64)
    }

}
