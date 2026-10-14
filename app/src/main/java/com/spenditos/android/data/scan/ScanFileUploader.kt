package com.spenditos.android.data.scan

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.ReceiptUploadData
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.data.receipt.ReceiptUploadClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Sends one prepared file to the presigned S3 POST policy returned by `POST /transactions/upload-url`. */
interface ScanFileUploader {
    suspend fun upload(target: ReceiptUploadData, file: File, mimeType: String)
}

/**
 * The receipt upload client: a separate OkHttp client with no auth header or interceptors, so the bearer token never
 * reaches S3 and the presigned URL is never logged. Same multipart rules as the receipt flow (all `fields` first, then `file`).
 */
@Singleton
class OkHttpScanFileUploader @Inject constructor(
    @ReceiptUploadClient private val client: OkHttpClient,
    @ReceiptIoDispatcher private val io: CoroutineDispatcher,
) : ScanFileUploader {
    override suspend fun upload(target: ReceiptUploadData, file: File, mimeType: String) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                target.fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart("file", "upload", file.asRequestBody(mimeType.toMediaType()))
            }
            .build()
        val request = try {
            Request.Builder().url(target.uploadUrl).post(body).build()
        } catch (_: IllegalArgumentException) {
            throw ScanFailure.InvalidUploadUrl
        }
        try {
            withContext(io) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ScanFailure.UploadFailed(response.code)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ScanFailure) {
            throw error
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
    }
}
