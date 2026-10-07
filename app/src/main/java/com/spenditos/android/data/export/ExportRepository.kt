package com.spenditos.android.data.export

import android.content.Context
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExportIoDispatcher

interface ExportActions {
    /** Downloads the CSV for [start]..[end] (inclusive) into the app's private export folder. */
    suspend fun exportCsv(start: LocalDate, end: LocalDate): File

    /** Removes exported files (financial data must not linger in cache). */
    suspend fun deleteExports()
}

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ExportApi,
    @ExportIoDispatcher private val io: CoroutineDispatcher,
) : ExportActions {
    private val directory get() = File(context.cacheDir, "exports")

    init {
        // Leftovers of a process that died after an export: never kept across launches.
        directory.listFiles()?.forEach { it.delete() }
    }

    override suspend fun exportCsv(start: LocalDate, end: LocalDate): File {
        val response = try {
            api.export(start.toString(), end.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }
        if (!response.isSuccessful) {
            val body = withContext(io) { runCatching { response.errorBody()?.string() }.getOrNull() }
            throw ApiError.Server(response.code(), readableMessage(body))
        }
        val body = response.body() ?: throw ApiError.NoData
        val name = sanitizeFilename(
            response.headers()["Content-Disposition"]?.let(::filenameFrom),
            fallback = "transactions-$start-to-$end.csv",
        )
        return withContext(io) {
            directory.mkdirs()
            val target = File(directory, name)
            try {
                body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                target
            } catch (e: CancellationException) {
                withContext(NonCancellable) { target.delete() }
                throw e
            } catch (e: IOException) {
                target.delete()
                throw ApiError.Network(e)
            }
        }
    }

    override suspend fun deleteExports() {
        withContext(NonCancellable + io) { directory.listFiles()?.forEach { it.delete() } }
    }

    /** The export handler answers `{error, message}`; show the readable `message` first. */
    private fun readableMessage(body: String?): String {
        if (body.isNullOrBlank()) return "Unknown error"
        return runCatching {
            val obj = SpenditosJson.parseToJsonElement(body).jsonObject
            obj["message"]?.jsonPrimitive?.contentOrNull ?: obj["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "Unknown error"
    }

    companion object {
        /** `attachment; filename="transactions-a-to-b.csv"` -> the quoted or bare name. */
        internal fun filenameFrom(header: String): String? =
            Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)

        /** Letters, digits, dot, dash, underscore only: no path separators, no traversal, always a .csv. */
        internal fun sanitizeFilename(raw: String?, fallback: String): String {
            val cleaned = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
            if (cleaned.isBlank() || cleaned.contains("..")) return fallback
            return if (cleaned.endsWith(".csv", ignoreCase = true)) cleaned else "$cleaned.csv"
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
