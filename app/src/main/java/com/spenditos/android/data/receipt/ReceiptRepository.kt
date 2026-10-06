package com.spenditos.android.data.receipt

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.model.ReceiptAnalyzeRequest
import com.spenditos.android.data.model.ReceiptUploadRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

enum class ReceiptScanStage { UPLOADING, ANALYZING, POLLING }

interface ReceiptScanner {
    suspend fun scan(image: File, onStage: (ReceiptScanStage) -> Unit = {}): ReceiptResult
}

sealed class ReceiptScanFailure : Exception() {
    data object InvalidUploadUrl : ReceiptScanFailure()
    data class UploadFailed(val status: Int) : ReceiptScanFailure()
    data object AnalysisFailed : ReceiptScanFailure()
    data object AnalysisTimeout : ReceiptScanFailure()
}

@Singleton
class ReceiptRepository @Inject constructor(
    private val api: ReceiptApi,
    @ReceiptUploadClient private val uploadClient: OkHttpClient,
) : ReceiptScanner {
    override suspend fun scan(image: File, onStage: (ReceiptScanStage) -> Unit): ReceiptResult {
        try {
            val traceId = UUID.randomUUID().toString().replace("-", "")
            onStage(ReceiptScanStage.UPLOADING)
            val upload = apiCall {
                api.uploadUrl(traceId, ReceiptUploadRequest(mimeType = JPEG_MIME, documentType = "receipt"))
            }
            try {
                uploadToS3(upload.uploadUrl, upload.fields, image)
            } finally {
                image.delete()
            }

            onStage(ReceiptScanStage.ANALYZING)
            val analysis = apiCall { api.analyzeImage(traceId, ReceiptAnalyzeRequest(upload.documentKey)) }

            onStage(ReceiptScanStage.POLLING)
            // iOS polls every 2 s while less than 30 s have passed. The poll COUNT bounds the loop
            // (deterministic, testable under virtual time); the monotonic wall-clock deadline is a
            // backstop for slow responses in production.
            val deadline = TimeSource.Monotonic.markNow() + POLL_WINDOW
            repeat(POLL_COUNT) { index ->
                if (index > 0) delay(POLL_INTERVAL_MILLIS)
                if (deadline.hasPassedNow()) throw ReceiptScanFailure.AnalysisTimeout
                val status = apiCall { api.analysisStatus(traceId, analysis.analysisId) }
                when (status.status) {
                    "success" -> return status.result ?: throw ReceiptScanFailure.AnalysisFailed
                    "failed" -> throw ReceiptScanFailure.AnalysisFailed
                }
            }
            throw ReceiptScanFailure.AnalysisTimeout
        } finally {
            image.delete()
        }
    }

    private suspend fun uploadToS3(url: String, fields: Map<String, String>, image: File) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart("file", "upload", image.asRequestBody(JPEG_MIME.toMediaType()))
            }
            .build()
        val request = try {
            Request.Builder().url(url).post(body).build()
        } catch (_: IllegalArgumentException) {
            throw ReceiptScanFailure.InvalidUploadUrl
        }

        try {
            withContext(Dispatchers.IO) {
                uploadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ReceiptScanFailure.UploadFailed(response.code)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ReceiptScanFailure) {
            throw error
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
    }

    private companion object {
        const val JPEG_MIME = "image/jpeg"
        const val POLL_INTERVAL_MILLIS = 2_000L
        const val POLL_COUNT = 15
        val POLL_WINDOW = 30.seconds
    }
}
