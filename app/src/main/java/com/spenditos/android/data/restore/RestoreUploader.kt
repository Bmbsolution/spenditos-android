package com.spenditos.android.data.restore

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Progress of one restore, in the order a client shows it. */
sealed interface RestoreUpdate {
    /** [done] of [total] parts are on the server. */
    data class Uploading(val done: Int, val total: Int) : RestoreUpdate

    /** All parts are in; the server is checking the file. */
    data object Checking : RestoreUpdate

    data class Restoring(val progress: RestoreProgress?) : RestoreUpdate

    data class Finished(val summary: RestoreSummary?) : RestoreUpdate
}

/** The restore job ended `failed`; [code] says why. */
class RestoreFailedException(val code: RestoreErrorCode, message: String) : Exception(message)

/** The upload sat unfinished past its deadline (`expired` status or HTTP 410). */
class RestoreExpiredException : Exception("This restore has expired")

/** The picked file is larger than the server accepts, found before anything is sent. */
class RestoreFileTooLargeException : Exception("The file is too large")

/** The picked file could not be opened or read. */
class RestoreFileUnreadableException(cause: Throwable? = null) : Exception("Could not read the file", cause)

/**
 * Sends a backup file as a restore session and follows the job to its end (docs/features/backup-restore.md).
 * Pure JVM: bytes in, [RestoreUpdate]s out. Cancelling the collector cancels everything; a restore that already
 * started on the server carries on by itself.
 */
class RestoreUploader(
    private val api: RestoreApi,
    private val binding: SessionBinding?,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val maxRetries: Int = MAX_RETRIES,
    private val backoffMs: (attempt: Int) -> Long = { attempt -> 500L shl (attempt - 1) },
) {
    fun run(bytes: ByteArray, sha256: String): Flow<RestoreUpdate> = flow {
        val started = withRetry { apiCall { api.start(StartRestoreRequest(bytes.size.toLong(), sha256), binding) } }
        val partSize = started.partSize
        require(partSize > 0 && started.partCount > 0) { "Server sent an unusable part plan" }
        val received = started.receivedParts.toMutableSet()
        emit(RestoreUpdate.Uploading(received.size, started.partCount))
        for (index in 0 until started.partCount) {
            if (index in received) continue
            val from = index * partSize
            val to = minOf(bytes.size, from + partSize)
            val body = bytes.toRequestBody(OCTET_STREAM, offset = from, byteCount = to - from)
            val updated = withRetry { apiCall { api.putPart(started.id, index, body, binding) } }
            received += index
            emit(RestoreUpdate.Uploading(maxOf(received.size, updated.receivedParts.size), started.partCount))
        }
        withRetry { apiCall { api.complete(started.id, binding) } }
        emit(RestoreUpdate.Checking)
        poll(started.id)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<RestoreUpdate>.poll(id: String) {
        while (true) {
            delay(pollIntervalMs)
            val session = withRetry { apiCall { api.get(id, binding) } }
            when (session.status) {
                RestoreSessionStatus.DONE -> {
                    emit(RestoreUpdate.Finished(session.summary))
                    return
                }
                RestoreSessionStatus.FAILED -> throw RestoreFailedException(
                    session.error?.code ?: RestoreErrorCode.UNKNOWN,
                    session.error?.message.orEmpty(),
                )
                RestoreSessionStatus.EXPIRED -> throw RestoreExpiredException()
                RestoreSessionStatus.RESTORING -> emit(RestoreUpdate.Restoring(session.progress))
                // The server hands a session back to `uploading` when it could not start the job: ask again.
                RestoreSessionStatus.UPLOADING -> withRetry { apiCall { api.complete(id, binding) } }
                RestoreSessionStatus.VALIDATING, RestoreSessionStatus.UNKNOWN -> emit(RestoreUpdate.Checking)
            }
        }
    }

    /** Runs [block], retrying transport failures and 5xx up to [maxRetries] times with backoff. HTTP 410 becomes [RestoreExpiredException]. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Server) {
                if (e.status == 410 || e.serverMessage == "RestoreExpired") throw RestoreExpiredException()
                if (e.status < 500 || attempt >= maxRetries) throw e
            } catch (e: ApiError.Network) {
                if (attempt >= maxRetries) throw e
            }
            attempt++
            delay(backoffMs(attempt))
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_RETRIES = 3
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
