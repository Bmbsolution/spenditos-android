package com.spenditos.android.feature.receipt

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.entitlement.ProAccess
import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.quota.QuotaCopy
import com.spenditos.android.data.quota.QuotaSource
import com.spenditos.android.data.receipt.ReceiptScanFailure
import com.spenditos.android.data.receipt.ReceiptScanStage
import com.spenditos.android.data.receipt.ReceiptScanner
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import javax.inject.Inject

enum class ReceiptScanStep { CHOOSE, PREPARING, UPLOADING, ANALYZING, DONE, FAILURE, QUOTA }

data class ReceiptScanUiState(
    val step: ReceiptScanStep = ReceiptScanStep.CHOOSE,
    val error: String? = null,
    val offline: Boolean = false,
    /** The processed image (already resized, owned by the scan job) shown behind the progress card. */
    val backdropPath: String? = null,
    /** Scan credits left this month (the `scan_credits` bucket); null for Pro, unmetered, or not known yet. */
    val scanCreditsLeft: Int? = null,
)

sealed interface ReceiptScanEvent {
    data class Result(val prefill: AddTransactionPrefill) : ReceiptScanEvent
}

/** Maps server analysis values to the add form without saving a transaction. */
fun ReceiptResult.toPrefill() = AddTransactionPrefill(
    amount = amount,
    description = merchantName ?: description,
    date = date,
    categoryId = categoryId,
    categoryHint = categoryHint,
    source = "Receipt scan",
)

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val scanner: ReceiptScanner,
    private val imageProcessor: ReceiptImageProcessing,
    private val savedStateHandle: SavedStateHandle,
    @ReceiptIoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val proAccess: ProAccess,
    private val quota: QuotaSource,
) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptScanUiState())
    val state: StateFlow<ReceiptScanUiState> = _state.asStateFlow()

    private val _events = Channel<ReceiptScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { checkScanCredits() }
    }

    /**
     * Free users with no scan credits left see the exhausted screen right away instead of finding out
     * after an upload (iOS ScanReceiptView `.task`). The server stays the source of truth: an unknown
     * (offline) count never blocks.
     */
    private suspend fun checkScanCredits() {
        if (proAccess.effectiveIsPro) return
        val remaining = quota.remainingScanCredits()
        val left = remaining.takeIf { it != QuotaOverview.UNLIMITED }
        if (_state.value.step != ReceiptScanStep.CHOOSE) return
        _state.value = if (left != null && left <= 0) {
            ReceiptScanUiState(step = ReceiptScanStep.QUOTA, error = QuotaCopy.SCAN_EXHAUSTED, scanCreditsLeft = 0)
        } else {
            _state.value.copy(scanCreditsLeft = left)
        }
    }

    private var scanJob: Job? = null
    private var activeSource: Uri? = null
    private var activeCameraSource = false
    private var activeTempFile: File? = null

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
        startScan(Uri.parse(uriString), cameraSource = true)
    }

    fun onLibraryPhotoSelected(uri: Uri) = startScan(uri, cameraSource = false)

    fun retry() {
        clearActive()
        _state.value = ReceiptScanUiState()
    }

    fun cancel() {
        scanJob?.cancel()
        clearActive()
        val pendingCamera = savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)
        if (pendingCamera != null) imageProcessor.deleteCameraFile(pendingCamera)
        savedStateHandle[KEY_PENDING_CAMERA_URI] = null
        _state.value = ReceiptScanUiState()
    }

    fun imagePreparationFailed() {
        savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)?.let(imageProcessor::deleteCameraFile)
        savedStateHandle[KEY_PENDING_CAMERA_URI] = null
        _state.value = ReceiptScanUiState(step = ReceiptScanStep.FAILURE, error = MANUAL_FALLBACK)
    }

    private fun startScan(uri: Uri, cameraSource: Boolean) {
        scanJob?.cancel()
        activeSource = uri
        activeCameraSource = cameraSource
        _state.value = ReceiptScanUiState(step = ReceiptScanStep.PREPARING)
        scanJob = viewModelScope.launch {
            var temp: File? = null
            var cameraSourceDeleted = false
            try {
                val outputFile = imageProcessor.createTempFile()
                temp = outputFile
                activeTempFile = outputFile
                withContext(ioDispatcher) { imageProcessor.process(uri, outputFile) }
                // The backdrop decodes this file while the scan uses it; the lifecycle below is unchanged.
                _state.value = _state.value.copy(backdropPath = outputFile.absolutePath)
                if (cameraSource) {
                    imageProcessor.deleteCameraFile(uri.toString())
                    cameraSourceDeleted = true
                    activeCameraSource = false
                    savedStateHandle[KEY_PENDING_CAMERA_URI] = null
                }
                val result = scanner.scan(outputFile) { stage ->
                    _state.value = _state.value.copy(
                        step = if (stage == ReceiptScanStage.UPLOADING) ReceiptScanStep.UPLOADING else ReceiptScanStep.ANALYZING,
                        error = null,
                        offline = false,
                    )
                }
                _state.value = ReceiptScanUiState(step = ReceiptScanStep.DONE)
                // One scan credit was spent: the next read of the quota hits the server.
                quota.invalidate()
                _events.send(ReceiptScanEvent.Result(result.toPrefill()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Class name only: no image, URL or server text reaches the log.
                android.util.Log.w("ReceiptScan", "scan failed: ${error::class.java.simpleName}")
                val quotaUsed = error is ApiError.Server && error.isQuotaExhausted
                val offline = error is ApiError.Network
                _state.value = ReceiptScanUiState(
                    step = if (quotaUsed) ReceiptScanStep.QUOTA else ReceiptScanStep.FAILURE,
                    error = when {
                        quotaUsed -> QuotaCopy.SCAN_EXHAUSTED
                        offline -> MANUAL_FALLBACK
                        else -> MANUAL_FALLBACK
                    },
                    offline = offline,
                    scanCreditsLeft = if (quotaUsed) 0 else null,
                )
            } finally {
                temp?.delete()
                if (activeTempFile === temp) activeTempFile = null
                if (cameraSource && !cameraSourceDeleted) imageProcessor.deleteCameraFile(uri.toString())
                if (activeSource == uri) activeSource = null
                if (activeCameraSource == cameraSource) activeCameraSource = false
                if (cameraSource) savedStateHandle[KEY_PENDING_CAMERA_URI] = null
            }
        }
    }

    private fun clearActive() {
        val source = activeSource
        if (activeCameraSource && source != null) imageProcessor.deleteCameraFile(source.toString())
        activeTempFile?.delete()
        activeTempFile = null
        activeSource = null
        activeCameraSource = false
        scanJob = null
    }

    private companion object {
        const val KEY_PENDING_CAMERA_URI = "receipt.pendingCameraUri"
        const val MANUAL_FALLBACK = "Couldn't read that receipt. You can enter it yourself."
    }
}
