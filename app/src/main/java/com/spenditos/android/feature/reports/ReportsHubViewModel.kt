package com.spenditos.android.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.CategorySummary
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.reports.ReportsDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

enum class ReportLoadStatus { OFFLINE, ERROR, PRO_REQUIRED }

internal fun reportLoadStatus(error: Throwable): ReportLoadStatus = when (error) {
    is ApiError.Network -> ReportLoadStatus.OFFLINE
    is ApiError.Server -> if (error.status == 403 && error.serverMessage == "ProRequired") {
        ReportLoadStatus.PRO_REQUIRED
    } else {
        ReportLoadStatus.ERROR
    }
    else -> ReportLoadStatus.ERROR
}

internal fun reportStatusMessage(status: ReportLoadStatus): String = when (status) {
    ReportLoadStatus.OFFLINE -> "Offline. Connect to load reports."
    ReportLoadStatus.ERROR -> "Reports couldn't load. Try again."
    ReportLoadStatus.PRO_REQUIRED -> "Pay periods are a Pro feature."
}

data class ReportsHubUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val dashboard: DashboardData? = null,
    val currency: String = "USD",
    val isRefreshing: Boolean = false,
    val loadStatus: ReportLoadStatus? = null,
    /** Active income and bills, excluding legacy "variable" estimates (iOS activeRecurringTemplatesCount). */
    val activeRecurringCount: Int = 0,
) {
    val isLoading: Boolean get() = dashboard == null && loadStatus == null
    val isEmpty: Boolean get() = dashboard != null && dashboard.totalTransactionCount == 0
}

data class ReportCategorySlice(val id: String, val name: String, val amount: BigDecimal, val isEverythingElse: Boolean = false)

internal fun categorySlices(categories: List<CategorySummary>): List<ReportCategorySlice> {
    val expenses = categories.asSequence()
        .filter { !it.isIncome && it.totalAmount > 0.0 }
        .sortedByDescending { BigDecimal.valueOf(it.totalAmount) }
        .toList()
    val top = expenses.take(3).map {
        ReportCategorySlice(it.categoryId, it.categoryName, BigDecimal.valueOf(it.totalAmount))
    }
    val rest = expenses.drop(3).fold(BigDecimal.ZERO) { sum, category ->
        sum.add(BigDecimal.valueOf(category.totalAmount))
    }
    return if (rest.signum() > 0) top + ReportCategorySlice("everything-else", "Everything else", rest, true) else top
}

internal fun activeRecurringCount(templates: List<RecurringTemplate>): Int =
    templates.count { it.isActive && !it.type.equals("variable", ignoreCase = true) }

@HiltViewModel
class ReportsHubViewModel @Inject constructor(
    private val reports: ReportsDataSource,
    private val recurring: RecurringActions? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsHubUiState())
    val state: StateFlow<ReportsHubUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reports.currency().collect { currency -> _state.update { it.copy(currency = currency) } }
        }
        recurring?.let { source ->
            viewModelScope.launch {
                source.observe().collect { templates ->
                    _state.update { state -> state.copy(activeRecurringCount = activeRecurringCount(templates)) }
                }
            }
        }
    }

    fun load() = refresh()

    fun refresh() {
        if (_state.value.isRefreshing) return
        val month = _state.value.month
        _state.update { it.copy(isRefreshing = true, loadStatus = null) }
        // Best effort and in parallel: it only feeds the takeaway count, so it must not delay the report.
        recurring?.let { source ->
            viewModelScope.launch {
                try {
                    source.refresh()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The count keeps its last value.
                }
            }
        }
        viewModelScope.launch {
            try {
                val result = reports.getDashboard(month, month.withDayOfMonth(month.lengthOfMonth()))
                _state.update { it.copy(dashboard = result, isRefreshing = false, loadStatus = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(isRefreshing = false, loadStatus = reportLoadStatus(error)) }
            }
        }
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        if (_state.value.isRefreshing) return
        val month = _state.value.month.plusMonths(delta).withDayOfMonth(1)
        _state.update { it.copy(month = month, dashboard = null, loadStatus = null) }
        refresh()
    }
}
