package com.spenditos.android.feature.capture

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.capture.AudioRecorder
import com.spenditos.android.data.capture.CaptureActions
import com.spenditos.android.data.capture.CaptureContext
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.capture.CaptureFailure
import com.spenditos.android.data.capture.CaptureSaveItem
import com.spenditos.android.data.entitlement.ProAccess
import com.spenditos.android.data.model.CaptureAnalysisItem
import com.spenditos.android.data.quota.QuotaCopy
import com.spenditos.android.data.quota.QuotaSource
import com.spenditos.android.data.quota.SentenceManualFallback
import com.spenditos.android.data.quota.SentenceMeter
import com.spenditos.android.data.quota.isSentenceQuota
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.data.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class CaptureMode { TYPE, SPEAK }
enum class CaptureAnalysisPhase { TRANSCRIBE, PARSE }

sealed interface CaptureStep {
    data object Input : CaptureStep
    data class Analyzing(val phase: CaptureAnalysisPhase) : CaptureStep
    data object CheckTranscript : CaptureStep
    data object Review : CaptureStep
    data object Saving : CaptureStep
    data object Done : CaptureStep
}

@Serializable
data class CaptureReviewItem(
    val idempotencyKeyId: String,
    val amountText: String,
    val description: String,
    val date: String,
    val categoryId: String? = null,
    val suggestedCategoryId: String? = null,
    val categoryHint: String? = null,
    val currency: String? = null,
    val transactionType: String? = null,
    val categoryEdited: Boolean = false,
    val isFrozen: Boolean = false,
    val include: Boolean = true,
    val failureReason: String? = null,
    // Scan rows only (#358): why a row starts unchecked and what it repeats. Voice and typed rows leave these empty.
    /** `duplicate` / `payment` / `transfer` / `rule` (see [com.spenditos.android.data.model.ScanSkipReason]). */
    val skipReason: String? = null,
    /** The user rule that categorized or skipped the row. */
    val ruleId: String? = null,
    /** The merchant the scan read (groups rows in the review and names the rule "Always" creates). */
    val merchantName: String? = null,
    /** The bank still lists the charge as pending. */
    val isPending: Boolean? = null,
    /** The existing transaction this row probably repeats: its date and amount. */
    val duplicateDate: String? = null,
    val duplicateAmount: Double? = null,
    /** Scan rows in another currency (#358): the user's currency the row was converted to ("USD → CAD"). */
    val convertedTo: String? = null,
    /** Set only when [amountText] could not be converted and is still in [currency]: shown in that currency, not the user's. */
    val reviewCurrency: String? = null,
)

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.TYPE,
    val step: CaptureStep = CaptureStep.Input,
    val text: String = "",
    val transcript: String = "",
    val transcriptAnalysisId: String? = null,
    val captureSessionId: String? = null,
    val analysisId: String? = null,
    val source: String = "text",
    val items: List<CaptureReviewItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currency: String = "USD",
    val categoriesLoading: Boolean = true,
    val permissionDenied: Boolean = false,
    /** This month's free voice captures are used up (403 QuotaExhausted): iOS shows "Monthly Limit Reached". */
    val quotaExceeded: Boolean = false,
    val isRecording: Boolean = false,
    val recordingMillis: Long = 0,
    val speechDetected: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val savedCount: Int = 0,
    /** Monthly smart-entry (typed sentence) quota; null until loaded, for Pro, or when the server is unreachable. */
    val sentenceMeter: SentenceMeter? = null,
    /** Effective Pro (store or server Premium): no counter, no banner. */
    val isPro: Boolean = false,
) {
    val includedItems: List<CaptureReviewItem> get() = items.filter { it.include }
    val canSave: Boolean get() = step == CaptureStep.Review && includedItems.isNotEmpty() && includedItems.all {
        parseCaptureAmount(it.amountText) != null && it.description.trim().isNotEmpty() && runCatching { LocalDate.parse(it.date) }.isSuccess
    }
}

sealed interface CaptureEvent {
    data class Saved(val message: String) : CaptureEvent

    /**
     * This month's smart entries are used up: open the manual form with the typed text (and the
     * amount read on the device) so the entry can still be saved. Never a dead end.
     */
    data class ManualFallback(val prefill: AddTransactionPrefill) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val capture: CaptureActions,
    private val dataSource: CaptureDataSource,
    private val recorder: AudioRecorder,
    private val savedStateHandle: SavedStateHandle,
    @ReceiptIoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val proAccess: ProAccess,
    private val quota: QuotaSource,
) : ViewModel() {
    private val _state = MutableStateFlow(restoreState(savedStateHandle, dataSource.currentUserId))
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var activeRecording: File? = null
    private var recordingMonitor: Job? = null
    private var operationJob: Job? = null
    private var recordingStartedAtMillis: Long = 0
    private var voiceCaptureAnalysisId: String? = _state.value.transcriptAnalysisId

    init {
        viewModelScope.launch {
            dataSource.categories().collect { categories ->
                _state.update { current -> current.copy(categories = categories, categoriesLoading = false, items = resolveCategories(current.items, categories)) }
                persist()
            }
        }
        viewModelScope.launch {
            dataSource.currency().collect { currency -> _state.update { it.copy(currency = currency) } }
        }
        viewModelScope.launch {
            proAccess.state.collect { access -> _state.update { it.copy(isPro = access.effectiveIsPro) } }
        }
        refreshSentenceMeter()
    }

    /** Loads the counter shown under the sentence field. Free users only; best effort. */
    fun refreshSentenceMeter(force: Boolean = false) {
        viewModelScope.launch {
            val meter = runCatching {
                if (force) quota.invalidate()
                if (proAccess.effectiveIsPro) null else quota.sentenceMeter(forceRefresh = force)
            }.getOrNull()
            _state.update { it.copy(sentenceMeter = meter) }
        }
    }

    fun onModeChange(mode: CaptureMode) {
        if (_state.value.step != CaptureStep.Input || _state.value.isRecording) return
        _state.update { it.copy(mode = mode, error = null, offline = false) }
        persist()
    }

    fun dismissQuota() = _state.update { it.copy(quotaExceeded = false) }

    fun onTextChange(value: String) = _state.update { it.copy(text = value.take(MAX_TEXT_LENGTH), error = null, offline = false) }

    fun parseTypedText() {
        val current = _state.value
        if (current.text.isBlank() || current.step != CaptureStep.Input) return
        _state.value = current.copy(step = CaptureStep.Analyzing(CaptureAnalysisPhase.PARSE), error = null, offline = false)
        persist()
        operationJob = viewModelScope.launch {
            try {
                // A fresh typed sentence costs one smart entry for free users (a re-parse with a parent
                // analysis, and every voice transcript, is never blocked by this bucket).
                if (!proAccess.effectiveIsPro) {
                    var remaining = quota.remainingSentenceEntries()
                    if (remaining <= 0) {
                        // A cached zero may be stale (new month, admin change).
                        quota.refresh(force = true)
                        remaining = quota.remainingSentenceEntries()
                    }
                    if (remaining <= 0) {
                        routeSentenceToManualForm(current.text)
                        return@launch
                    }
                }
                val parsed = capture.parseText(current.text, CaptureContext(current.currency), inputType = "typed", parentAnalysisId = null)
                showReview(parsed, source = "text")
                refreshSentenceMeter(force = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Server safety net: same never-dead-end path as the pre-check.
                if (error is ApiError.Server && error.isQuotaExhausted && isSentenceQuota(error.quotaType) && !proAccess.effectiveIsPro) {
                    routeSentenceToManualForm(current.text)
                } else {
                    showError(error, CaptureStep.Input)
                }
            }
        }
    }

    private suspend fun routeSentenceToManualForm(sentence: String) {
        val fallback = SentenceManualFallback.from(sentence)
        _state.update {
            it.copy(
                step = CaptureStep.Input,
                error = null,
                offline = false,
                sentenceMeter = SentenceMeter(used = it.sentenceMeter?.used ?: 0, remaining = 0),
            )
        }
        persist()
        _events.send(
            CaptureEvent.ManualFallback(
                AddTransactionPrefill(
                    amount = fallback.amount?.toDoubleOrNull(),
                    description = fallback.description.takeIf { it.isNotEmpty() },
                    smartEntriesUsed = true,
                ),
            ),
        )
    }

    /** Called only after ActivityResultContracts.RequestPermission grants RECORD_AUDIO. */
    fun onRecordPermissionResult(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(mode = CaptureMode.SPEAK, permissionDenied = true, error = Strings.get(R.string.capture_allow_microphone_access_in_settings_2), offline = false) }
            return
        }
        _state.update { it.copy(permissionDenied = false, error = null, offline = false) }
        startRecording()
    }

    fun onPermissionSettingsReturned(granted: Boolean) {
        _state.update { it.copy(permissionDenied = !granted, error = if (granted) null else it.error) }
    }

    fun startRecording() {
        if (_state.value.step != CaptureStep.Input || _state.value.isRecording) return
        val file = try {
            recorder.start()
        } catch (_: Exception) {
            _state.update { it.copy(error = Strings.get(R.string.capture_couldn_t_start_recording_try), offline = false) }
            return
        }
        activeRecording = file
        recordingStartedAtMillis = SystemClock.elapsedRealtime()
        _state.update { it.copy(mode = CaptureMode.SPEAK, isRecording = true, recordingMillis = 0, speechDetected = false, error = null, offline = false) }
        recordingMonitor = viewModelScope.launch {
            while (_state.value.isRecording) {
                delay(RECORD_SAMPLE_MILLIS)
                val amplitude = runCatching { recorder.maxAmplitude() }.getOrDefault(0)
                val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAtMillis).coerceAtLeast(0L)
                _state.update { it.copy(recordingMillis = elapsed, speechDetected = it.speechDetected || amplitude > SPEECH_AMPLITUDE_THRESHOLD) }
                if (elapsed >= MAX_RECORDING_MILLIS) {
                    stopRecording()
                    break
                }
            }
        }
    }

    /** The screen left the foreground mid-recording: finish it now so no microphone session outlives the screen. */
    fun onBackgrounded() {
        if (_state.value.isRecording) stopRecording()
    }

    fun stopRecording() {
        val current = _state.value
        if (!current.isRecording) return
        recordingMonitor?.cancel()
        recordingMonitor = null
        val file = try { recorder.stop() ?: activeRecording } catch (_: Exception) { activeRecording }
        _state.update { it.copy(isRecording = false, step = CaptureStep.Analyzing(CaptureAnalysisPhase.TRANSCRIBE), error = null, offline = false) }
        persist()
        if (file == null) {
            activeRecording = null
            _state.update { it.copy(step = CaptureStep.Input, error = Strings.get(R.string.capture_no_recording_file_found)) }
            return
        }
        operationJob = viewModelScope.launch {
            try {
                val transcript = capture.transcribeAudio(file, CaptureContext(current.currency), current.speechDetected)
                voiceCaptureAnalysisId = transcript.analysisId
                _state.update {
                    it.copy(
                        step = CaptureStep.CheckTranscript,
                        transcript = transcript.text,
                        transcriptAnalysisId = transcript.analysisId,
                        captureSessionId = transcript.captureSessionId,
                        error = null,
                        offline = false,
                    )
                }
                persist()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error, CaptureStep.Input)
            } finally {
                withContext(NonCancellable + ioDispatcher) { file.delete() }
                if (activeRecording == file) activeRecording = null
            }
        }
    }

    fun onTranscriptChange(value: String) = _state.update { it.copy(transcript = value.take(MAX_TEXT_LENGTH), error = null, offline = false) }

    fun parseTranscript() {
        val current = _state.value
        if (current.step != CaptureStep.CheckTranscript || current.transcript.isBlank()) return
        _state.value = current.copy(step = CaptureStep.Analyzing(CaptureAnalysisPhase.PARSE), error = null, offline = false)
        persist()
        operationJob = viewModelScope.launch {
            try {
                val parsed = capture.parseText(
                    current.transcript,
                    CaptureContext(current.currency),
                    inputType = "voice",
                    parentAnalysisId = current.transcriptAnalysisId,
                )
                showReview(parsed, source = "voice")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error, CaptureStep.CheckTranscript)
            }
        }
    }

    fun toggleInclude(key: String) = updateItem(key, ReviewItemEditing::toggled)
    fun onAmountChange(key: String, text: String) = updateItem(key) { ReviewItemEditing.withAmountText(it, text) }
    fun onDescriptionChange(key: String, text: String) = updateItem(key) { ReviewItemEditing.withDescription(it, text) }
    fun onDateChange(key: String, date: LocalDate) = updateItem(key) { ReviewItemEditing.withDate(it, date) }

    /** iOS TransactionItemEditorView type picker (see [ReviewItemEditing.withType]). */
    fun onTypeChange(key: String, income: Boolean) = updateItem(key) { ReviewItemEditing.withType(it, income) }

    fun onCategoryChange(key: String, categoryId: String?) = updateItem(key) { ReviewItemEditing.withCategory(it, categoryId, _state.value.categories) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val included = current.includedItems
        val saveItems = included.map { item ->
            CaptureSaveItem(
                amount = requireNotNull(parseCaptureAmount(item.amountText)).toDouble(),
                date = LocalDate.parse(item.date),
                categoryId = item.categoryId?.takeIf { id ->
                    val income = item.transactionType.equals("income", ignoreCase = true)
                    current.categories.any { it.id == id && it.isIncome == income }
                },
                description = item.description.trim(),
                idempotencyKeyId = item.idempotencyKeyId,
            )
        }
        val includedKeys = saveItems.map { it.idempotencyKeyId }.toSet()
        _state.value = current.copy(
            step = CaptureStep.Saving,
            items = current.items.map { item -> if (item.idempotencyKeyId in includedKeys) item.copy(isFrozen = true) else item },
            error = null,
            offline = false,
        )
        persist()
        operationJob = viewModelScope.launch {
            try {
                val result = capture.save(saveItems, current.source, current.analysisId)
                val remaining = current.items
                    .filterNot { it.idempotencyKeyId in result.savedIds }
                    .map { item -> item.copy(failureReason = result.failures[item.idempotencyKeyId], isFrozen = item.idempotencyKeyId in result.unconfirmedIds) }
                val savedTotal = current.savedCount + result.savedCount
                if (remaining.none { it.include }) {
                    _state.value = current.copy(step = CaptureStep.Done, items = emptyList(), savedCount = savedTotal, error = null, offline = false)
                    persist()
                    _events.send(CaptureEvent.Saved(Strings.get(R.string.capture_saved_transactions, savedTotal)))
                } else {
                    _state.value = current.copy(step = CaptureStep.Review, items = remaining, savedCount = savedTotal, error = null, offline = false)
                    persist()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error, CaptureStep.Review)
            }
        }
    }

    fun retry() {
        val current = _state.value
        val step = if (current.items.isNotEmpty()) CaptureStep.Review else if (current.transcriptAnalysisId != null) CaptureStep.CheckTranscript else CaptureStep.Input
        _state.update { it.copy(step = step, error = null, offline = false) }
        persist()
    }

    fun retryEmptyAnalysis() {
        val current = _state.value
        _state.update {
            it.copy(
                step = if (current.source == "voice") CaptureStep.CheckTranscript else CaptureStep.Input,
                mode = if (current.source == "voice") CaptureMode.SPEAK else CaptureMode.TYPE,
                items = emptyList(),
                analysisId = null,
                error = null,
                offline = false,
            )
        }
        persist()
    }

    fun restart() {
        closeVoiceSession("restarted")
        operationJob?.cancel()
        operationJob = null
        recordingMonitor?.cancel()
        recorder.cancel()
        activeRecording?.delete()
        activeRecording = null
        voiceCaptureAnalysisId = null
        _state.update {
            it.copy(step = CaptureStep.Input, text = "", transcript = "", transcriptAnalysisId = null, captureSessionId = null, analysisId = null, items = emptyList(), source = "text", error = null, offline = false, isRecording = false, savedCount = 0)
        }
        persist()
    }

    fun cancel() {
        closeVoiceSession("abandoned")
        operationJob?.cancel()
        operationJob = null
        recordingMonitor?.cancel()
        recorder.cancel()
        activeRecording?.delete()
        activeRecording = null
        _state.update { it.copy(isRecording = false) }
    }

    private fun showReview(parsed: com.spenditos.android.data.capture.CaptureParseResult, source: String) {
        val items = parsed.items.map { item -> item.toReviewItem(_state.value.categories) }
        _state.update {
            it.copy(step = CaptureStep.Review, items = items, analysisId = parsed.analysisId, source = source, error = null, offline = false)
        }
        persist()
    }

    private fun CaptureAnalysisItem.toReviewItem(categories: List<Category>): CaptureReviewItem {
        val income = transactionType.equals("income", ignoreCase = true)
        val category = categories.firstOrNull { it.id == categoryId && it.isIncome == income }
            ?: categoryHint?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                categories.firstOrNull { it.isIncome == income && it.name.equals(hint, ignoreCase = true) }
            }
        val amountText = amount?.takeIf { it.isFinite() && it > 0 }?.let {
            BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
        }.orEmpty()
        val parsedDate = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        return CaptureReviewItem(
            idempotencyKeyId = UUID.randomUUID().toString(),
            amountText = amountText,
            description = (description ?: merchantName.orEmpty()).take(200),
            date = parsedDate.toString(),
            categoryId = category?.id,
            suggestedCategoryId = categoryId,
            categoryHint = categoryHint,
            currency = currency,
            transactionType = transactionType,
        )
    }

    private fun resolveCategories(items: List<CaptureReviewItem>, categories: List<Category>): List<CaptureReviewItem> = items.map { item ->
        if (item.categoryId != null || item.categoryEdited) return@map item
        val income = item.transactionType.equals("income", ignoreCase = true)
        val exact = categories.firstOrNull { it.id == item.suggestedCategoryId && it.isIncome == income }
        val match = exact ?: item.categoryHint?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
            categories.firstOrNull { it.isIncome == income && it.name.equals(hint, ignoreCase = true) }
        }
        if (match != null) item.copy(categoryId = match.id) else item
    }

    private fun updateItem(key: String, transform: (CaptureReviewItem) -> CaptureReviewItem) {
        _state.update { current -> current.copy(items = current.items.map { if (it.idempotencyKeyId == key && !it.isFrozen) transform(it) else it }) }
        persist()
    }

    private fun showError(error: Throwable, returnTo: CaptureStep) {
        val message = when (error) {
            is ApiError.Network -> Strings.get(R.string.common_you_re_offline_try_again)
            is ApiError.Server -> if (error.isQuotaExhausted) QuotaCopy.VOICE_EXHAUSTED else Strings.get(R.string.capture_couldn_t_complete_that_try)
            CaptureFailure.NoSpeechDetected -> Strings.get(R.string.capture_no_speech_was_detected_please)
            CaptureFailure.EmptyTranscript -> Strings.get(R.string.capture_no_transcript_was_returned_try)
            is CaptureFailure.AnalysisFailed -> error.userMessage
            CaptureFailure.AnalysisTimeout -> Strings.get(R.string.capture_analysis_timed_out_try_again)
            else -> Strings.get(R.string.capture_couldn_t_complete_that_try)
        }
        val quotaHit = error is ApiError.Server && error.isQuotaExhausted
        _state.update { current ->
            val knownNoCommit = error is ApiError.Server && error.status in 400..499
            current.copy(
                step = returnTo,
                isRecording = false,
                items = if (knownNoCommit) current.items.map { it.copy(isFrozen = false) } else current.items,
                error = if (quotaHit) null else message,
                offline = error is ApiError.Network,
                quotaExceeded = quotaHit,
            )
        }
        persist()
    }

    private fun closeVoiceSession(outcome: String) {
        val id = voiceCaptureAnalysisId ?: _state.value.transcriptAnalysisId ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { capture.closeCaptureSession(id, outcome) }
        }
    }

    private fun persist() {
        val current = _state.value
        val stepName = when (current.step) {
            CaptureStep.Input -> "INPUT"
            CaptureStep.CheckTranscript -> "CHECK_TRANSCRIPT"
            CaptureStep.Review, CaptureStep.Saving -> "REVIEW"
            CaptureStep.Done -> "DONE"
            is CaptureStep.Analyzing -> "INPUT"
        }
        val payload = PersistedCapture(
            step = stepName,
            mode = current.mode.name,
            text = current.text,
            transcript = current.transcript,
            transcriptAnalysisId = current.transcriptAnalysisId,
            captureSessionId = current.captureSessionId,
            analysisId = current.analysisId,
            source = current.source,
            items = current.items,
            savedCount = current.savedCount,
        )
        savedStateHandle[KEY_OWNER] = dataSource.currentUserId
        savedStateHandle[KEY_STATE] = SpenditosJson.encodeToString(PersistedCapture.serializer(), payload)
    }

    override fun onCleared() {
        operationJob?.cancel()
        recordingMonitor?.cancel()
        recorder.cancel()
        activeRecording?.delete()
        activeRecording = null
        super.onCleared()
    }

    @Serializable
    private data class PersistedCapture(
        val step: String,
        val mode: String,
        val text: String,
        val transcript: String,
        val transcriptAnalysisId: String?,
        val captureSessionId: String?,
        val analysisId: String?,
        val source: String,
        val items: List<CaptureReviewItem>,
        val savedCount: Int,
    )

    private companion object {
        const val KEY_STATE = "capture.state"
        const val KEY_OWNER = "capture.owner"
        const val KEY_MODE_ARG = "mode"
        const val MAX_TEXT_LENGTH = 4_000
        const val RECORD_SAMPLE_MILLIS = 100L
        const val MAX_RECORDING_MILLIS = 60_000L
        const val SPEECH_AMPLITUDE_THRESHOLD = 80

        /** The add form's method bar opens capture as Voice (`mode=speak`) or Sentence (`mode=type`). */
        fun requestedMode(handle: SavedStateHandle): CaptureMode =
            if (handle.get<String>(KEY_MODE_ARG) == "speak") CaptureMode.SPEAK else CaptureMode.TYPE

        fun restoreState(handle: SavedStateHandle, owner: String?): CaptureUiState {
            // A saved capture belongs to the account that started it; another account starts clean.
            if (handle.get<String>(KEY_OWNER) != owner) {
                handle.remove<Any>(KEY_STATE)
                handle[KEY_OWNER] = owner
                return CaptureUiState(mode = requestedMode(handle))
            }
            val data = runCatching {
                handle.get<String>(KEY_STATE)?.let { SpenditosJson.decodeFromString(PersistedCapture.serializer(), it) }
            }.getOrNull() ?: return CaptureUiState(mode = requestedMode(handle))
            val step = when (data.step) {
                "CHECK_TRANSCRIPT" -> CaptureStep.CheckTranscript
                "REVIEW" -> CaptureStep.Review
                "DONE" -> CaptureStep.Done
                else -> CaptureStep.Input
            }
            return CaptureUiState(
                mode = runCatching { CaptureMode.valueOf(data.mode) }.getOrDefault(CaptureMode.TYPE),
                step = step,
                text = data.text,
                transcript = data.transcript,
                transcriptAnalysisId = data.transcriptAnalysisId,
                captureSessionId = data.captureSessionId,
                analysisId = data.analysisId,
                source = data.source,
                items = data.items,
                categoriesLoading = true,
                savedCount = data.savedCount,
            )
        }
    }
}

internal fun parseCaptureAmount(text: String): BigDecimal? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    return normalized.toBigDecimalOrNull()?.takeIf { it.signum() > 0 && it.scale() <= 2 }
}
