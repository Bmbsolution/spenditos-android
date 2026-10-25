package com.spenditos.android.feature.receipt

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanProgress
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.data.scan.ClipboardImages
import com.spenditos.android.data.scan.ScanActions
import com.spenditos.android.data.scan.ScanContext
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanCredits
import com.spenditos.android.data.scan.ScanCreditsProvider
import com.spenditos.android.data.scan.ScanFailure
import com.spenditos.android.data.scan.ScanInput
import com.spenditos.android.data.scan.ScanInputFactory
import com.spenditos.android.data.scan.ScanResolution
import com.spenditos.android.data.scan.ScanRules
import com.spenditos.android.data.scan.resolution
import com.spenditos.android.feature.scan.toPrefill
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** iOS `ScanViewState`: the camera (with Upload), the reading screen, and the two dead ends. */
enum class ReceiptScanStep { CHOOSE, PREPARING, READING, FAILURE, QUOTA, DONE }

/** What the reading screen shows; filled in as the upload and the server-side scan progress (iOS `ReadingState`). */
data class ScanReadingState(
    val label: String = "",
    val unit: String = "Image",
    val uploaded: Int = 0,
    val uploadTotal: Int? = null,
    val scanId: String? = null,
    val total: Int = 0,
    val done: Int = 0,
    /** Files can be big: offer to leave. Photos only when there are many. */
    val offersBackground: Boolean = false,
) {
    /** Upload progress until the scan exists, then documents read by the server. */
    val fraction: Float?
        get() = if (scanId == null) {
            uploadTotal?.takeIf { it > 0 }?.let { uploaded.toFloat() / it }
        } else {
            total.takeIf { it > 0 }?.let { minOf(done, it).toFloat() / it }
        }

    val title: String
        get() = label.trim().let { if (it.isEmpty()) "Reading your scan…" else "Reading $it…" }

    val progressText: String
        get() {
            if (scanId == null) {
                val count = uploadTotal
                return if (count != null && count > 0) "Uploading ${minOf(uploaded + 1, count)} of $count" else "Uploading…"
            }
            if (total <= 1) return "Matching what you already have"
            return "$unit ${minOf(done + 1, total)} of $total · matching what you already have"
        }
}

data class ReceiptScanUiState(
    val step: ReceiptScanStep = ReceiptScanStep.CHOOSE,
    val error: String? = null,
    val offline: Boolean = false,
    val reading: ScanReadingState = ScanReadingState(),
    /** A short message over the camera ("You have 3 scans left — pick up to 3"). */
    val toast: String? = null,
)

sealed interface ReceiptScanEvent {
    /** One purchase: the add form opens prefilled. */
    data class Result(val prefill: AddTransactionPrefill) : ReceiptScanEvent

    /** Anything else: the month-grouped review list of this scan. */
    data class OpenReview(val scanId: String) : ReceiptScanEvent

    /** The screen is done (the scan was left to finish in the background). */
    data object Close : ReceiptScanEvent
}

/** Maps a scanned receipt to the add form without saving a transaction. */
fun ReceiptResult.toPrefill() = AddTransactionPrefill(
    amount = amount,
    description = merchantName ?: description,
    date = date,
    categoryId = categoryId,
    categoryHint = categoryHint,
    source = "Receipt scan",
)

/**
 * The Scan screen's state (iOS `ScanReceiptView`). Camera photos, library photos and files all go through the
 * Scan engine, which decides whether it is one receipt or a list: the user never picks a "mode".
 */
@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val scans: ScanActions,
    private val coordinator: ScanCoordinator,
    private val inputs: ScanInputFactory,
    private val clipboard: ClipboardImages,
    private val imageProcessor: ReceiptImageProcessing,
    private val credits: ScanCreditsProvider,
    private val dataSource: CaptureDataSource,
    private val savedStateHandle: SavedStateHandle,
    @ReceiptIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptScanUiState())
    val state: StateFlow<ReceiptScanUiState> = _state.asStateFlow()

    private val _events = Channel<ReceiptScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var scanJob: Job? = null

    /** The scan was handed to Home (left in the background or finished); nothing more to do when this screen goes away. */
    private var handedOff = false

    init {
        coordinator.start()
        // iOS: a user with no scan credits left sees "Monthly Limit Reached" as soon as the screen opens.
        viewModelScope.launch {
            val remaining = credits.remainingScanCredits()
            if (remaining != null && remaining <= 0 && _state.value.step == ReceiptScanStep.CHOOSE) {
                _state.value = ReceiptScanUiState(step = ReceiptScanStep.QUOTA, error = QUOTA_MESSAGE)
            }
        }
    }

    // region Camera

    fun setPendingCameraUri(uri: String) {
        savedStateHandle[KEY_PENDING_CAMERA_URI] = uri
    }

    fun onCameraCaptureResult(captured: Boolean) {
        val uriString = savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI) ?: return
        if (!captured) {
            imageProcessor.deleteCameraFile(uriString)
            savedStateHandle[KEY_PENDING_CAMERA_URI] = null
            return
        }
        startCameraScan(Uri.parse(uriString))
    }

    fun imagePreparationFailed() {
        savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)?.let(imageProcessor::deleteCameraFile)
        savedStateHandle[KEY_PENDING_CAMERA_URI] = null
        _state.value = ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = MANUAL_FALLBACK)
    }

    /** The camera photo is resized and oriented first (EXIF), then read by the engine; the original is deleted at once. */
    private fun startCameraScan(uri: Uri) {
        scanJob?.cancel()
        handedOff = false
        _state.value = ReceiptScanUiState(step = ReceiptScanStep.PREPARING)
        scanJob = viewModelScope.launch {
            var processed: File? = null
            try {
                val output = imageProcessor.createTempFile()
                processed = output
                withContext(ioDispatcher) { imageProcessor.process(uri, output, SCAN_MAX_LONG_SIDE) }
                imageProcessor.deleteCameraFile(uri.toString())
                savedStateHandle[KEY_PENDING_CAMERA_URI] = null
                val input = inputs.camera(output)
                processed = null // the engine owns (and deletes) the file from here
                beginReading(listOf(input))
                runScan(listOf(input))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showFailure(error)
            } finally {
                processed?.delete()
                imageProcessor.deleteCameraFile(uri.toString())
                savedStateHandle[KEY_PENDING_CAMERA_URI] = null
            }
        }
    }

    // endregion

    // region Upload (Photo Library | Files)

    /** The Photo Picker returned [uris] in the order the user picked them. */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startChecked(uris.take(ScanRules.MAX_DOCUMENTS_PER_UPLOAD).map(inputs::photo))
    }

    /** Whether the Upload menu should offer "Paste screenshot": only when the clipboard holds an image. */
    fun clipboardHasImage(): Boolean = clipboard.hasImage()

    /**
     * "Paste screenshot" (#394): the images on the clipboard are read exactly like library photos picked in that order
     * (same limits, credit check, errors and review). An empty clipboard only shows a short message.
     * Counts only are logged, never the clipboard or the image.
     */
    fun onPasteScreenshot() {
        val uris = clipboard.imageUris()
        android.util.Log.i("ScanSheet", "paste: ${uris.size} image(s)")
        if (uris.isEmpty()) {
            _state.update { it.copy(toast = NO_CLIPBOARD_IMAGE) }
            return
        }
        onPhotosPicked(uris)
    }

    /** The document picker returned [uris]: PDF, CSV, Excel and images are read; anything else is explained. */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            // Names and types come from the document provider, which can be slow: off the main thread.
            val accepted = withContext(ioDispatcher) { uris.take(ScanRules.MAX_DOCUMENTS_PER_UPLOAD).mapNotNull(inputs::file) }
            if (accepted.isEmpty()) {
                _state.value = ReceiptScanUiState(
                    step = ReceiptScanStep.FAILURE,
                    error = ScanFailure.UnsupportedFile(uris.first().lastPathSegment?.takeIf { it.isNotBlank() }).userMessage,
                )
                return@launch
            }
            checkCreditsThenRead(accepted)
        }
    }

    private fun startChecked(picked: List<ScanInput>) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch { checkCreditsThenRead(picked) }
    }

    /** For a user with a known credit balance, warn before uploading when the selection costs more than they have left. */
    private suspend fun checkCreditsThenRead(picked: List<ScanInput>) {
        val remaining = credits.remainingScanCredits()
        if (remaining != null) {
            val message = ScanCredits.overLimitMessage(scans.estimateCredits(picked), remaining)
            if (message != null) {
                _state.update { it.copy(toast = message) }
                return
            }
        }
        beginReading(picked)
        runScan(picked)
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    // endregion

    // region Reading

    private fun beginReading(picked: List<ScanInput>) {
        handedOff = false
        _state.value = ReceiptScanUiState(
            step = ReceiptScanStep.READING,
            reading = ScanReadingState(
                label = ScanRules.sourceLabel(picked),
                unit = ScanRules.readingUnit(picked),
                offersBackground = picked.any { it.origin == com.spenditos.android.data.scan.ScanInputOrigin.FILES },
            ),
        )
    }

    private suspend fun runScan(picked: List<ScanInput>) {
        try {
            val context = ScanContext(
                currency = runCatching { dataSource.currency().first() }.getOrDefault(""),
                locale = Locale.getDefault().toLanguageTag(),
            )
            val submission = scans.submit(picked, context) { progress ->
                // Ignore late callbacks once the scan exists.
                _state.update { current ->
                    if (current.reading.scanId != null) current
                    else current.copy(reading = current.reading.copy(uploaded = progress.uploaded, uploadTotal = progress.total))
                }
            }
            credits.spent()
            currentCoroutineContext().ensureActive()
            _state.update { current ->
                current.copy(
                    reading = current.reading.copy(
                        scanId = submission.scanId,
                        total = submission.total,
                        label = submission.sourceLabel,
                        offersBackground = current.reading.offersBackground || submission.total > BACKGROUND_OFFER_AFTER,
                    ),
                )
            }

            val detail = scans.waitUntilReady(submission.scanId) { summary ->
                _state.update { current ->
                    current.copy(
                        reading = current.reading.copy(
                            done = summary.progress.done,
                            total = if (summary.progress.total > 0) summary.progress.total else current.reading.total,
                            label = summary.sourceLabel.ifEmpty { current.reading.label },
                        ),
                    )
                }
            }
            currentCoroutineContext().ensureActive()

            when (val resolution = detail.resolution()) {
                is ScanResolution.Receipt -> {
                    // One purchase: the existing prefilled form takes over, so the scan is done as far as the engine is concerned.
                    handedOff = true
                    coordinator.completeInBackground(resolution.scanId, ScanOutcome.SAVED)
                    _state.value = ReceiptScanUiState(step = ReceiptScanStep.DONE)
                    _events.send(ReceiptScanEvent.Result(resolution.item.toPrefill()))
                }
                is ScanResolution.Review -> {
                    handedOff = true
                    coordinator.handOff(resolution.detail)
                    _state.value = ReceiptScanUiState(step = ReceiptScanStep.DONE)
                    _events.send(ReceiptScanEvent.OpenReview(resolution.detail.scanId))
                }
            }
        } catch (error: CancellationException) {
            // Cancel / Notify me / leaving already moved the state on.
            throw error
        } catch (error: ScanFailure.Timeout) {
            // Still reading after a long wait: let it finish in the background.
            val reading = _state.value.reading
            val scanId = reading.scanId ?: return showFailure(error)
            handedOff = true
            coordinator.trackBackground(scanId, reading.label, ScanProgress(reading.done, reading.total))
            _events.send(ReceiptScanEvent.Close)
        } catch (error: Throwable) {
            showFailure(error)
        }
    }

    private fun showFailure(error: Throwable) {
        // Class name only: no file, amount, URL or server text reaches the log.
        android.util.Log.w("ScanSheet", "scan failed: ${error::class.java.simpleName}")
        _state.value = failureState(error)
    }

    /** Cancel while reading. A scan that already exists keeps running on the server, so it is hidden and discarded once it finishes. */
    fun cancelReading() {
        scanJob?.cancel()
        scanJob = null
        _state.value.reading.scanId?.let(coordinator::dismiss)
        savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)?.let(imageProcessor::deleteCameraFile)
        savedStateHandle[KEY_PENDING_CAMERA_URI] = null
        _state.value = ReceiptScanUiState()
    }

    /**
     * "Notify me when it's ready": stop waiting here and show the scan on Home, where it turns into
     * "Ready to review". (The server sends no Android push; Home learns of it from the realtime event and on return.)
     */
    fun leaveInBackground() {
        val reading = _state.value.reading
        val scanId = reading.scanId ?: return
        scanJob?.cancel()
        scanJob = null
        handedOff = true
        coordinator.trackBackground(scanId, reading.label, ScanProgress(reading.done, reading.total))
        viewModelScope.launch { _events.send(ReceiptScanEvent.Close) }
    }

    /** System Back: while reading it leaves the scan running (like swiping the iOS sheet away); everywhere else it closes. */
    fun onBack() {
        val current = _state.value
        if (current.step == ReceiptScanStep.READING && current.reading.scanId != null) {
            leaveInBackground()
        } else {
            cancel()
            viewModelScope.launch { _events.send(ReceiptScanEvent.Close) }
        }
    }

    /** Leaves the screen without starting or keeping anything. */
    fun cancel() {
        scanJob?.cancel()
        scanJob = null
        savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)?.let(imageProcessor::deleteCameraFile)
        savedStateHandle[KEY_PENDING_CAMERA_URI] = null
        _state.value = ReceiptScanUiState()
    }

    fun retry() {
        _state.value = ReceiptScanUiState()
    }

    override fun onCleared() {
        // Swiping the screen away mid-read is the same as "Notify me": the scan keeps running server-side and resurfaces on Home.
        val reading = _state.value.reading
        val scanId = reading.scanId
        if (_state.value.step == ReceiptScanStep.READING && scanId != null && !handedOff) {
            coordinator.trackBackground(scanId, reading.label, ScanProgress(reading.done, reading.total))
        }
        super.onCleared()
    }

    // endregion

    internal companion object {
        const val KEY_PENDING_CAMERA_URI = "receipt.pendingCameraUri"

        /** More documents than this and the reading screen offers to leave (iOS `total > 3`). */
        const val BACKGROUND_OFFER_AFTER = 3
        const val QUOTA_MESSAGE = "You've used all your free scan credits for this month."
        const val RATE_LIMIT_MESSAGE = "Too many attempts in a short time — wait a few seconds and try again."
        const val NO_CLIPBOARD_IMAGE = "There's no image on the clipboard."
        const val MANUAL_FALLBACK ="Couldn't read your scan. You can enter it yourself."

        /** What the failure screen says for [error]. A 403 `QuotaExhausted` is the friendly limit screen, never the raw code. */
        fun failureState(error: Throwable): ReceiptScanUiState = when {
            error is ApiError.Server && error.status == 403 && error.serverMessage.contains("quota", ignoreCase = true) ->
                ReceiptScanUiState(step = ReceiptScanStep.QUOTA, error = QUOTA_MESSAGE)
            error is ApiError.Server && error.status == 429 ->
                ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = RATE_LIMIT_MESSAGE)
            error is ApiError.Network -> ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = MANUAL_FALLBACK, offline = true)
            error is ScanFailure -> ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = error.userMessage)
            else -> ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = MANUAL_FALLBACK)
        }
    }
}
