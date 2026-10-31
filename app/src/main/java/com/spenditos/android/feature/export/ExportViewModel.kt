package com.spenditos.android.feature.export

import com.spenditos.android.core.network.unknownServerMessage
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.export.ExportActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/** iOS `SpreadsheetRange.Preset`. */
enum class ExportRange(@androidx.annotation.StringRes val labelRes: Int) {
    THIS_MONTH(R.string.common_this_month),
    THIS_YEAR(R.string.export_this_year),
    LAST_YEAR(R.string.export_last_year),
    CUSTOM(R.string.common_custom),
    ;

    val label: String get() = Strings.get(labelRes)
}

data class ExportUiState(
    val range: ExportRange = ExportRange.THIS_YEAR,
    val start: LocalDate,
    val end: LocalDate,
    val isExporting: Boolean = false,
    /** The finished spreadsheet, until the screen hands it to the share sheet. */
    val file: File? = null,
    val error: String? = null,
) {
    val validationError: String?
        get() = when {
            end.isBefore(start) -> Strings.get(R.string.export_the_end_date_must_be)
            ChronoUnit.DAYS.between(start, end) > MAX_DAYS -> Strings.get(R.string.export_choose_a_range_of_up)
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
    private val _state = MutableStateFlow(rangeFor(ExportRange.THIS_YEAR).let { (s, e) -> ExportUiState(start = s, end = e) })
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

    fun dismissError() = _state.update { it.copy(error = null) }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(isExporting = false) }
    }

    /**
     * The screen opened the share sheet: forget the file so it is not shared twice. It stays on disk
     * on purpose: the receiving app reads it through the FileProvider URI after this returns, and
     * deleting now races with that read. It is removed when this screen is left ([onCleared]), when
     * the next export starts, and at app start (ExportRepository init).
     */
    fun shared() {
        _state.update { it.copy(file = null) }
    }

    /** Outlives [viewModelScope], which is cancelled before [onCleared] runs. */
    internal var cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCleared() {
        job?.cancel()
        // Financial data must not linger in cache: the screen is gone, so the exported file is too.
        cleanupScope.launch { export.deleteExports() }
    }

    private fun message(error: Throwable): String = when (error) {
        is ApiError.Network -> Strings.get(R.string.common_you_re_offline_check_your)
        is ApiError.Unauthorized -> Strings.get(R.string.export_your_session_expired_sign_in)
        is SessionChangedException -> Strings.get(R.string.export_the_signed_in_account_changed)
        is ApiError.Server -> when {
            error.status == 400 && error.serverMessage.isNotBlank() && error.serverMessage != unknownServerMessage() -> error.serverMessage
            error.status >= 500 -> Strings.get(R.string.export_downloads_are_temporarily_unavailable_try)
            else -> Strings.get(R.string.export_couldn_t_download_your_transactions)
        }
        else -> Strings.get(R.string.export_couldn_t_download_your_transactions)
    }

    private fun rangeFor(range: ExportRange): Pair<LocalDate, LocalDate> = when (range) {
        ExportRange.THIS_MONTH -> YearMonth.from(today).let { it.atDay(1) to today }
        ExportRange.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today
        ExportRange.LAST_YEAR -> LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
        ExportRange.CUSTOM -> (_state.value.start) to (_state.value.end)
    }
}
