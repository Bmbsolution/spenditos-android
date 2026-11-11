package com.spenditos.android.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.R
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.restore.AccountDataCounts
import com.spenditos.android.data.restore.RestoreActions
import com.spenditos.android.data.restore.RestoreMode
import com.spenditos.android.data.restore.RestoreSession
import com.spenditos.android.data.restore.RestoreUndoExpiredException
import com.spenditos.android.data.restore.RestoreUndoFailedException
import com.spenditos.android.data.restore.RestoreUndoStatus
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

    /** A file is picked; the account's data summary decides between the two confirmations. */
    CheckingAccount,

    /** Fresh account: the screen asks the user to confirm. */
    Confirming,

    /** Account with data: the screen lists what is erased and asks for the word [RestoreViewModel.REPLACE_WORD]. */
    ConfirmingReplace,
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
    /** What a replace would erase (set while [RestoreUiPhase.ConfirmingReplace]). */
    val counts: AccountDataCounts? = null,
    val replaceText: String = "",
    /** The newest restore that can still be undone, if any. */
    val undoSession: RestoreSession? = null,
    val undoPhase: UndoPhase = UndoPhase.Idle,
    val undoError: String? = null,
    /** True after an Undo finished, until the next restore. */
    val undoDone: Boolean = false,
) {
    /** The destructive button is enabled only for exactly the word, nothing else. */
    val canReplace: Boolean get() = replaceText == RestoreViewModel.REPLACE_WORD

    val isBusy: Boolean
        get() = phase == RestoreUiPhase.CheckingAccount || phase == RestoreUiPhase.Uploading ||
            phase == RestoreUiPhase.Checking || phase == RestoreUiPhase.Restoring

    val undoBusy: Boolean get() = undoPhase == UndoPhase.Running
}

enum class UndoPhase { Idle, Confirming, Running }

/**
 * Restore from a file (Settings > Download my data, and the first setup screen) plus Undo of the last replace.
 * After a file is picked the account's data summary decides: an empty account gets the plain confirmation (mode
 * fresh); an account with data gets the Replace confirmation (mode replace, typed word required).
 */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val restore: RestoreActions,
) : ViewModel() {
    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var undoJob: Job? = null
    private var freshOnly = false

    /** Back to the idle restore state, keeping what is known about Undo. */
    private fun reset(transform: RestoreUiState.() -> RestoreUiState = { this }) {
        _state.update { RestoreUiState(undoSession = it.undoSession, undoPhase = it.undoPhase, undoDone = it.undoDone).transform() }
    }

    /**
     * The document picker answered; a null [file] means the user backed out. With [freshOnly] (the setup screen) the
     * account is known to be new: no summary is fetched and the mode is always fresh.
     */
    fun filePicked(file: Uri?, freshOnly: Boolean = false) {
        if (_state.value.isBusy || _state.value.undoBusy) return
        this.freshOnly = freshOnly
        if (file == null) return reset()
        if (freshOnly) return reset { copy(phase = RestoreUiPhase.Confirming, pickedFile = file) }
        reset { copy(phase = RestoreUiPhase.CheckingAccount, pickedFile = file) }
        job = viewModelScope.launch {
            try {
                val counts = restore.dataSummary()
                _state.update {
                    if (counts.isEmptyForFreshRestore) it.copy(phase = RestoreUiPhase.Confirming, counts = counts)
                    else it.copy(phase = RestoreUiPhase.ConfirmingReplace, counts = counts, replaceText = "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reset { copy(phase = RestoreUiPhase.Failed, error = message(e)) }
            }
        }
    }

    fun onReplaceTextChange(text: String) {
        if (_state.value.phase == RestoreUiPhase.ConfirmingReplace) _state.update { it.copy(replaceText = text) }
    }

    fun cancelConfirmation() {
        val phase = _state.value.phase
        if (phase == RestoreUiPhase.Confirming || phase == RestoreUiPhase.ConfirmingReplace) reset()
    }

    fun confirm() {
        val current = _state.value
        val file = current.pickedFile ?: return
        val mode = when (current.phase) {
            RestoreUiPhase.Confirming -> RestoreMode.FRESH
            RestoreUiPhase.ConfirmingReplace -> if (current.canReplace && !freshOnly) RestoreMode.REPLACE else return
            else -> return
        }
        reset { copy(phase = RestoreUiPhase.Uploading, pickedFile = file) }
        job = viewModelScope.launch {
            try {
                restore.restore(file, mode).collect(::apply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reset { copy(phase = RestoreUiPhase.Failed, error = message(e)) }
                // A replace that failed after the copy was saved can still be undone.
                loadUndo()
            }
        }
    }

    /** Stops the summary check or the sending of parts. A restore the server already started finishes on its own. */
    fun cancel() {
        val phase = _state.value.phase
        if (phase != RestoreUiPhase.Uploading && phase != RestoreUiPhase.CheckingAccount) return
        job?.cancel()
        reset()
    }

    fun dismissError() = _state.update { if (it.phase == RestoreUiPhase.Failed) RestoreUiState(undoSession = it.undoSession, undoPhase = it.undoPhase, undoDone = it.undoDone) else it }

    // region Undo

    /** Finds the newest restore that can still be undone (`GET /user/restores`). Best-effort: no row when it fails. */
    fun loadUndo() {
        viewModelScope.launch {
            val found = try {
                restore.undoableRestore()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return@launch
            }
            _state.update { if (it.undoBusy) it else it.copy(undoSession = found) }
        }
    }

    fun requestUndo() {
        if (_state.value.undoSession == null || _state.value.undoBusy || _state.value.isBusy) return
        _state.update { it.copy(undoPhase = UndoPhase.Confirming, undoError = null) }
    }

    fun cancelUndoConfirmation() = _state.update { if (it.undoPhase == UndoPhase.Confirming) it.copy(undoPhase = UndoPhase.Idle) else it }

    fun confirmUndo() {
        val session = _state.value.undoSession ?: return
        if (_state.value.undoPhase != UndoPhase.Confirming) return
        _state.update { it.copy(undoPhase = UndoPhase.Running, undoError = null, undoDone = false) }
        undoJob = viewModelScope.launch {
            try {
                restore.undo(session.id)
                restore.refreshAfterRestore()
                _state.update { it.copy(undoPhase = UndoPhase.Idle, undoSession = null, undoDone = true, phase = RestoreUiPhase.Idle, summary = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val gone = e is RestoreUndoExpiredException || (e is ApiError.Server && e.serverMessage == "UndoUnavailable")
                // A failed Undo keeps its row (Try again); one that can never run is dropped.
                _state.update {
                    it.copy(
                        undoPhase = UndoPhase.Idle,
                        undoSession = if (gone) null else it.undoSession,
                        undoError = undoMessage(e),
                    )
                }
            }
        }
    }

    fun dismissUndoError() = _state.update { it.copy(undoError = null) }

    // endregion

    private suspend fun apply(update: RestoreUpdate) {
        when (update) {
            is RestoreUpdate.Uploading -> _state.update {
                it.copy(phase = RestoreUiPhase.Uploading, uploadedParts = update.done, totalParts = update.total)
            }
            RestoreUpdate.Checking -> _state.update { it.copy(phase = RestoreUiPhase.Checking) }
            is RestoreUpdate.Restoring -> _state.update { it.copy(phase = RestoreUiPhase.Restoring, progress = update.progress) }
            is RestoreUpdate.Finished -> {
                restore.refreshAfterRestore()
                reset { copy(phase = RestoreUiPhase.Done, summary = update.summary ?: RestoreSummary()) }
                // A replace leaves a copy to undo; look it up right away (a fresh restore finds nothing).
                loadUndo()
            }
        }
    }

    private fun undoMessage(error: Throwable): String = when (error) {
        is RestoreUndoFailedException -> Strings.get(R.string.restore_undo_error_failed)
        is RestoreUndoExpiredException -> Strings.get(R.string.restore_undo_error_unavailable)
        is ApiError.Server -> when (error.serverMessage) {
            "UndoUnavailable" -> Strings.get(R.string.restore_undo_error_unavailable)
            "RestoreInProgress" -> Strings.get(R.string.restore_error_in_progress)
            else -> Strings.get(R.string.restore_undo_error_failed)
        }
        is RestoreExpiredException -> Strings.get(R.string.restore_undo_error_unavailable)
        is ApiError.Network -> Strings.get(R.string.common_you_re_offline_check_your)
        is ApiError.Unauthorized -> Strings.get(R.string.export_your_session_expired_sign_in)
        else -> Strings.get(R.string.restore_undo_error_failed)
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

    companion object {
        /** The word the user types to allow a replace (the same in every language). */
        const val REPLACE_WORD = "REPLACE"
    }
}
