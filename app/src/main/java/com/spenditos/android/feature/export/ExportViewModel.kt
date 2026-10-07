package com.spenditos.android.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.export.ExportActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ExportRange(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year"),
    CUSTOM("Custom"),
}

data class ExportUiState(
    val range: ExportRange = ExportRange.THIS_MONTH,
    val start: LocalDate,
    val end: LocalDate,
    val isExporting: Boolean = false,
    /** The finished file, until the screen hands it to the share sheet. */
    val file: File? = null,
    val error: String? = null,
) {
    val validationError: String?
        get() = when {
            end.isBefore(start) -> "The end date must be on or after the start date."
            ChronoUnit.DAYS.between(start, end) > MAX_DAYS -> "Choose a range of up to two years."
            else -> null
        }
    val canExport: Boolean get() = !isExporting && validationError == null

    companion object { const val MAX_DAYS = 730L }
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val export: ExportActions,
    private val clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val _state = MutableStateFlow(rangeFor(ExportRange.THIS_MONTH).let { (s, e) -> ExportUiState(start = s, end = e) })
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun selectRange(range: ExportRange) {
        if (range == ExportRange.CUSTOM) {
            _state.update { it.copy(range = range, error = null, file = null) }
            return
        }
        val (start, end) = rangeFor(range)
        _state.update { it.copy(range = range, start = start, end = end, error = null, file = null) }
    }

    fun setStart(date: LocalDate) = _state.update { it.copy(range = ExportRange.CUSTOM, start = date, error = null, file = null) }
    fun setEnd(date: LocalDate) = _state.update { it.copy(range = ExportRange.CUSTOM, end = date, error = null, file = null) }

    fun export() {
        val current = _state.value
        if (!current.canExport) return
        _state.update { it.copy(isExporting = true, error = null, file = null) }
        job = viewModelScope.launch {
            try {
                val file = export.exportCsv(current.start, current.end)
                _state.update { it.copy(isExporting = false, file = file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isExporting = false, error = message(e)) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(isExporting = false) }
    }

    /** The screen opened the share sheet (or the user left): drop the file from memory and disk. */
    fun shared() {
        _state.update { it.copy(file = null) }
        viewModelScope.launch { export.deleteExports() }
    }

    override fun onCleared() {
        // viewModelScope is cancelled here; clear files on a best-effort basis.
        job?.cancel()
    }

    private fun message(error: Throwable): String = when (error) {
        is ApiError.Network -> "You're offline. Check your connection and try again."
        is ApiError.Unauthorized -> "Your session expired. Sign in and try again."
        is ApiError.Server -> when {
            error.status == 400 && error.serverMessage.isNotBlank() && error.serverMessage != "Unknown error" -> error.serverMessage
            error.status >= 500 -> "Export is temporarily unavailable. Try again."
            else -> "Couldn't export your transactions. Try again."
        }
        else -> "Couldn't export your transactions. Try again."
    }

    private fun rangeFor(range: ExportRange): Pair<LocalDate, LocalDate> = when (range) {
        ExportRange.THIS_MONTH -> YearMonth.from(today).let { it.atDay(1) to today }
        ExportRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        ExportRange.LAST_30_DAYS -> today.minusDays(29) to today
        ExportRange.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today
        ExportRange.CUSTOM -> (_state.value.start) to (_state.value.end)
    }
}
