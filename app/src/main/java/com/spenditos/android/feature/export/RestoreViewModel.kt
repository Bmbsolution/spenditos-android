package com.spenditos.android.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.R
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.restore.RestoreActions
import com.spenditos.android.data.restore.RestoreErrorCode
import com.spenditos.android.data.restore.RestoreExpiredException
import com.spenditos.android.data.restore.RestoreFailedException
import com.spenditos.android.data.restore.RestoreFileTooLargeException
import com.spenditos.android.data.restore.RestoreFileUnreadableException
import com.spenditos.android.data.restore.RestoreProgress
import com.spenditos.android.data.restore.RestoreSummary
import com.spenditos.android.data.restore.RestoreUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RestoreUiPhase {
    Idle,

    /** A file is picked; the screen asks the user to confirm. */
    Confirming,
    Uploading,
    Checking,
    Restoring,
    Done,
    Failed,
}

data class RestoreUiState(
    val phase: RestoreUiPhase = RestoreUiPhase.Idle,
    val pickedFile: Uri? = null,
    val uploadedParts: Int = 0,
    val totalParts: Int = 0,
    val progress: RestoreProgress? = null,
    val summary: RestoreSummary? = null,
    val error: String? = null,
) {
    val isBusy: Boolean
        get() = phase == RestoreUiPhase.Uploading || phase == RestoreUiPhase.Checking || phase == RestoreUiPhase.Restoring
}

/** Settings > Download my data > Restore from a file: sends a "Download my data" file back (`/user/restore`). */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val restore: RestoreActions,
) : ViewModel() {
    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** The document picker answered; a null [file] means the user backed out. */
    fun filePicked(file: Uri?) {
        if (_state.value.isBusy) return
        _state.value = if (file == null) RestoreUiState() else RestoreUiState(phase = RestoreUiPhase.Confirming, pickedFile = file)
    }

    fun cancelConfirmation() {
        if (_state.value.phase == RestoreUiPhase.Confirming) _state.value = RestoreUiState()
    }

    fun confirm() {
        val file = _state.value.pickedFile ?: return
        if (_state.value.phase != RestoreUiPhase.Confirming) return
        _state.value = RestoreUiState(phase = RestoreUiPhase.Uploading, pickedFile = file)
        job = viewModelScope.launch {
            try {
                restore.restore(file).collect(::apply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = RestoreUiState(phase = RestoreUiPhase.Failed, error = message(e))
            }
        }
    }

    /** Stops sending parts. A restore the server already started finishes on its own. */
    fun cancel() {
        if (_state.value.phase != RestoreUiPhase.Uploading) return
        job?.cancel()
        _state.value = RestoreUiState()
    }

    fun dismissError() = _state.update { if (it.phase == RestoreUiPhase.Failed) RestoreUiState() else it }

    private suspend fun apply(update: RestoreUpdate) {
        when (update) {
            is RestoreUpdate.Uploading -> _state.update {
                it.copy(phase = RestoreUiPhase.Uploading, uploadedParts = update.done, totalParts = update.total)
            }
            RestoreUpdate.Checking -> _state.update { it.copy(phase = RestoreUiPhase.Checking) }
            is RestoreUpdate.Restoring -> _state.update { it.copy(phase = RestoreUiPhase.Restoring, progress = update.progress) }
            is RestoreUpdate.Finished -> {
                restore.refreshAfterRestore()
                _state.value = RestoreUiState(phase = RestoreUiPhase.Done, summary = update.summary ?: RestoreSummary())
            }
        }
    }

    private fun message(error: Throwable): String = when (error) {
        is RestoreFailedException -> when (error.code) {
            RestoreErrorCode.CHECKSUM_MISMATCH -> Strings.get(R.string.restore_error_checksum)
            RestoreErrorCode.INVALID_BACKUP_FILE -> Strings.get(R.string.restore_error_invalid)
            RestoreErrorCode.UNSUPPORTED_BACKUP_VERSION -> Strings.get(R.string.restore_error_unsupported)
            RestoreErrorCode.ACCOUNT_NOT_EMPTY -> Strings.get(R.string.restore_error_not_empty)
            RestoreErrorCode.RESTORE_FAILED -> Strings.get(R.string.restore_error_failed)
            RestoreErrorCode.UNKNOWN -> Strings.get(R.string.restore_error_generic)
        }
        is RestoreExpiredException -> Strings.get(R.string.restore_error_expired)
        is RestoreFileTooLargeException -> Strings.get(R.string.restore_error_too_large)
        is RestoreFileUnreadableException -> Strings.get(R.string.restore_error_unreadable)
        is ApiError.Network -> Strings.get(R.string.common_you_re_offline_check_your)
        is ApiError.Unauthorized -> Strings.get(R.string.export_your_session_expired_sign_in)
        is SessionChangedException -> Strings.get(R.string.export_the_signed_in_account_changed_2)
        // `{error: <code>, message}`: the handlers' codes are the part to match, never the English message.
        is ApiError.Server -> when (error.serverMessage) {
            "BackupTooLarge" -> Strings.get(R.string.restore_error_too_large)
            "TooManyRestores" -> Strings.get(R.string.restore_error_too_many)
            "RestoreInProgress" -> Strings.get(R.string.restore_error_in_progress)
            "RestoreExpired" -> Strings.get(R.string.restore_error_expired)
            else -> Strings.get(R.string.restore_error_generic)
        }
        else -> Strings.get(R.string.restore_error_generic)
    }
}
