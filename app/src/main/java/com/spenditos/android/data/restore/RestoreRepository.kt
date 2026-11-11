package com.spenditos.android.data.restore

import android.content.Context
import android.net.Uri
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.export.ExportIoDispatcher
import com.spenditos.android.data.user.UserSettingsActions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Largest backup file a restore accepts (`RESTORE_MAX_BYTES`, 50 MB). */
const val RESTORE_MAX_BYTES: Long = 50L * 1024 * 1024

interface RestoreActions {
    /**
     * Uploads the file at [source] (a document the user picked) and follows the restore to its end.
     * Throws [RestoreFailedException], [RestoreExpiredException], [RestoreFileTooLargeException],
     * [RestoreFileUnreadableException] or an [ApiError]. Cancelling the collector stops the upload.
     */
    fun restore(source: Uri): Flow<RestoreUpdate>

    /** After a finished restore: refresh what the app already caches and tell screens to reload. Best-effort. */
    suspend fun refreshAfterRestore()
}

@Singleton
class RestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RestoreApi,
    private val session: SessionManager,
    private val settings: UserSettingsActions,
    private val categories: CategoryRepository,
    private val invalidations: FinanceInvalidationBus,
    @ExportIoDispatcher private val io: CoroutineDispatcher,
) : RestoreActions {
    override fun restore(source: Uri): Flow<RestoreUpdate> = flow {
        val binding = session.currentBinding ?: throw ApiError.Unauthorized
        val bytes = withContext(io) { readBytes(source) }
        val sha256 = withContext(io) { sha256Hex(bytes) }
        emitAll(RestoreUploader(api, binding).run(bytes, sha256))
    }.flowOn(io)

    override suspend fun refreshAfterRestore() {
        // The same refreshes a fresh sign-in runs (SessionLifecycle), then every finance aggregate reloads.
        runCatching { settings.refresh() }
        runCatching { categories.refresh() }
        invalidations.invalidate(*Aggregate.entries.toTypedArray())
    }

    /** Reads at most [RESTORE_MAX_BYTES] + 1 bytes so an oversized pick is refused without filling memory. */
    private fun readBytes(source: Uri): ByteArray {
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw RestoreFileUnreadableException()
            input.use { stream ->
                val bytes = stream.readUpTo(RESTORE_MAX_BYTES.toInt() + 1)
                if (bytes.size > RESTORE_MAX_BYTES) throw RestoreFileTooLargeException()
                if (bytes.isEmpty()) throw RestoreFileUnreadableException()
                return bytes
            }
        } catch (e: IOException) {
            throw RestoreFileUnreadableException(e)
        } catch (e: SecurityException) {
            throw RestoreFileUnreadableException(e)
        }
    }

    /** `InputStream.readNBytes` needs API 33; this reads up to [limit] bytes on any API level. */
    private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        /** Lowercase hex SHA-256, the form the server checks. */
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RestoreModule {
    @Provides @Singleton
    fun restoreApi(retrofit: Retrofit): RestoreApi = retrofit.create(RestoreApi::class.java)

    @Provides @Singleton
    fun restoreActions(impl: RestoreRepository): RestoreActions = impl
}
