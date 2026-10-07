package com.spenditos.android.feature.statements

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.data.model.StatementImportResult
import com.spenditos.android.data.model.StatementImportStats
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.toImportTransactions
import com.spenditos.android.data.statements.StatementFileTooLargeException
import com.spenditos.android.data.statements.StatementFileUnavailableException
import com.spenditos.android.data.statements.StatementImporter
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class StatementImportStep { CHOOSE, ANALYZING, MAP_COLUMNS, PREVIEW, IMPORTING, COMPLETE }
enum class StatementColumn { DATE, DESCRIPTION, AMOUNT, DEBIT, CREDIT }

private enum class FailedOperation { ANALYZE, RESTORE, PREVIEW, IMPORT }

data class StatementImportUiState(
    val isCheckingSettings: Boolean = true,
    val premiumActive: Boolean = false,
    val settingsError: String? = null,
    val settingsOffline: Boolean = false,
    val step: StatementImportStep = StatementImportStep.CHOOSE,
    val isWorking: Boolean = false,
    val pickedUri: String? = null,
    val filename: String? = null,
    val documentKey: String? = null,
    val analysis: StatementAnalysisResult? = null,
    val mapping: StatementColumnMapping? = null,
    val preview: StatementPreviewResult? = null,
    val result: StatementImportResult? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
    val importRequestSent: Boolean = false,
    val backBlockedHint: Boolean = false,
    val canRetry: Boolean = false,
    val currency: String = "USD",
)

/** Applies the iOS picker rule: one amount column or the debit/credit pair. */
fun changeStatementMapping(mapping: StatementColumnMapping, column: StatementColumn, value: String?): StatementColumnMapping = when (column) {
    StatementColumn.DATE -> mapping.copy(dateColumn = value)
    StatementColumn.DESCRIPTION -> mapping.copy(descriptionColumn = value)
    StatementColumn.AMOUNT -> mapping.copy(amountColumn = value, debitColumn = null, creditColumn = null)
    StatementColumn.DEBIT -> mapping.copy(debitColumn = value, amountColumn = null)
    StatementColumn.CREDIT -> mapping.copy(creditColumn = value, amountColumn = null)
}

fun normalizeStatementMapping(mapping: StatementColumnMapping): StatementColumnMapping =
    if (mapping.amountColumn != null) mapping.copy(debitColumn = null, creditColumn = null) else mapping

@HiltViewModel
class StatementImportViewModel @Inject constructor(
    private val importer: StatementImporter,
    private val settings: UserSettingsActions,
    private val savedStateHandle: SavedStateHandle,
    private val invalidations: FinanceInvalidationBus,
) : ViewModel() {
    private val _state = MutableStateFlow(
        StatementImportUiState(
            step = savedStateHandle.get<String>(KEY_STEP)?.let { saved ->
                runCatching { StatementImportStep.valueOf(saved) }.getOrNull()
            } ?: StatementImportStep.CHOOSE,
            pickedUri = savedStateHandle[KEY_URI],
        ),
    )
    val state: StateFlow<StatementImportUiState> = _state.asStateFlow()

    private var failedOperation: FailedOperation? = null
    private var restoreStep = _state.value.step
    private var restoredMapping = savedStateHandle.get<String>(KEY_MAPPING)?.let { raw ->
        runCatching { SpenditosJson.decodeFromString(StatementColumnMapping.serializer(), raw) }.getOrNull()
    }

    init {
        // A saved flow belongs to the account that started it; another account starts clean.
        val owner = importer.currentUserId
        if (savedStateHandle.get<String>(KEY_USER) != owner) {
            listOf(
                KEY_STEP, KEY_URI, KEY_IDEMPOTENCY, KEY_MAPPING, KEY_RESULT_COUNT, KEY_RESULT_MESSAGE,
                KEY_RESULT_CREATED, KEY_RESULT_UPDATED, KEY_RESULT_SKIPPED,
            ).forEach { savedStateHandle.remove<Any>(it) }
            restoredMapping = null
            restoreStep = StatementImportStep.CHOOSE
            _state.value = StatementImportUiState()
            savedStateHandle[KEY_USER] = owner
        }
        viewModelScope.launch { settings.currency().collect { code -> _state.update { it.copy(currency = code) } } }
        checkSettings()
    }

    fun onFileSelected(uri: Uri) {
        if (_state.value.isCheckingSettings || !_state.value.premiumActive || _state.value.importRequestSent) return
        // Re-picking the same file after a finished import is a new import, so it gets a new key.
        val samePickedUri = savedStateHandle.get<String>(KEY_URI) == uri.toString() && _state.value.step != StatementImportStep.COMPLETE
        val key = if (samePickedUri) savedStateHandle.get<String>(KEY_IDEMPOTENCY) ?: UUID.randomUUID().toString() else UUID.randomUUID().toString()
        failedOperation = null
        restoredMapping = null
        savedStateHandle[KEY_STEP] = StatementImportStep.CHOOSE.name
        savedStateHandle[KEY_IDEMPOTENCY] = key
        savedStateHandle[KEY_MAPPING] = null
        update {
            it.copy(
                step = StatementImportStep.ANALYZING,
                isWorking = true,
                pickedUri = uri.toString(),
                filename = null,
                documentKey = null,
                analysis = null,
                mapping = null,
                preview = null,
                result = null,
                error = null,
                isOffline = false,
                importRequestSent = false,
                backBlockedHint = false,
                canRetry = false,
            )
        }
        viewModelScope.launch { analyze(uri) }
    }

    fun onMappingChange(mapping: StatementColumnMapping) {
        val normalized = normalizeStatementMapping(mapping)
        restoredMapping = normalized
        update { it.copy(mapping = normalized, error = null, isOffline = false, canRetry = false) }
        savedStateHandle[KEY_MAPPING] = SpenditosJson.encodeToString(StatementColumnMapping.serializer(), normalized)
    }

    fun preview() {
        val mapping = _state.value.mapping ?: return
        if (!mapping.isProcessable || _state.value.isWorking) return
        viewModelScope.launch { loadPreview(mapping) }
    }

    fun startImport() {
        val current = _state.value
        val preview = current.preview ?: return
        val idempotencyKey = savedStateHandle.get<String>(KEY_IDEMPOTENCY) ?: return
        if (current.isWorking || preview.transactions.isEmpty()) return
        viewModelScope.launch { importPreview(preview, idempotencyKey) }
    }

    fun retry() {
        if (_state.value.isWorking) return
        when (failedOperation) {
            FailedOperation.ANALYZE -> _state.value.pickedUri?.let { uri -> viewModelScope.launch { analyze(Uri.parse(uri)) } }
            FailedOperation.RESTORE -> _state.value.pickedUri?.let { uri -> viewModelScope.launch { restoreFile(Uri.parse(uri)) } }
            FailedOperation.PREVIEW -> _state.value.mapping?.let { mapping -> viewModelScope.launch { loadPreview(mapping) } }
            FailedOperation.IMPORT -> startImport()
            null -> Unit
        }
    }

    fun retrySettings() {
        if (!_state.value.isCheckingSettings) checkSettings()
    }

    fun noteBackBlocked() {
        if (_state.value.importRequestSent) update { it.copy(backBlockedHint = true) }
    }

    private fun checkSettings() {
        update { it.copy(isCheckingSettings = true, settingsError = null, settingsOffline = false) }
        viewModelScope.launch {
            try {
                val userSettings = settings.refresh()
                update { it.copy(isCheckingSettings = false, premiumActive = userSettings.premium?.active == true) }
                if (userSettings.premium?.active == true) restoreSavedFlow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                update {
                    it.copy(
                        isCheckingSettings = false,
                        premiumActive = false,
                        settingsOffline = error is ApiError.Network,
                        settingsError = if (error is ApiError.Network) "You're offline. Check your connection and try again." else "Couldn't check your Pro access. Try again.",
                    )
                }
            }
        }
    }

    private suspend fun restoreSavedFlow() {
        val uriString = _state.value.pickedUri ?: return
        restoreStep = _state.value.step
        if (restoreStep == StatementImportStep.CHOOSE) return
        if (restoreStep == StatementImportStep.COMPLETE) {
            val count = savedStateHandle.get<Int>(KEY_RESULT_COUNT)
            if (count != null) {
                val stats = savedStateHandle.get<Int>(KEY_RESULT_CREATED)?.let { created ->
                    StatementImportStats(
                        created = created,
                        updated = savedStateHandle.get<Int>(KEY_RESULT_UPDATED) ?: 0,
                        skipped = savedStateHandle.get<Int>(KEY_RESULT_SKIPPED) ?: 0,
                    )
                }
                update {
                    it.copy(
                        step = StatementImportStep.COMPLETE,
                        result = StatementImportResult(savedStateHandle[KEY_RESULT_MESSAGE] ?: "Import complete.", count, stats),
                        isWorking = false,
                    )
                }
                return
            }
            restoreStep = StatementImportStep.PREVIEW
        }
        if (restoreStep == StatementImportStep.IMPORTING) restoreStep = StatementImportStep.PREVIEW
        try {
            if (!importer.isReadable(Uri.parse(uriString))) {
                update {
                    it.copy(
                        step = StatementImportStep.CHOOSE,
                        isWorking = false,
                        error = "That file is no longer available. Choose it again.",
                        isOffline = false,
                        importRequestSent = false,
                        canRetry = false,
                    )
                }
                return
            }
            failedOperation = FailedOperation.RESTORE
            update {
                it.copy(
                    step = restoreStep,
                    isWorking = false,
                    error = "Your statement needs to be prepared again. Try again to continue.",
                    isOffline = false,
                    importRequestSent = false,
                    canRetry = true,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failedOperation = FailedOperation.RESTORE
            update { it.copy(step = restoreStep, isWorking = false, error = errorCopy(error), isOffline = error is ApiError.Network, canRetry = true) }
        }
    }

    private suspend fun restoreFile(uri: Uri) {
        failedOperation = null
        val resumeAt = restoreStep
        update { it.copy(step = StatementImportStep.ANALYZING, isWorking = true, error = null, isOffline = false, canRetry = false) }
        try {
            val document = importer.analyze(uri)
            val mapping = normalizeStatementMapping(restoredMapping ?: document.analysis.mapping)
            update {
                it.copy(
                    step = StatementImportStep.MAP_COLUMNS,
                    isWorking = false,
                    filename = document.filename,
                    documentKey = document.documentKey,
                    analysis = document.analysis,
                    mapping = mapping,
                    error = null,
                    isOffline = false,
                )
            }
            onMappingChange(mapping)
            if (resumeAt == StatementImportStep.PREVIEW) loadPreview(mapping)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error is StatementFileUnavailableException) {
                failedOperation = null
                update {
                    it.copy(
                        step = StatementImportStep.CHOOSE,
                        isWorking = false,
                        error = "That file is no longer available. Choose it again.",
                        isOffline = false,
                        importRequestSent = false,
                        canRetry = false,
                    )
                }
            } else {
                failedOperation = FailedOperation.RESTORE
                update { it.copy(step = resumeAt, isWorking = false, error = errorCopy(error), isOffline = error is ApiError.Network, canRetry = true) }
            }
        }
    }

    private suspend fun analyze(uri: Uri) {
        failedOperation = null
        update { it.copy(step = StatementImportStep.ANALYZING, isWorking = true, error = null, isOffline = false, canRetry = false) }
        savedStateHandle[KEY_STEP] = StatementImportStep.ANALYZING.name
        try {
            val document = importer.analyze(uri)
            val initialMapping = normalizeStatementMapping(document.analysis.mapping)
            update {
                it.copy(
                    step = StatementImportStep.MAP_COLUMNS,
                    isWorking = false,
                    filename = document.filename,
                    documentKey = document.documentKey,
                    analysis = document.analysis,
                    mapping = initialMapping,
                    error = null,
                    isOffline = false,
                    canRetry = false,
                )
            }
            onMappingChange(initialMapping)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failedOperation = FailedOperation.ANALYZE
            update { it.copy(step = StatementImportStep.ANALYZING, isWorking = false, error = errorCopy(error), isOffline = error is ApiError.Network, canRetry = true) }
        }
    }

    private suspend fun loadPreview(mapping: StatementColumnMapping) {
        val current = _state.value
        val filename = current.filename ?: return
        val documentKey = current.documentKey ?: return
        failedOperation = null
        update { it.copy(step = StatementImportStep.ANALYZING, isWorking = true, error = null, isOffline = false, canRetry = false) }
        savedStateHandle[KEY_STEP] = StatementImportStep.MAP_COLUMNS.name
        try {
            val preview = importer.preview(filename, documentKey, mapping)
            if (preview.transactions.isEmpty()) throw StatementPreviewEmptyException()
            update { it.copy(step = StatementImportStep.PREVIEW, isWorking = false, preview = preview, error = null, isOffline = false, canRetry = false) }
            savedStateHandle[KEY_STEP] = StatementImportStep.PREVIEW.name
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failedOperation = FailedOperation.PREVIEW
            update {
                it.copy(
                    step = StatementImportStep.MAP_COLUMNS,
                    isWorking = false,
                    error = if (error is StatementPreviewEmptyException) "No transactions found. Check your column choices." else errorCopy(error),
                    isOffline = error is ApiError.Network,
                    canRetry = true,
                )
            }
        }
    }

    private suspend fun importPreview(preview: StatementPreviewResult, idempotencyKey: String) {
        failedOperation = null
        update { it.copy(step = StatementImportStep.IMPORTING, isWorking = true, error = null, isOffline = false, importRequestSent = true, backBlockedHint = false, canRetry = false) }
        savedStateHandle[KEY_STEP] = StatementImportStep.IMPORTING.name
        try {
            val result = importer.importTransactions(preview.toImportTransactions(), idempotencyKey)
            savedStateHandle[KEY_RESULT_COUNT] = result.count
            savedStateHandle[KEY_RESULT_MESSAGE] = result.message
            savedStateHandle[KEY_RESULT_CREATED] = result.stats?.created
            savedStateHandle[KEY_RESULT_UPDATED] = result.stats?.updated
            savedStateHandle[KEY_RESULT_SKIPPED] = result.stats?.skipped
            _state.update { it.copy(step = StatementImportStep.COMPLETE, isWorking = false, result = result, importRequestSent = false, backBlockedHint = false, error = null) }
            savedStateHandle[KEY_URI] = _state.value.pickedUri
            savedStateHandle[KEY_STEP] = StatementImportStep.COMPLETE.name
            invalidations.invalidate(Aggregate.TRANSACTION, Aggregate.IMPORT, Aggregate.CATEGORY)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failedOperation = FailedOperation.IMPORT
            update {
                it.copy(
                    step = StatementImportStep.PREVIEW,
                    isWorking = false,
                    importRequestSent = false,
                    error = errorCopy(error),
                    isOffline = error is ApiError.Network,
                    canRetry = true,
                )
            }
            savedStateHandle[KEY_STEP] = StatementImportStep.PREVIEW.name
        }
    }

    private fun errorCopy(error: Throwable): String = when (error) {
        is StatementFileTooLargeException -> "Choose a file 25 MB or smaller."
        is StatementFileUnavailableException -> "Couldn't open that file. Choose it again."
        is ApiError.Network -> "You're offline. Check your connection and try again."
        is ApiError.Server -> when {
            error.status == 400 -> "The server couldn't read this file. Check its columns and try again."
            error.status == 403 -> "You don't have access to this statement file."
            error.status == 413 -> "Choose a file 25 MB or smaller."
            error.status == 429 -> "Too many attempts. Please try again later."
            error.status >= 500 -> "Statement import is temporarily unavailable. Try again."
            error.serverMessage.isNotBlank() -> error.serverMessage
            else -> "Couldn't import this statement. Please try again."
        }
        else -> "Couldn't import this statement. Please try again."
    }

    private fun update(transform: (StatementImportUiState) -> StatementImportUiState) {
        _state.update(transform)
        savedStateHandle[KEY_URI] = _state.value.pickedUri
        savedStateHandle[KEY_STEP] = _state.value.step.name
    }

    private class StatementPreviewEmptyException : Exception()

    private companion object {
        const val KEY_USER = "statement.user"
        const val KEY_STEP = "statement.step"
        const val KEY_URI = "statement.uri"
        const val KEY_IDEMPOTENCY = "statement.idempotencyKey"
        const val KEY_MAPPING = "statement.mapping"
        const val KEY_RESULT_COUNT = "statement.result.count"
        const val KEY_RESULT_MESSAGE = "statement.result.message"
        const val KEY_RESULT_CREATED = "statement.result.created"
        const val KEY_RESULT_UPDATED = "statement.result.updated"
        const val KEY_RESULT_SKIPPED = "statement.result.skipped"
    }
}
