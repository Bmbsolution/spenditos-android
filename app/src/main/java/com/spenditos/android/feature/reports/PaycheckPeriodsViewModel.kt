package com.spenditos.android.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.model.PaycheckPeriod
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

data class PaycheckPeriodsSummary(
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val averageCoverage: BigDecimal?,
    val periodsCount: Int,
)

data class PaycheckPeriodsUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val allPeriods: List<PaycheckPeriod> = emptyList(),
    val hasLoaded: Boolean = false,
    val currency: String = "USD",
    val isRefreshing: Boolean = false,
    val loadStatus: ReportLoadStatus? = null,
) {
    val isLoading: Boolean get() = !hasLoaded && loadStatus == null
    val periods: List<PaycheckPeriod> get() {
        val monthEnd = month.withDayOfMonth(month.lengthOfMonth())
        return allPeriods.filter { it.startDate <= monthEnd && it.endDate >= month }
    }
    val summary: PaycheckPeriodsSummary? get() = summarizePaycheckPeriods(periods)
    val isEmpty: Boolean get() = hasLoaded && periods.isEmpty()
}

internal fun summarizePaycheckPeriods(periods: List<PaycheckPeriod>): PaycheckPeriodsSummary? {
    if (periods.isEmpty()) return null
    val totalIncome = periods.fold(BigDecimal.ZERO) { sum, period -> sum.add(BigDecimal.valueOf(period.income.total)) }
    val totalExpenses = periods.fold(BigDecimal.ZERO) { sum, period -> sum.add(BigDecimal.valueOf(period.expenses.total)) }
    val coverageSum = periods.fold(BigDecimal.ZERO) { sum, period ->
        val income = BigDecimal.valueOf(period.income.total)
        if (income.signum() <= 0) sum else sum.add(
            BigDecimal.valueOf(period.expenses.total).multiply(BigDecimal.valueOf(100)).divide(income, MathContext.DECIMAL64),
        )
    }
    val average = coverageSum.divide(BigDecimal.valueOf(periods.size.toLong()), MathContext.DECIMAL64)
    return PaycheckPeriodsSummary(totalIncome, totalExpenses, average, periods.size)
}

@HiltViewModel
class PaycheckPeriodsViewModel @Inject constructor(
    private val reports: ReportsDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(PaycheckPeriodsUiState())
    val state: StateFlow<PaycheckPeriodsUiState> = _state.asStateFlow()

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
        _state.update { it.copy(month = month, allPeriods = emptyList(), hasLoaded = false, loadStatus = null) }
        fetch(month)
    }

    private fun fetch(month: LocalDate) {
        val start = month.minusMonths(1).withDayOfMonth(1)
        val endMonth = month.plusMonths(1).withDayOfMonth(1)
        val end = endMonth.withDayOfMonth(endMonth.lengthOfMonth())
        _state.update { it.copy(isRefreshing = true, loadStatus = null) }
        viewModelScope.launch {
            try {
                val response = reports.getPaycheckPeriods(start, end)
                _state.update { it.copy(allPeriods = response.periods, hasLoaded = true, isRefreshing = false, loadStatus = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val status = reportLoadStatus(error)
                _state.update {
                    it.copy(
                        allPeriods = if (status == ReportLoadStatus.PRO_REQUIRED) emptyList() else it.allPeriods,
                        hasLoaded = if (status == ReportLoadStatus.PRO_REQUIRED) false else it.hasLoaded,
                        isRefreshing = false,
                        loadStatus = status,
                    )
                }
            }
        }
    }

    fun incomeAmount(period: PaycheckPeriod): BigDecimal = BigDecimal.valueOf(period.income.total)

    fun expenseAmount(period: PaycheckPeriod): BigDecimal = BigDecimal.valueOf(period.expenses.total)

    fun remainingAmount(period: PaycheckPeriod): BigDecimal = incomeAmount(period).subtract(expenseAmount(period))

    fun isCovered(period: PaycheckPeriod): Boolean = remainingAmount(period) >= BigDecimal("-0.01")

    fun expenseRatio(period: PaycheckPeriod): Float {
        val income = incomeAmount(period)
        if (income.signum() <= 0) return 0f
        return expenseAmount(period).divide(income, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
    }
}
