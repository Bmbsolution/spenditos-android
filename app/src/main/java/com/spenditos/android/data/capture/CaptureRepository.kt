package com.spenditos.android.data.capture

import android.content.Context
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.CaptureAnalysisItem
import com.spenditos.android.data.model.CaptureAnalysisStatus
import com.spenditos.android.data.model.CaptureAnalyzeRequest
import com.spenditos.android.data.model.CaptureBulkRequest
import com.spenditos.android.data.model.CaptureBulkTransaction
import com.spenditos.android.data.model.CaptureUploadRequest
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.data.receipt.ReceiptUploadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

data class CaptureContext(val currency: String, val locale: String = Locale.getDefault().toLanguageTag())

data class CaptureTranscript(val text: String, val analysisId: String, val captureSessionId: String?)

data class CaptureSaveItem(
    val amount: Double,
    val date: LocalDate,
    val categoryId: String?,
    val description: String,
    val idempotencyKeyId: String,
)

data class CaptureSaveResult(
    val savedIds: Set<String>,
    val failures: Map<String, String>,
    val savedCount: Int,
    val unconfirmedIds: Set<String> = emptySet(),
)

interface CaptureActions {
    suspend fun transcribeAudio(recording: File, context: CaptureContext, hasSpeech: Boolean): CaptureTranscript
    suspend fun parseText(text: String, context: CaptureContext, inputType: String, parentAnalysisId: String?): CaptureParseResult
    suspend fun save(items: List<CaptureSaveItem>, source: String, analysisId: String?): CaptureSaveResult
    suspend fun closeCaptureSession(analysisId: String, outcome: String)
}

data class CaptureParseResult(
    val items: List<CaptureAnalysisItem>,
    val analysisId: String,
    val transcript: String?,
    val parentAnalysisId: String?,
    val captureSessionId: String?,
)

interface CaptureTransactionRefresh {
    suspend fun refresh(start: LocalDate, end: LocalDate)
}

sealed class CaptureFailure : Exception() {
    data object InvalidUploadUrl : CaptureFailure()
    data class UploadFailed(val status: Int) : CaptureFailure()
    data object RecordingUnavailable : CaptureFailure()
    data object NoSpeechDetected : CaptureFailure()
    data object AnalysisTimeout : CaptureFailure()
    data object EmptyTranscript : CaptureFailure()
    data class AnalysisFailed(val userMessage: String) : CaptureFailure()
}

/** Upload, async analysis, and idempotent reviewed writes for capture. */
@Singleton
class CaptureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: CaptureApi,
    @ReceiptUploadClient private val uploadClient: OkHttpClient,
    @ReceiptIoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val invalidations: FinanceInvalidationBus,
    private val transactionRefresh: CaptureTransactionRefresh,
) : CaptureActions {

    override suspend fun transcribeAudio(recording: File, context: CaptureContext, hasSpeech: Boolean): CaptureTranscript {
        try {
            if (!recording.exists() || recording.length() == 0L) throw CaptureFailure.RecordingUnavailable
            if (!hasSpeech) throw CaptureFailure.NoSpeechDetected
            val traceId = UUID.randomUUID().toString().replace("-", "")
            val upload = apiCall { api.uploadUrl(traceId, CaptureUploadRequest(AUDIO_MIME)) }
            uploadToS3(upload.uploadUrl, upload.fields, recording, AUDIO_MIME)
            val started = apiCall {
                api.analyze(
                    traceId,
                    CaptureAnalyzeRequest(
                        documentKey = upload.documentKey,
                        mode = "transcribe",
                        inputType = "voice",
                        mimeType = AUDIO_MIME,
                        locale = context.locale,
                        referenceDate = LocalDate.now().toString(),
                        userCurrency = context.currency,
                    ),
                )
            }
            val status = poll(traceId, started.analysisId)
            val transcript = status.transcript?.trim().orEmpty()
            if (transcript.isEmpty()) throw CaptureFailure.EmptyTranscript
            return CaptureTranscript(transcript, started.analysisId, status.captureSessionId)
        } finally {
            withContext(NonCancellable + ioDispatcher) { recording.delete() }
        }
    }

    override suspend fun parseText(
        text: String,
        context: CaptureContext,
        inputType: String,
        parentAnalysisId: String?,
    ): CaptureParseResult {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Text is required" }
        require(inputType == "typed" || inputType == "voice")
        val created = AtomicReference<File?>(null)
        try {
            val temp = withContext(ioDispatcher) {
                File.createTempFile("capture-text-", ".txt", this@CaptureRepository.context.cacheDir).also { file ->
                    created.set(file)
                    file.writeText(trimmed, Charsets.UTF_8)
                }
            }
            val traceId = UUID.randomUUID().toString().replace("-", "")
            val upload = apiCall { api.uploadUrl(traceId, CaptureUploadRequest(TEXT_MIME)) }
            uploadToS3(upload.uploadUrl, upload.fields, temp, TEXT_MIME)
            val started = apiCall {
                api.analyze(
                    traceId,
                    CaptureAnalyzeRequest(
                        documentKey = upload.documentKey,
                        mode = "audio-transcript",
                        transcript = trimmed,
                        mimeType = TEXT_MIME,
                        // The current backend only accepts parentAnalysisId for its typed
                        // transcript path. A voice transcript still keeps source="voice"
                        // on the eventual bulk write and links to its transcription record.
                        inputType = if (parentAnalysisId != null) "typed" else inputType,
                        locale = context.locale,
                        referenceDate = LocalDate.now().toString(),
                        userCurrency = context.currency,
                        parentAnalysisId = parentAnalysisId,
                    ),
                )
            }
            val status = poll(traceId, started.analysisId)
            val items = status.items.ifEmpty { listOfNotNull(status.result) }
            return CaptureParseResult(items, started.analysisId, status.transcript ?: trimmed, status.parentAnalysisId, status.captureSessionId)
        } finally {
            withContext(NonCancellable + ioDispatcher) { created.get()?.delete() }
        }
    }

    override suspend fun save(items: List<CaptureSaveItem>, source: String, analysisId: String?): CaptureSaveResult {
        require(items.isNotEmpty()) { "At least one transaction is required" }
        require(source == "voice" || source == "text")
        val response = apiCall {
            api.createBulk(
                CaptureBulkRequest(
                    transactions = items.map { item ->
                        CaptureBulkTransaction(
                            description = item.description.trim(),
                            amount = item.amount,
                            date = item.date.toString(),
                            categoryId = item.categoryId,
                            mood = TransactionMood.NECESSARY,
                            idempotencyKeyId = item.idempotencyKeyId,
                        )
                    },
                    source = source,
                    analysisId = analysisId,
                ),
            )
        }

        val failedByIndex = response.failed.associate { it.index to it.reason }
        val availableIndices = items.indices.filterNot { it in failedByIndex }
        val successfulResultIndices = response.results.mapNotNull { it.index }.filter { it in availableIndices }.toSet()
        val indicesWithoutExplicitResults = availableIndices.filterNot { it in successfulResultIndices }
        val unindexedResultCount = response.results.count { it.index == null }
        val inferredResultIndices = indicesWithoutExplicitResults.take(unindexedResultCount).toSet()
        val resultIndices = successfulResultIndices + inferredResultIndices
        val duplicateCount = response.duplicates.coerceAtMost((availableIndices - resultIndices).size)
        val duplicateIndices = (availableIndices - resultIndices.toList().toSet()).take(duplicateCount).toSet()
        val savedIndices = resultIndices + duplicateIndices
        val unconfirmedIndices = availableIndices - savedIndices
        val failures = linkedMapOf<String, String>()
        failedByIndex.forEach { (index, reason) -> items.getOrNull(index)?.let { failures[it.idempotencyKeyId] = reason } }
        unconfirmedIndices.forEach { index ->
            failures[items[index].idempotencyKeyId] = "Couldn't confirm this transaction. Try again."
        }
        val savedCount = savedIndices.size
        if (savedCount > 0) {
            invalidations.invalidate(Aggregate.TRANSACTION)
            val dates = items.map { it.date }
            try {
                transactionRefresh.refresh(dates.minOrNull()!!, dates.maxOrNull()!!)
            } catch (_: ApiError) {
                // The bulk write is already committed; refresh is best-effort like TransactionRepository writes.
            }
        }
        return CaptureSaveResult(
            savedIds = savedIndices.map { items[it].idempotencyKeyId }.toSet(),
            failures = failures,
            savedCount = savedCount,
            unconfirmedIds = unconfirmedIndices.map { items[it].idempotencyKeyId }.toSet(),
        )
    }

    override suspend fun closeCaptureSession(analysisId: String, outcome: String) {
        if (analysisId.isBlank()) return
        require(outcome == "restarted" || outcome == "abandoned")
        val traceId = UUID.randomUUID().toString().replace("-", "")
        // Closing an abandoned capture is an audit convenience, never a blocker.
        try {
            apiCall {
                api.closeSession(
                    traceId,
                    CaptureAnalyzeRequest(action = "close-session", analysisId = analysisId, outcome = outcome),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best-effort server lifecycle update.
        }
    }

    private suspend fun poll(traceId: String, analysisId: String): CaptureAnalysisStatus {
        val deadline = TimeSource.Monotonic.markNow() + POLL_WINDOW
        repeat(POLL_COUNT) { index ->
            if (index > 0) delay(POLL_INTERVAL_MILLIS)
            if (deadline.hasPassedNow()) throw CaptureFailure.AnalysisTimeout
            val status = apiCall { api.analysisStatus(traceId, analysisId) }
            when (status.status) {
                "success", "completed" -> return status
                "failed" -> throw CaptureFailure.AnalysisFailed(plainError(status.errorDetail))
            }
        }
        throw CaptureFailure.AnalysisTimeout
    }

    private fun plainError(message: String?): String {
        val value = message?.trim().orEmpty()
        if (value.isEmpty() || value.length > 180 || '\n' in value || '\r' in value || value.startsWith('{') || value.startsWith('[')) return GENERIC_ANALYSIS_ERROR
        if (listOf("exception", "stack", "traceback", "arn:", " at ").any { value.contains(it, ignoreCase = true) }) return GENERIC_ANALYSIS_ERROR
        return value
    }

    private suspend fun uploadToS3(url: String, fields: Map<String, String>, file: File, mimeType: String) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart("file", "upload", file.asRequestBody(mimeType.toMediaType()))
        }.build()
        val request = try {
            Request.Builder().url(url).post(body).build()
        } catch (_: IllegalArgumentException) {
            throw CaptureFailure.InvalidUploadUrl
        }
        try {
            withContext(ioDispatcher) {
                uploadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw CaptureFailure.UploadFailed(response.code)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: CaptureFailure) {
            throw error
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
    }

    private companion object {
        const val AUDIO_MIME = "audio/mp4"
        const val TEXT_MIME = "text/plain"
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val POLL_COUNT = 60
        val POLL_WINDOW = 120.seconds
        const val GENERIC_ANALYSIS_ERROR = "We couldn't finish that capture. Try again."
    }
}
