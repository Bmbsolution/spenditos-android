package com.spenditos.android.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.export.ExportActions
import com.spenditos.android.data.export.ExportSaveFailedException
import com.spenditos.android.data.export.FullCopy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FullCopyPhase {
    Idle,

    /** Downloading `GET /user/export` into a private temporary file. */
    Preparing,

    /** The file is ready; the screen opens the system "save as" picker with [FullCopyUiState.suggestedName]. */
    ChooseDestination,

    /** Streaming the file into the document the user picked. */
    Saving,
    Saved,
    Failed,
}

data class FullCopyUiState(
    val phase: FullCopyPhase = FullCopyPhase.Idle,
    val suggestedName: String? = null,
    /** True once the screen launched the picker, so a rotation does not open a second one. */
    val pickerRequested: Boolean = false,
    val error: String? = null,
) {
    val isBusy: Boolean get() = phase == FullCopyPhase.Preparing || phase == FullCopyPhase.Saving || phase == FullCopyPhase.ChooseDestination
}

/** Settings > Download my data > Full copy: one JSON file with the whole account (`GET /user/export`). */
@HiltViewModel
class FullCopyViewModel @Inject constructor(
    private val export: ExportActions,
) : ViewModel() {
    private val _state = MutableStateFlow(FullCopyUiState())
    val state: StateFlow<FullCopyUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var pending: FullCopy? = null

    fun download() {
        if (_state.value.isBusy) return
        _state.value = FullCopyUiState(phase = FullCopyPhase.Preparing)
        job = viewModelScope.launch {
            try {
                val copy = export.prepareFullCopy()
                pending = copy
                _state.value = FullCopyUiState(phase = FullCopyPhase.ChooseDestination, suggestedName = copy.suggestedName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    fun pickerRequested() = _state.update { it.copy(pickerRequested = true) }

    /** The picker answered; a null [target] means the user backed out. */
    fun destinationPicked(target: Uri?) {
        val copy = pending ?: return
        if (target == null) {
            discard()
            _state.value = FullCopyUiState()
            return
        }
        _state.update { it.copy(phase = FullCopyPhase.Saving) }
        job = viewModelScope.launch {
            try {
                export.saveFullCopy(copy, target)
                pending = null
                _state.value = FullCopyUiState(phase = FullCopyPhase.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                pending = null
                fail(e)
            }
        }
    }

    /** Stops a download that is still preparing. */
    fun cancel() {
        if (_state.value.phase != FullCopyPhase.Preparing) return
        job?.cancel()
        _state.value = FullCopyUiState()
    }

    fun dismissError() = _state.update { if (it.phase == FullCopyPhase.Failed) FullCopyUiState() else it }

    override fun onCleared() {
        job?.cancel()
        discard()
    }

    private fun discard() {
        pending?.let(export::discardFullCopy)
        pending = null
    }

    private fun fail(error: Throwable) {
        _state.value = FullCopyUiState(phase = FullCopyPhase.Failed, error = message(error))
    }

    private fun message(error: Throwable): String = when (error) {
        is ApiError.Network -> "You're offline. Check your connection and try again."
        is ApiError.Unauthorized -> "Your session expired. Sign in and try again."
        is SessionChangedException -> "The signed-in account changed, so nothing was saved. Try again."
        is ExportSaveFailedException -> "Couldn't save the file there. Try another location."
        // `{error: "ExportFailed", message: "Could not build your data file. Please try again."}`
        is ApiError.Server -> error.serverMessage.takeIf { it.isNotBlank() && it != "Unknown error" }
            ?: "Couldn't download your data. Try again."
        else -> "Couldn't download your data. Try again."
    }
}
