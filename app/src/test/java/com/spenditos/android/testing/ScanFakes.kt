package com.spenditos.android.testing

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.data.model.ReceiptUploadData
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.scan.PreparedScanDocument
import com.spenditos.android.data.scan.ScanActions
import com.spenditos.android.data.scan.ScanContext
import com.spenditos.android.data.scan.ScanDocumentPreparer
import com.spenditos.android.data.scan.ScanFileUploader
import com.spenditos.android.data.scan.ScanInput
import com.spenditos.android.data.scan.ScanSaveItem
import com.spenditos.android.data.scan.ScanSaveOutcome
import com.spenditos.android.data.scan.ScanSession
import com.spenditos.android.data.scan.ScanStateStore
import com.spenditos.android.data.scan.ScanSubmission
import com.spenditos.android.data.scan.ScanTimeSource
import com.spenditos.android.data.scan.ScanUploadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File

/** Deterministic clock: sleeping advances time without waiting. */
class FakeScanTime : ScanTimeSource {
    var now = 0L
    val sleeps = mutableListOf<Long>()
    override fun elapsedMillis() = now
    override suspend fun sleep(millis: Long) {
        sleeps += millis
        now += millis
    }
}

class FakeScanSession(user: String? = "user-a") : ScanSession {
    private val ids = MutableStateFlow(user)
    override val userId: String? get() = ids.value
    override val binding: SessionBinding? get() = userId?.let { SessionBinding(it, 1L) }
    override val userIds: Flow<String?> = ids
    fun signIn(user: String?) {
        ids.value = user
    }
}

class InMemoryScanStore : ScanStateStore {
    val dismissedByUser = mutableMapOf<String, Set<String>>()
    val pendingByUser = mutableMapOf<String, Map<String, String>>()
    override fun dismissed(userId: String) = dismissedByUser[userId].orEmpty()
    override fun saveDismissed(userId: String, ids: Set<String>) {
        dismissedByUser[userId] = ids
    }
    override fun pendingCompletions(userId: String) = pendingByUser[userId].orEmpty()
    override fun savePendingCompletions(userId: String, pending: Map<String, String>) {
        pendingByUser[userId] = pending
    }
}

/** A preparer that turns each input into [documentsPerInput] small temp files (a PDF input gets one shared group id). */
class FakePreparer(private val dir: File) : ScanDocumentPreparer {
    val created = mutableListOf<File>()

    /** Files handed to the engine (a lazy real preparer only creates what is collected). */
    val emitted = mutableListOf<File>()
    var documentsPerInput: (ScanInput) -> List<PreparedScanDocument> = { input ->
        listOf(document(input.filename ?: "doc", "image/jpeg", com.spenditos.android.data.scan.ScanDocumentKind.IMAGE, input.filename))
    }
    var pageCounts: Map<String, Int?> = emptyMap()

    fun document(
        content: String,
        mime: String,
        kind: com.spenditos.android.data.scan.ScanDocumentKind,
        filename: String?,
        groupId: String? = null,
    ): PreparedScanDocument {
        val file = File.createTempFile("fake-scan-", ".bin", dir).apply { writeText(content) }
        created += file
        return PreparedScanDocument(file, mime, kind, filename, groupId)
    }

    override fun documents(input: ScanInput, remaining: Int, onCount: (Int) -> Unit): Flow<PreparedScanDocument> = flow {
        val docs = documentsPerInput(input).take(remaining)
        onCount(docs.size)
        docs.forEach {
            emitted += it.file
            emit(it)
        }
    }

    override suspend fun pdfPageCount(input: ScanInput): Int? = pageCounts[input.filename]
}

/** Records what the engine uploads: the bytes, the target key and the MIME type. */
class RecordingUploader : ScanFileUploader {
    val uploads = mutableListOf<Triple<String, String, String>>() // documentKey, content, mime
    var failWith: Throwable? = null
    override suspend fun upload(target: ReceiptUploadData, file: File, mimeType: String) {
        failWith?.let { throw it }
        synchronized(uploads) { uploads += Triple(target.documentKey, file.readText(), mimeType) }
    }
}

/** A scripted [ScanActions] for coordinator and view-model tests. */
class FakeScanActions : ScanActions {
    var listResult: () -> List<ScanSummary> = { emptyList() }
    var getResult: (String) -> ScanDetail = { error("no scan $it") }
    var submitResult: suspend (List<ScanInput>) -> ScanSubmission = { inputs ->
        ScanSubmission("scn_1", inputs.size, com.spenditos.android.data.scan.ScanRules.sourceLabel(inputs))
    }
    var waitResult: suspend (String, (ScanSummary) -> Unit) -> ScanDetail = { id, _ -> getResult(id) }
    var saveResult: suspend (String, List<ScanSaveItem>) -> ScanSaveOutcome = { _, items -> ScanSaveOutcome(saved = items.size) }
    var completeError: Throwable? = null
    var creditEstimate: (List<ScanInput>) -> Int = { it.size }

    val completed = mutableListOf<Pair<String, ScanOutcome>>()
    val submitted = mutableListOf<List<ScanInput>>()
    val listCalls get() = listCount
    private var listCount = 0

    override suspend fun submit(inputs: List<ScanInput>, context: ScanContext, onProgress: (ScanUploadProgress) -> Unit): ScanSubmission {
        submitted += inputs
        onProgress(ScanUploadProgress(0, inputs.size))
        return submitResult(inputs)
    }

    override suspend fun waitUntilReady(scanId: String, onProgress: (ScanSummary) -> Unit): ScanDetail = waitResult(scanId, onProgress)
    override suspend fun get(scanId: String): ScanDetail = getResult(scanId)
    override suspend fun list(statuses: List<ScanStatus>): List<ScanSummary> {
        listCount++
        return listResult()
    }

    override suspend fun complete(scanId: String, outcome: ScanOutcome): ScanSummary {
        completeError?.let { throw it }
        completed += scanId to outcome
        return ScanSummary(scanId = scanId, status = outcome.wire)
    }

    override suspend fun save(scanId: String, items: List<ScanSaveItem>, onChunk: (Int, Int) -> Unit): ScanSaveOutcome =
        saveResult(scanId, items).also { onChunk(items.size, items.size) }

    override suspend fun estimateCredits(inputs: List<ScanInput>): Int = creditEstimate(inputs)
}

/** Classifies by file name like the real factory, without a ContentResolver. */
class FakeScanInputFactory : com.spenditos.android.data.scan.ScanInputFactory {
    override fun photo(uri: android.net.Uri) = ScanInput(
        com.spenditos.android.data.scan.ScanInputKind.IMAGE,
        com.spenditos.android.data.scan.ScanInputOrigin.PHOTO_LIBRARY,
        com.spenditos.android.data.scan.ScanSource.Content(uri),
    )

    override fun file(uri: android.net.Uri): ScanInput? {
        val name = uri.lastPathSegment
        val kind = com.spenditos.android.data.scan.ScanFileTypes.classify(name, null) ?: return null
        return ScanInput(kind, com.spenditos.android.data.scan.ScanInputOrigin.FILES, com.spenditos.android.data.scan.ScanSource.Content(uri), name)
    }

    override fun camera(file: File) = ScanInput(
        com.spenditos.android.data.scan.ScanInputKind.IMAGE,
        com.spenditos.android.data.scan.ScanInputOrigin.CAMERA,
        com.spenditos.android.data.scan.ScanSource.OwnedFile(file),
    )
}

/** A clipboard that holds whatever the test puts in [uris]; [reads] counts how often the data was read. */
class FakeClipboardImages(var uris: List<android.net.Uri> = emptyList()) : com.spenditos.android.data.scan.ClipboardImages {
    var reads = 0
        private set

    override fun hasImage() = uris.isNotEmpty()

    override fun imageUris(): List<android.net.Uri> {
        reads++
        return uris
    }
}

/** Rates by currency code (to whatever the test converts into); a code without an entry has no rate. */
class FakeExchangeRates(var rates: Map<String, Double> = emptyMap()) : com.spenditos.android.data.scan.ExchangeRates {
    val asked = mutableListOf<Pair<String, String>>()

    /** Runs before each answer, e.g. to hold the rates back until a test lets them through. */
    var onRate: suspend () -> Unit = {}

    override suspend fun rate(from: String, to: String): Double {
        asked += from to to
        onRate()
        return rates[from] ?: throw com.spenditos.android.data.scan.ExchangeRateUnavailable("no rate")
    }
}
