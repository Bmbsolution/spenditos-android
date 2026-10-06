package com.spenditos.android.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.data.reports.ReportsDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class MonthComparisonUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val currentAggregates: List<DailyAggregate> = emptyList(),
    val previousAggregates: List<DailyAggregate> = emptyList(),
    val hasLoaded: Boolean = false,
    val currency: String = "USD",
    val isRefreshing: Boolean = false,
    val loadStatus: ReportLoadStatus? = null,
) {
    val isLoading: Boolean get() = !hasLoaded && loadStatus == null
    val isEmpty: Boolean get() = hasLoaded && currentAggregates.none { it.transactionCount > 0 } && previousAggregates.none { it.transactionCount > 0 }
    val currentTotalIncome: BigDecimal get() = sumDaily(currentAggregates) { it.income }
    val previousTotalIncome: BigDecimal get() = sumDaily(previousAggregates) { it.income }
    val currentTotalExpenses: BigDecimal get() = sumDaily(currentAggregates) { it.expenses }
    val previousTotalExpenses: BigDecimal get() = sumDaily(previousAggregates) { it.expenses }
    val currentNet: BigDecimal get() = currentTotalIncome.subtract(currentTotalExpenses)
    val previousNet: BigDecimal get() = previousTotalIncome.subtract(previousTotalExpenses)
}

data class WeeklyComparison(val week: Int, val current: BigDecimal, val previous: BigDecimal)

internal fun sumDaily(rows: List<DailyAggregate>, amount: (DailyAggregate) -> Double): BigDecimal =
    rows.fold(BigDecimal.ZERO) { total, aggregate -> total.add(BigDecimal.valueOf(amount(aggregate))) }

internal fun weeklyTotals(
    current: List<DailyAggregate>,
    previous: List<DailyAggregate>,
    amount: (DailyAggregate) -> Double,
    locale: Locale = Locale.getDefault(),
): List<WeeklyComparison> {
    val weekOfMonth = WeekFields.of(locale).weekOfMonth()
    fun group(rows: List<DailyAggregate>): Map<Int, BigDecimal> {
        val result = sortedMapOf<Int, BigDecimal>()
        rows.forEach { row ->
            val week = maxOf(1, row.date.get(weekOfMonth))
            result[week] = (result[week] ?: BigDecimal.ZERO).add(BigDecimal.valueOf(amount(row)))
        }
        return result
    }
    val currentByWeek = group(current)
    val previousByWeek = group(previous)
    val maxWeek = maxOf(currentByWeek.keys.maxOrNull() ?: 0, previousByWeek.keys.maxOrNull() ?: 0)
    return (1..maxWeek).map { week ->
        WeeklyComparison(week, currentByWeek[week] ?: BigDecimal.ZERO, previousByWeek[week] ?: BigDecimal.ZERO)
    }
}

@HiltViewModel
class MonthComparisonViewModel @Inject constructor(
    private val reports: ReportsDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(MonthComparisonUiState())
    val state: StateFlow<MonthComparisonUiState> = _state.asStateFlow()

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
        _state.update {
            it.copy(month = month, currentAggregates = emptyList(), previousAggregates = emptyList(), hasLoaded = false, loadStatus = null)
        }
        fetch(month)
    }

    private fun fetch(month: LocalDate) {
        val previousMonth = month.minusMonths(1).withDayOfMonth(1)
        _state.update { it.copy(isRefreshing = true, loadStatus = null) }
        viewModelScope.launch {
            try {
                val (current, previous) = coroutineScope {
                    val currentTask = async { reports.getDailyAggregates(month, month.withDayOfMonth(month.lengthOfMonth())) }
                    val previousTask = async { reports.getDailyAggregates(previousMonth, previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())) }
                    currentTask.await() to previousTask.await()
                }
                _state.update {
                    it.copy(currentAggregates = current, previousAggregates = previous, hasLoaded = true, isRefreshing = false, loadStatus = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isRefreshing = false, loadStatus = reportLoadStatus(error)) }
            }
        }
    }

    fun weeklyExpenses(): List<WeeklyComparison> = weeklyTotals(
        _state.value.currentAggregates,
        _state.value.previousAggregates,
        DailyAggregate::expenses,
    )

    fun weeklyIncome(): List<WeeklyComparison> = weeklyTotals(
        _state.value.currentAggregates,
        _state.value.previousAggregates,
        DailyAggregate::income,
    )

    fun percentageChange(current: BigDecimal, previous: BigDecimal): String {
        if (previous.signum() == 0) return if (current.signum() > 0) "+100%" else "0%"
        val change = current.subtract(previous).multiply(BigDecimal.valueOf(100))
            .divide(previous, 0, RoundingMode.HALF_UP).toInt()
        return if (change >= 0) "+${change}%" else "${change}%"
    }

    fun changeIsPositive(current: BigDecimal, previous: BigDecimal, isExpense: Boolean): Boolean =
        if (isExpense) current <= previous else current >= previous

    fun maxWeeklyValue(values: List<WeeklyComparison>): BigDecimal =
        values.maxOfOrNull { maxOf(it.current, it.previous) } ?: BigDecimal.ONE
}
