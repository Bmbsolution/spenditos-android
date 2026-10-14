package com.spenditos.android.data.scan

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.capture.CaptureTransactionRefresh
import com.spenditos.android.data.model.CompleteScanRequest
import com.spenditos.android.data.model.ReceiptUploadRequest
import com.spenditos.android.data.model.ScanBulkRequest
import com.spenditos.android.data.model.ScanBulkTransaction
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.model.TransactionMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/** One reviewed row to save: [idempotencyKeyId] is the scan item id, stable across retries and relaunches. */
data class ScanSaveItem(
    val idempotencyKeyId: String,
    val description: String,
    val amount: Double,
    val date: LocalDate,
    val categoryId: String?,
    val isIncome: Boolean,
)

/** Result of saving a scan's reviewed rows. [duplicates] (a retried chunk the server already had) count as saved. */
data class ScanSaveOutcome(
    val saved: Int = 0,
    val failed: Int = 0,
    val failures: Map<String, String> = emptyMap(),
) {
    operator fun plus(other: ScanSaveOutcome) = ScanSaveOutcome(saved + other.saved, failed + other.failed, failures + other.failures)
}

/**
 * A chunk failed part-way through a save. [handled] rows (from the start, in order) are done, so a retry only
 * needs to resend the rest, and resending them anyway is safe: every row carries its scan item id as idempotency key.
 */
class ScanSaveFailure(val handled: Int, val outcome: ScanSaveOutcome, override val cause: Throwable) : Exception(cause)

/** What the screens and the coordinator use of the Scan engine (a seam for tests). */
interface ScanActions {
    suspend fun submit(inputs: List<ScanInput>, context: ScanContext, onProgress: (ScanUploadProgress) -> Unit = {}): ScanSubmission

    /** Polls until the scan is ready (or saved), reporting progress; throws on failed / discarded / 30 minutes. */
    suspend fun waitUntilReady(scanId: String, onProgress: (ScanSummary) -> Unit = {}): ScanDetail

    suspend fun get(scanId: String): ScanDetail

    suspend fun list(statuses: List<ScanStatus> = listOf(ScanStatus.PROCESSING, ScanStatus.READY)): List<ScanSummary>

    suspend fun complete(scanId: String, outcome: ScanOutcome): ScanSummary

    /** Chunks of 100 through `POST /transactions/bulk` (`source: "scan"`, the scan id); [onChunk] reports rows handled so far. */
    suspend fun save(scanId: String, items: List<ScanSaveItem>, onChunk: (handled: Int, total: Int) -> Unit = { _, _ -> }): ScanSaveOutcome

    /** Credits a selection should cost (1 per file, 1 per started block of 10 PDF pages). */
    suspend fun estimateCredits(inputs: List<ScanInput>): Int
}

/** Injectable clock so polling is deterministic in tests. */
interface ScanTimeSource {
    fun elapsedMillis(): Long
    suspend fun sleep(millis: Long)
}

@Singleton
class SystemScanTimeSource @Inject constructor() : ScanTimeSource {
    override fun elapsedMillis(): Long = System.nanoTime() / 1_000_000
    override suspend fun sleep(millis: Long) = kotlinx.coroutines.delay(millis)
}

/**
 * The unified Scan engine (iOS `ScanService`): prepare every pick (PDF pages become JPEGs on the device), upload a few
 * documents at a time with the receipt upload client, create the scan, poll it, save the reviewed rows.
 * Nothing here logs amounts, merchants, file names or tokens.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val api: ScanApi,
    private val preparer: ScanDocumentPreparer,
    private val uploader: ScanFileUploader,
    private val session: ScanSession,
    private val invalidations: FinanceInvalidationBus,
    private val transactionRefresh: CaptureTransactionRefresh,
    private val time: ScanTimeSource,
) : ScanActions {

    override suspend fun submit(inputs: List<ScanInput>, context: ScanContext, onProgress: (ScanUploadProgress) -> Unit): ScanSubmission {
        if (inputs.isEmpty()) throw ScanFailure.NoDocuments
        val label = ScanRules.sourceLabel(inputs)
        val traceId = newTraceId()
        val uploaded = ConcurrentHashMap<Int, UploadedScanDocument>()
        val uploadedCount = AtomicInteger(0)
        var nextIndex = 0
        var total: Int? = null

        coroutineScope {
            val permits = Semaphore(ScanRules.MAX_CONCURRENT_UPLOADS)
            for ((inputIndex, input) in inputs.withIndex()) {
                currentCoroutineContext().ensureActive()
                val remaining = ScanRules.MAX_DOCUMENTS_PER_UPLOAD - nextIndex
                if (remaining <= 0) break
                val isLast = inputIndex == inputs.lastIndex
                val base = nextIndex
                preparer.documents(input, remaining) { count ->
                    if (isLast) {
                        total = base + min(count, remaining)
                        onProgress(ScanUploadProgress(uploadedCount.get(), total))
                    }
                }.take(remaining).collect { document ->
                    try {
                        permits.acquire()
                    } catch (error: Throwable) {
                        document.file.delete()
                        throw error
                    }
                    val index = nextIndex++
                    // ATOMIC: even if the scope is cancelled before this starts, the body runs to its finally,
                    // so the temp file is always deleted and the permit released.
                    launch(start = CoroutineStart.ATOMIC) {
                        try {
                            val key = uploadDocument(document, traceId)
                            uploaded[index] = UploadedScanDocument(key, document.kind, document.filename, document.pdfGroupId)
                            onProgress(ScanUploadProgress(uploadedCount.incrementAndGet(), total))
                        } finally {
                            document.file.delete()
                            permits.release()
                        }
                    }
                }
            }
            total = nextIndex
        }

        if (nextIndex == 0) throw ScanFailure.NoDocuments
        val ordered = (0 until nextIndex).mapNotNull { uploaded[it] }
        // A scan takes up to MAX_DOCUMENTS; larger uploads become several scans. The first is followed on screen,
        // the rest show on Home.
        val chunks = ScanRules.chunks(ordered)
        var first: ScanSubmission? = null
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val chunkLabel = ScanRules.chunkLabel(label, chunkIndex, chunks.size)
            val response = apiCall { api.create(traceId, ScanRules.createRequest(chunk, chunkLabel, context)) }
            if (first == null) first = ScanSubmission(response.scanId, response.total, chunkLabel)
        }
        return first ?: throw ScanFailure.NoDocuments
    }

    private suspend fun uploadDocument(document: PreparedScanDocument, traceId: String): String {
        val target = apiCall { api.uploadUrl(traceId, ReceiptUploadRequest(mimeType = document.mimeType, documentType = "scan")) }
        uploader.upload(target, document.file, document.mimeType)
        return target.documentKey
    }

    override suspend fun get(scanId: String): ScanDetail = apiCall { api.get(scanId) }

    override suspend fun list(statuses: List<ScanStatus>): List<ScanSummary> =
        apiCall { api.list(statuses.joinToString(",") { it.wire }) }.scans

    override suspend fun complete(scanId: String, outcome: ScanOutcome): ScanSummary =
        apiCall { api.complete(scanId, CompleteScanRequest(outcome.wire)) }

    override suspend fun waitUntilReady(scanId: String, onProgress: (ScanSummary) -> Unit): ScanDetail {
        val start = time.elapsedMillis()
        var attempt = 0
        var failures = 0
        while (time.elapsedMillis() - start < MAX_POLL_MILLIS) {
            currentCoroutineContext().ensureActive()
            try {
                val detail = get(scanId)
                failures = 0
                when (detail.state) {
                    ScanStatus.READY, ScanStatus.SAVED -> return detail
                    ScanStatus.FAILED -> throw ScanFailure.Failed(detail.summary.error)
                    ScanStatus.DISCARDED -> throw ScanFailure.Cancelled
                    ScanStatus.PROCESSING, ScanStatus.UNKNOWN -> onProgress(detail.summary)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ScanFailure) {
                throw error
            } catch (error: ApiError.Server) {
                // Unauthorized, quota, a missing scan and rate limits are final; other failures are retried.
                if (error.status in FINAL_STATUSES) throw error
                if (++failures >= MAX_CONSECUTIVE_FAILURES) throw error
            } catch (error: ApiError.Unauthorized) {
                throw error
            } catch (error: ApiError) {
                if (++failures >= MAX_CONSECUTIVE_FAILURES) throw error
            }
            time.sleep(pollDelayMillis(attempt))
            attempt++
        }
        throw ScanFailure.Timeout
    }

    override suspend fun save(scanId: String, items: List<ScanSaveItem>, onChunk: (Int, Int) -> Unit): ScanSaveOutcome {
        // One session for the whole save: an account switch mid-save is refused instead of sending
        // this account's rows with another account's token.
        val binding = session.binding
        var outcome = ScanSaveOutcome()
        var handled = 0
        try {
            for (chunk in items.chunked(BULK_CHUNK_SIZE)) {
                currentCoroutineContext().ensureActive()
                val response = try {
                    apiCall { api.bulk(ScanBulkRequest(chunk.map(::toBulkRow), scanId = scanId), binding) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw ScanSaveFailure(handled, outcome, error)
                }
                // Results are not index-aligned; failures carry the index of the row in this request.
                val failed = response.failed.filter { it.index in chunk.indices }.associateBy { it.index }
                val failures = failed.entries.associate { (index, failure) ->
                    chunk[index].idempotencyKeyId to failure.reason.ifBlank { "Couldn't be saved" }
                }
                outcome += ScanSaveOutcome(saved = chunk.size - failed.size, failed = failed.size, failures = failures)
                handled += chunk.size
                onChunk(handled, items.size)
            }
        } finally {
            if (outcome.saved > 0) withContext(NonCancellable) { refreshAfterSave(items) }
        }
        return outcome
    }

    private suspend fun refreshAfterSave(items: List<ScanSaveItem>) {
        invalidations.invalidate(Aggregate.TRANSACTION)
        val dates = items.map { it.date }
        try {
            transactionRefresh.refresh(dates.min(), dates.max())
        } catch (_: ApiError) {
            // The bulk write is already committed; the refresh is best-effort like every transaction write.
        }
    }

    override suspend fun estimateCredits(inputs: List<ScanInput>): Int = ScanCredits.forFiles(
        inputs.map { input ->
            val isPdf = input.kind == ScanInputKind.PDF
            ScanCredits.FileEstimate(isPdf, if (isPdf) preparer.pdfPageCount(input) else null)
        },
    )

    private fun toBulkRow(item: ScanSaveItem) = ScanBulkTransaction(
        description = item.description.trim().take(200),
        amount = item.amount,
        date = item.date.toString(),
        mood = TransactionMood.NECESSARY,
        idempotencyKeyId = item.idempotencyKeyId,
        transactionType = if (item.isIncome) "income" else "expense",
        // Rows without a category are saved under the user's Uncategorized category by the server.
        categoryId = item.categoryId?.takeIf { it.isNotBlank() },
    )

    private fun newTraceId() = UUID.randomUUID().toString().replace("-", "")

    companion object {
        /** Largest batch `/transactions/bulk` accepts. */
        const val BULK_CHUNK_SIZE = 100

        /** The server fails a scan that has been processing for 30 minutes, so polling never needs to outlast that. */
        const val MAX_POLL_MILLIS = 30 * 60 * 1000L
        const val MAX_CONSECUTIVE_FAILURES = 8
        private val FINAL_STATUSES = setOf(401, 403, 404, 429)

        /** 1 s, 1.5 s, 2.25 s ... capped at 5 s (iOS `pollDelay`). */
        fun pollDelayMillis(attempt: Int): Long = min(5_000.0, 1_000.0 * 1.5.pow(attempt)).toLong()
    }
}
