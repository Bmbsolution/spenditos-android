package com.spenditos.android.data.statements

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementAnalyzeRequest
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.data.model.StatementImportResult
import com.spenditos.android.data.model.StatementImportTransaction
import com.spenditos.android.data.model.StatementImportRequest
import com.spenditos.android.data.model.StatementPreviewRequest
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.StatementUploadUrlRequest
import com.spenditos.android.data.receipt.ReceiptUploadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class StatementAnalysisDocument(
    val filename: String,
    val documentKey: String,
    val analysis: StatementAnalysisResult,
)

interface StatementImporter {
    /** Signed-in user, so a saved import flow is never resumed under another account. */
    val currentUserId: String?
    suspend fun isReadable(uri: Uri): Boolean
    suspend fun analyze(uri: Uri): StatementAnalysisDocument
    suspend fun preview(filename: String, documentKey: String, mapping: StatementColumnMapping): StatementPreviewResult
    suspend fun importTransactions(transactions: List<StatementImportTransaction>, idempotencyKey: String): StatementImportResult
}

class StatementFileTooLargeException : Exception()
class StatementFileUnavailableException(cause: Throwable? = null) : Exception(cause)

@Singleton
class StatementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: StatementApi,
    private val session: SessionManager,
    @ReceiptUploadClient private val uploadClient: OkHttpClient,
    @StatementIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StatementImporter {
    init {
        File(context.cacheDir, "statements").listFiles()
            ?.filter { it.isFile && it.name.startsWith("statement-") }
            ?.forEach { it.delete() }
    }

    override val currentUserId: String? get() = session.currentUserId

    override suspend fun analyze(uri: Uri): StatementAnalysisDocument {
        val filename = withContext(ioDispatcher) { displayName(uri) }
        val mimeType = if (filename.substringAfterLast('.', "").equals("csv", ignoreCase = true)) CSV_MIME else XLSX_MIME
        // The holder is set inside the copy, so a cancellation racing the copy's return still deletes the file.
        val created = AtomicReference<File?>(null)
        try {
            val tempFile = copyToTempFile(uri, filename, created)
            val upload = apiCall {
                api.uploadUrl(StatementUploadUrlRequest(mimeType = mimeType, documentType = "bank_statement"))
            }
            uploadToS3(upload.uploadUrl, upload.fields, tempFile, mimeType)
            val analysis = apiCall { api.analyze(StatementAnalyzeRequest(filename, upload.documentKey)) }
            return StatementAnalysisDocument(filename, upload.documentKey, analysis)
        } finally {
            withContext(NonCancellable + ioDispatcher) { created.get()?.delete() }
        }
    }

    override suspend fun isReadable(uri: Uri): Boolean = withContext(ioDispatcher) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { it.read(); true }
        } catch (_: SecurityException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override suspend fun preview(
        filename: String,
        documentKey: String,
        mapping: StatementColumnMapping,
    ): StatementPreviewResult = apiCall {
        api.preview(StatementPreviewRequest(filename, documentKey, mapping))
    }

    override suspend fun importTransactions(
        transactions: List<StatementImportTransaction>,
        idempotencyKey: String,
    ): StatementImportResult = apiCall {
        api.importSpreadsheet(StatementImportRequest(transactions), idempotencyKey)
    }

    private fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameColumn)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        } catch (error: SecurityException) {
            throw StatementFileUnavailableException(error)
        } catch (error: IOException) {
            throw StatementFileUnavailableException(error)
        }
        val fallback = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
        if (fallback.isBlank()) throw StatementFileUnavailableException()
        return fallback
    }

    private suspend fun copyToTempFile(uri: Uri, filename: String, created: AtomicReference<File?>): File = withContext(ioDispatcher) {
        val input = try {
            context.contentResolver.openInputStream(uri) ?: throw StatementFileUnavailableException()
        } catch (error: SecurityException) {
            throw StatementFileUnavailableException(error)
        } catch (error: IOException) {
            throw StatementFileUnavailableException(error)
        }
        val directory = File(context.cacheDir, "statements")
        if (!directory.exists() && !directory.mkdirs()) {
            input.close()
            throw StatementFileUnavailableException()
        }
        val suffix = if (filename.substringAfterLast('.', "").equals("csv", ignoreCase = true)) "csv" else "xlsx"
        val tempFile = File(directory, "statement-${UUID.randomUUID()}.$suffix")
        created.set(tempFile)
        var complete = false
        try {
            input.use { source ->
                tempFile.outputStream().buffered().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_STATEMENT_FILE_BYTES) throw StatementFileTooLargeException()
                        target.write(buffer, 0, read)
                    }
                }
            }
            complete = true
            tempFile
        } catch (error: StatementFileTooLargeException) {
            throw error
        } catch (error: SecurityException) {
            throw StatementFileUnavailableException(error)
        } catch (error: IOException) {
            throw StatementFileUnavailableException(error)
        } finally {
            if (!complete) tempFile.delete()
        }
    }

    private suspend fun uploadToS3(url: String, fields: Map<String, String>, file: File, mimeType: String) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart("file", "upload", file.asRequestBody(mimeType.toMediaType()))
            }
            .build()
        val request = try {
            Request.Builder().url(url).post(body).build()
        } catch (_: IllegalArgumentException) {
            throw ApiError.Server(400, "Invalid upload address")
        }
        try {
            withContext(ioDispatcher) {
                uploadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiError.Server(response.code, "Statement upload failed")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiError) {
            throw error
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
    }

    private companion object {
        const val CSV_MIME = "text/csv"
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val MAX_STATEMENT_FILE_BYTES = 25L * 1024 * 1024
    }
}
