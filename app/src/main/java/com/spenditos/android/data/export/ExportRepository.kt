package com.spenditos.android.data.export

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.AuthRequiredException
import com.spenditos.android.core.network.KeystoreUnavailableException
import com.spenditos.android.core.network.SpenditosJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExportIoDispatcher

/**
 * A "Download my data" file waiting for the user to pick where to save it. [file] is a private
 * temporary copy; [binding] is the account that asked for it, so it is never written out after the
 * signed-in account changed.
 */
class FullCopy internal constructor(
    internal val file: File,
    val suggestedName: String,
    internal val binding: SessionBinding,
)

/** The destination could not be written (revoked, full, or the picker's provider failed). */
class ExportSaveFailedException(cause: Throwable? = null) : Exception("Could not save the file", cause)

interface ExportActions {
    /** Downloads the CSV for [start]..[end] (inclusive) into the app's private export folder, replacing earlier exports. */
    suspend fun exportCsv(start: LocalDate, end: LocalDate): File

    /** Removes exported files (financial data must not linger in cache). */
    suspend fun deleteExports()

    /** `GET /user/export` into a private temporary file, ready for the user to choose a destination. */
    suspend fun prepareFullCopy(): FullCopy

    /**
     * Streams [copy] to the document the user picked and always removes the temporary file. When the
     * signed-in account changed since the download began, nothing (more) is written and the empty or
     * partial document is deleted ([SessionChangedException]).
     */
    suspend fun saveFullCopy(copy: FullCopy, target: Uri)

    /** The user dismissed the picker: drop the temporary file. */
    fun discardFullCopy(copy: FullCopy)
}

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ExportApi,
    private val session: SessionManager,
    @ExportIoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : ExportActions {
    private val directory get() = File(context.cacheDir, "exports")

    /** Never exposed through the FileProvider (not listed in file_paths.xml). */
    private val fullCopyDirectory get() = File(context.cacheDir, "full-copy")

    init {
        // Leftovers of a process that died after an export: never kept across launches.
        directory.listFiles()?.forEach { it.delete() }
        fullCopyDirectory.listFiles()?.forEach { it.delete() }
        // Statement import is gone (Scan takes statements): drop temp copies it may have left behind.
        File(context.cacheDir, "statements").deleteRecursively()
    }

    override suspend fun exportCsv(start: LocalDate, end: LocalDate): File {
        val binding = session.currentBinding ?: throw ApiError.Unauthorized
        val response = fetch { api.export(start.toString(), end.toString(), sessionBinding = binding) }
        val body = response.body() ?: throw ApiError.NoData
        val name = sanitizeFilename(
            response.headers()["Content-Disposition"]?.let(::filenameFrom),
            fallback = "spenditos-transactions-$start-to-$end.csv",
            extension = ".csv",
        )
        // A previous export may still be waiting for its share target to read it (the screen keeps it
        // until it is left); a new export replaces it rather than piling up.
        withContext(io) { directory.listFiles()?.forEach { it.delete() } }
        return download(body, binding, File(directory, name))
    }

    override suspend fun deleteExports() {
        withContext(NonCancellable + io) { directory.listFiles()?.forEach { it.delete() } }
    }

    override suspend fun prepareFullCopy(): FullCopy {
        val binding = session.currentBinding ?: throw ApiError.Unauthorized
        val response = fetch { api.fullCopy(binding) }
        val body = response.body() ?: throw ApiError.NoData
        val name = sanitizeFilename(
            response.headers()["Content-Disposition"]?.let(::filenameFrom),
            fallback = "spenditos-data-${LocalDate.now(clock)}.json",
            extension = ".json",
        )
        val file = download(body, binding, File(fullCopyDirectory, "${UUID.randomUUID()}.json"))
        return FullCopy(file, name, binding)
    }

    override suspend fun saveFullCopy(copy: FullCopy, target: Uri) {
        try {
            withContext(io) {
                val output = context.contentResolver.openOutputStream(target, "w")
                    ?: throw ExportSaveFailedException()
                output.use { saveTo(copy, it) }
            }
        } catch (e: Throwable) {
            // Whatever the reason, a document we did not finish writing must not stay behind.
            withContext(NonCancellable + io) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) } }
            throw when {
                e is SessionChangedException -> e
                e is IOException || e is SecurityException -> ExportSaveFailedException(e)
                else -> e
            }
        } finally {
            discardFullCopy(copy)
        }
    }

    override fun discardFullCopy(copy: FullCopy) {
        copy.file.delete()
    }

    /** Writes the temporary file to [output], refusing as soon as the account is no longer [FullCopy.binding]. */
    internal suspend fun saveTo(copy: FullCopy, output: OutputStream) {
        copy.file.inputStream().use { input -> copyWhileSession(input, output, copy.binding) }
        output.flush()
    }

    private suspend fun download(body: ResponseBody, binding: SessionBinding, target: File): File =
        withContext(io) {
            target.parentFile?.mkdirs()
            try {
                body.use { it.byteStream().use { input -> target.outputStream().use { out -> copyWhileSession(input, out, binding) } } }
                target
            } catch (e: Throwable) {
                withContext(NonCancellable) { target.delete() }
                throw when (e) {
                    is CancellationException, is SessionChangedException -> e
                    is IOException -> ApiError.Network(e)
                    else -> e
                }
            }
        }

    /** Copies in chunks; stops (without writing the chunk) when the coroutine is cancelled or the account changed. */
    private suspend fun copyWhileSession(input: InputStream, output: OutputStream, binding: SessionBinding) {
        val buffer = ByteArray(BUFFER_BYTES)
        if (session.currentBinding != binding) throw SessionChangedException()
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (session.currentBinding != binding) throw SessionChangedException()
            output.write(buffer, 0, read)
        }
        if (session.currentBinding != binding) throw SessionChangedException()
    }

    /** Runs a streamed call; a non-2xx answer becomes [ApiError.Server] with the handler's readable message. */
    private suspend fun fetch(call: suspend () -> Response<ResponseBody>): Response<ResponseBody> {
        val response = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionChangedException) {
            throw e
        } catch (e: AuthRequiredException) {
            throw ApiError.Unauthorized
        } catch (e: KeystoreUnavailableException) {
            throw ApiError.KeystoreUnavailable(e)
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }
        if (!response.isSuccessful) {
            val body = withContext(io) { runCatching { response.errorBody()?.string() }.getOrNull() }
            throw ApiError.Server(response.code(), readableMessage(body))
        }
        return response
    }

    /** The export handlers answer `{error, message}`; show the readable `message` first. */
    private fun readableMessage(body: String?): String {
        if (body.isNullOrBlank()) return Strings.get(R.string.data_export_unknown_error)
        return runCatching {
            val obj = SpenditosJson.parseToJsonElement(body).jsonObject
            obj["message"]?.jsonPrimitive?.contentOrNull ?: obj["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: Strings.get(R.string.data_export_unknown_error)
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024

        /** `attachment; filename="transactions-a-to-b.csv"` -> the quoted or bare name. */
        internal fun filenameFrom(header: String): String? =
            Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)

        /** Letters, digits, dot, dash, underscore only: no path separators, no traversal, always [extension]. */
        internal fun sanitizeFilename(raw: String?, fallback: String, extension: String = ".csv"): String {
            val cleaned = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
            if (cleaned.isBlank() || cleaned.contains("..")) return fallback
            return if (cleaned.endsWith(extension, ignoreCase = true)) cleaned else "$cleaned$extension"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ExportModule {
    @Provides @Singleton
    fun exportApi(retrofit: Retrofit): ExportApi = retrofit.create(ExportApi::class.java)

    @Provides @Singleton
    fun exportActions(impl: ExportRepository): ExportActions = impl

    @Provides @ExportIoDispatcher
    fun exportIo(): CoroutineDispatcher = Dispatchers.IO
}
