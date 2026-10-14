package com.spenditos.android.data.scan

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.net.Uri
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.capture.CaptureTransactionRefresh
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.testing.FakePreparer
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.RecordingUploader
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class ScanRepositoryTest {
    private val server = MockWebServer()
    private val uploadServer = MockWebServer()
    private lateinit var dir: File
    private lateinit var preparer: FakePreparer
    private lateinit var uploader: RecordingUploader
    private lateinit var time: FakeScanTime
    private lateinit var repository: ScanRepository
    private val bus = FinanceInvalidationBus()
    private val refreshes = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val uploadUrls = AtomicInteger(0)
    private val context = ScanContext(currency = "CAD", locale = "en-CA", referenceDate = LocalDate.of(2026, 10, 11))

    /** Route -> response; replaced per test. */
    private var respond: (RecordedRequest) -> MockResponse = { MockResponse(code = 404) }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start()
        uploadServer.start()
        dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "scan-test").apply { deleteRecursively(); mkdirs() }
        preparer = FakePreparer(dir)
        uploader = RecordingUploader()
        time = FakeScanTime()
        repository = newRepository(uploader)
        respond = ::defaultRoutes
    }

    @After
    fun tearDown() {
        server.close()
        uploadServer.close()
        dir.deleteRecursively()
    }

    private fun newRepository(fileUploader: ScanFileUploader) = ScanRepository(
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScanApi::class.java),
        preparer = preparer,
        uploader = fileUploader,
        session = FakeScanSession(),
        invalidations = bus,
        transactionRefresh = object : CaptureTransactionRefresh {
            override suspend fun refresh(start: LocalDate, end: LocalDate) {
                refreshes += start to end
            }
        },
        time = time,
    )

    private fun defaultRoutes(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
        "/transactions/upload-url" -> MockResponse(
            body = """{"data":{"uploadUrl":"${uploadServer.url("/presigned-post")}","fields":{"key":"k","policy":"p"},"documentKey":"users/u/scans/doc-${uploadUrls.incrementAndGet()}","expiresIn":300,"maxFileSizeBytes":10485760}}""",
        )
        "/scans" -> if (request.method == "POST") MockResponse(code = 202, body = """{"data":{"scanId":"scn_${scanCreates.incrementAndGet()}","status":"processing","total":5}}""") else MockResponse(body = """{"data":{"scans":[]}}""")
        else -> MockResponse(code = 404)
    }

    private val scanCreates = AtomicInteger(0)

    private fun body(request: RecordedRequest): JsonObject = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject

    private fun requestsTo(path: String) = requests.filter { it.url.encodedPath == path }

    private fun image(name: String) = ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.PHOTO_LIBRARY, ScanSource.Content(Uri.parse("content://fake/$name")), name)

    @Test
    fun `upload then create sends ordered documents with a shared pdf group and never groups spreadsheets`() = runTest {
        val groupId = ScanRules.makePdfGroupId()
        preparer.documentsPerInput = { input ->
            when (input.kind) {
                ScanInputKind.PDF -> (1..3).map { page ->
                    preparer.document("pdf-$page", "image/jpeg", ScanDocumentKind.IMAGE, "Visa-page-$page.jpg", groupId)
                }
                ScanInputKind.CSV -> listOf(preparer.document("a,b", ScanRules.CSV_MIME, ScanDocumentKind.SPREADSHEET, input.filename))
                else -> listOf(preparer.document("img", "image/jpeg", ScanDocumentKind.IMAGE, input.filename))
            }
        }
        val inputs = listOf(
            image("IMG_1.png"),
            ScanInput(ScanInputKind.PDF, ScanInputOrigin.FILES, ScanSource.Content(Uri.parse("content://fake/v")), "Visa.pdf"),
            ScanInput(ScanInputKind.CSV, ScanInputOrigin.FILES, ScanSource.Content(Uri.parse("content://fake/c")), "Sheet.csv"),
        )
        val progress = mutableListOf<ScanUploadProgress>()

        val submission = repository.submit(inputs, context) { progress += it }

        assertEquals("scn_1", submission.scanId)
        assertEquals("3 files", submission.sourceLabel)
        val uploadBodies = requestsTo("/transactions/upload-url").map(::body)
        assertEquals(5, uploadBodies.size)
        assertTrue(uploadBodies.all { it["documentType"]!!.jsonPrimitive.content == "scan" })
        assertEquals(setOf("image/jpeg", "text/csv"), uploadBodies.map { it["mimeType"]!!.jsonPrimitive.content }.toSet())

        val create = body(requestsTo("/scans").single())
        val documents = create["documents"]!!.jsonArray.map { it.jsonObject }
        // Order is the order the user picked, whatever finished uploading first.
        assertEquals(
            listOf("IMG_1.png", "Visa-page-1.jpg", "Visa-page-2.jpg", "Visa-page-3.jpg", "Sheet.csv"),
            documents.map { it["filename"]!!.jsonPrimitive.content },
        )
        val keyByContent = uploader.uploads.associate { it.second to it.first }
        assertEquals(
            listOf("img", "pdf-1", "pdf-2", "pdf-3", "a,b").map { keyByContent.getValue(it) },
            documents.map { it["documentKey"]!!.jsonPrimitive.content },
        )
        assertEquals(listOf(null, groupId, groupId, groupId, null), documents.map { it["pdfGroupId"]?.jsonPrimitive?.content })
        assertEquals(listOf("image", "image", "image", "image", "spreadsheet"), documents.map { it["kind"]!!.jsonPrimitive.content })
        assertEquals("2026-10-11", create["referenceDate"]!!.jsonPrimitive.content)
        assertEquals("CAD", create["userCurrency"]!!.jsonPrimitive.content)
        assertEquals("en-CA", create["locale"]!!.jsonPrimitive.content)
        assertEquals("3 files", create["sourceLabel"]!!.jsonPrimitive.content)
        // Progress ends at 5 of 5 and every temp file is gone.
        assertEquals(ScanUploadProgress(5, 5), progress.last())
        assertTrue(preparer.created.none { it.exists() })
    }

    @Test
    fun `an upload over 60 documents becomes several scans and the first one is followed`() = runTest {
        preparer.documentsPerInput = { List(130) { i -> preparer.document("d$i", "image/jpeg", ScanDocumentKind.IMAGE, "p$i") } }

        val submission = repository.submit(listOf(image("Many.pdf")), context)

        assertEquals("scn_1", submission.scanId)
        assertEquals("Many.pdf (1 of 3)", submission.sourceLabel)
        val creates = requestsTo("/scans").map(::body)
        assertEquals(listOf(60, 60, 10), creates.map { it["documents"]!!.jsonArray.size })
        assertEquals(
            listOf("Many.pdf (1 of 3)", "Many.pdf (2 of 3)", "Many.pdf (3 of 3)"),
            creates.map { it["sourceLabel"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `an upload stops preparing at the 300 document guard`() = runTest {
        preparer.documentsPerInput = { List(400) { i -> preparer.document("d$i", "image/jpeg", ScanDocumentKind.IMAGE, "p$i") } }
        repository.submit(listOf(image("Huge.pdf")), context)
        assertEquals(300, requestsTo("/transactions/upload-url").size)
        assertEquals(5, requestsTo("/scans").size)
    }

    @Test
    fun `quota exhausted on create surfaces the 403 and leaves no temp files`() = runTest {
        respond = { request ->
            if (request.url.encodedPath == "/scans") {
                MockResponse(code = 403, body = """{"error":"QuotaExhausted","quotaType":"scanCredits","limit":10,"used":10,"remaining":0,"amount":1,"resetDate":"2026-11-01"}""")
            } else {
                defaultRoutes(request)
            }
        }
        val error = runCatching { repository.submit(listOf(image("a.png")), context) }.exceptionOrNull()
        assertTrue(error is ApiError.Server)
        assertEquals(403, (error as ApiError.Server).status)
        assertEquals("QuotaExhausted", error.serverMessage)
        assertTrue(preparer.created.none { it.exists() })
    }

    @Test
    fun `a failed upload stops the scan before it is created and removes every temp file`() = runTest {
        preparer.documentsPerInput = { List(5) { i -> preparer.document("d$i", "image/jpeg", ScanDocumentKind.IMAGE, "p$i") } }
        uploader.failWith = ScanFailure.UploadFailed(403)

        val error = runCatching { repository.submit(listOf(image("a.png")), context) }.exceptionOrNull()

        assertEquals(ScanFailure.UploadFailed(403), error)
        assertTrue(requestsTo("/scans").isEmpty())
        assertTrue(preparer.emitted.isNotEmpty())
        assertTrue(preparer.emitted.none { it.exists() })
    }

    @Test
    fun `an input the preparer refuses fails the submit with its message`() = runTest {
        preparer.documentsPerInput = { throw ScanFailure.PdfLocked("Secret.pdf") }
        val error = runCatching { repository.submit(listOf(image("Secret.pdf")), context) }.exceptionOrNull()
        assertEquals("Secret.pdf is password protected. Remove the password and try again.", (error as ScanFailure).userMessage)
        assertTrue(requestsTo("/scans").isEmpty())
    }

    @Test
    fun `empty input is rejected`() = runTest {
        assertEquals(ScanFailure.NoDocuments, runCatching { repository.submit(emptyList(), context) }.exceptionOrNull())
    }

    @Test
    fun `end to end a photo is uploaded the scan created and polled until its rows are ready`() = runTest {
        val polls = AtomicInteger(0)
        respond = { request ->
            if (request.url.encodedPath == "/scans/scn_1") {
                if (polls.incrementAndGet() < 3) MockResponse(body = scanBody("processing")) else MockResponse(body = requireNotNull(javaClass.classLoader!!.getResource("contract/scanReceiptDetailFixture.json")).readText())
            } else {
                defaultRoutes(request)
            }
        }
        val submission = repository.submit(listOf(image("receipt.png")), context)
        val detail = repository.waitUntilReady(submission.scanId)

        assertEquals(listOf("/transactions/upload-url", "/scans", "/scans/scn_1", "/scans/scn_1", "/scans/scn_1"), requests.map { it.url.encodedPath })
        assertEquals(1, uploader.uploads.size)
        assertEquals("users/u/scans/doc-1", uploader.uploads.single().first)
        val resolution = detail.resolution() as ScanResolution.Receipt
        assertEquals("Metro", resolution.item.merchantName)
        assertTrue(preparer.created.none { it.exists() })
    }

    // region Polling

    private fun scanBody(status: String, extra: String = "") =
        """{"data":{"scanId":"scn_9","status":"$status","progress":{"done":1,"total":3},"itemCount":0,"sourceLabel":"3 files","createdAt":"2026-10-11T10:00:00.000Z"$extra}}"""

    @Test
    fun `polling backs off 1 s 1_5 s 2_25 s capped at 5 s and returns the ready detail`() = runTest {
        val calls = AtomicInteger(0)
        respond = { if (calls.incrementAndGet() <= 6) MockResponse(body = scanBody("processing")) else MockResponse(body = scanBody("ready", ""","items":[{"id":"a","amount":4.5}]""")) }
        val seen = mutableListOf<Int>()

        val detail = repository.waitUntilReady("scn_9") { seen += it.progress.done }

        assertEquals(ScanStatus.READY, detail.state)
        assertEquals(1, detail.items!!.size)
        assertEquals(listOf(1000L, 1500L, 2250L, 3375L, 5000L, 5000L), time.sleeps)
        assertEquals(6, seen.size)
        assertEquals(7, requestsTo("/scans/scn_9").size)
    }

    @Test
    fun `a failed scan stops polling with the server message`() = runTest {
        respond = { MockResponse(body = scanBody("failed", ""","error":"Could not read these files"""")) }
        val error = runCatching { repository.waitUntilReady("scn_9") }.exceptionOrNull()
        assertEquals(ScanFailure.Failed("Could not read these files"), error)
        assertEquals(1, requestsTo("/scans/scn_9").size)
    }

    @Test
    fun `a discarded scan stops polling as cancelled and a saved one returns`() = runTest {
        respond = { MockResponse(body = scanBody("discarded")) }
        assertEquals(ScanFailure.Cancelled, runCatching { repository.waitUntilReady("scn_9") }.exceptionOrNull())
        respond = { MockResponse(body = scanBody("saved")) }
        assertEquals(ScanStatus.SAVED, repository.waitUntilReady("scn_9").state)
    }

    @Test
    fun `404 403 and 429 end polling immediately`() = runTest {
        for (status in listOf(404, 403, 429, 401)) {
            requests.clear()
            respond = { MockResponse(code = status, body = """{"message":"x"}""") }
            val error = runCatching { repository.waitUntilReady("scn_9") }.exceptionOrNull()
            assertEquals(status, (error as ApiError.Server).status)
            assertEquals(1, requestsTo("/scans/scn_9").size)
        }
    }

    @Test
    fun `eight consecutive server errors end polling and one success resets the count`() = runTest {
        val calls = AtomicInteger(0)
        respond = { MockResponse(code = 500, body = """{"message":"boom"}""").also { calls.incrementAndGet() } }
        val error = runCatching { repository.waitUntilReady("scn_9") }.exceptionOrNull()
        assertEquals(500, (error as ApiError.Server).status)
        assertEquals(ScanRepository.MAX_CONSECUTIVE_FAILURES, calls.get())

        requests.clear()
        val sequence = AtomicInteger(0)
        respond = {
            when (val n = sequence.incrementAndGet()) {
                in 1..7 -> MockResponse(code = 500, body = """{"message":"boom"}""")
                8 -> MockResponse(body = scanBody("processing"))
                in 9..15 -> MockResponse(code = 500, body = """{"message":"boom"}""")
                else -> MockResponse(body = scanBody("ready", ""","items":[]""")).also { check(n >= 16) }
            }
        }
        assertEquals(ScanStatus.READY, repository.waitUntilReady("scn_9").state)
    }

    @Test
    fun `a scan still processing after 30 minutes times out`() = runTest {
        respond = { MockResponse(body = scanBody("processing")) }
        val error = runCatching { repository.waitUntilReady("scn_9") }.exceptionOrNull()
        assertEquals(ScanFailure.Timeout, error)
        assertTrue(time.now >= ScanRepository.MAX_POLL_MILLIS)
        // The clock stops right after the deadline, not long after it.
        assertTrue(time.now < ScanRepository.MAX_POLL_MILLIS + 5_000)
    }

    @Test
    fun `poll delay follows the iOS schedule`() {
        assertEquals(listOf(1000L, 1500L, 2250L, 3375L, 5000L, 5000L), (0..5).map { ScanRepository.pollDelayMillis(it) })
    }

    // endregion

    // region Read / complete

    @Test
    fun `list asks for processing and ready with an unescaped comma and decodes lossily`() = runTest {
        respond = { MockResponse(body = """{"data":{"scans":[{"scanId":"a","status":"ready"},{"status":"ready"}]}}""") }
        val list = repository.list()
        assertEquals(listOf("a"), list.map { it.scanId })
        assertEquals("status=processing,ready", requests.first().url.encodedQuery)
    }

    @Test
    fun `complete posts the outcome and decodes the summary`() = runTest {
        respond = { MockResponse(body = scanBody("saved")) }
        val summary = repository.complete("scn_9", ScanOutcome.SAVED)
        assertEquals(ScanStatus.SAVED, summary.state)
        val request = requests.first()
        assertEquals("POST", request.method)
        assertEquals("/scans/scn_9/complete", request.url.encodedPath)
        assertEquals("saved", body(request)["outcome"]!!.jsonPrimitive.content)

        requests.clear()
        repository.complete("scn_9", ScanOutcome.DISCARDED)
        assertEquals("discarded", body(requests.first())["outcome"]!!.jsonPrimitive.content)
    }

    @Test
    fun `completing a scan that is not ready surfaces the 409`() = runTest {
        respond = { MockResponse(code = 409, body = """{"error":"ScanNotReady","message":"Scan is still processing"}""") }
        val error = runCatching { repository.complete("scn_9", ScanOutcome.SAVED) }.exceptionOrNull()
        assertEquals(409, (error as ApiError.Server).status)
        assertEquals("ScanNotReady", error.serverMessage)
    }

    // endregion

    // region Save

    private fun items(count: Int) = List(count) { i ->
        ScanSaveItem(
            idempotencyKeyId = "item-$i",
            description = "Row $i",
            amount = 1.0 + i,
            date = LocalDate.of(2026, 10, 1 + i % 20),
            categoryId = if (i % 2 == 0) "cat_a" else null,
            isIncome = i % 3 == 0,
        )
    }

    @Test
    fun `save sends chunks of 100 with the scan id source and item ids as idempotency keys`() = runTest {
        respond = { MockResponse(code = 201, body = """{"data":{"results":[]}}""") }
        val progress = mutableListOf<Pair<Int, Int>>()
        val events = mutableListOf<Set<Aggregate>>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { events += it } }

        val outcome = repository.save("scn_9", items(230)) { handled, total -> progress += handled to total }

        val bulk = requestsTo("/transactions/bulk").map(::body)
        assertEquals(listOf(100, 100, 30), bulk.map { it["transactions"]!!.jsonArray.size })
        assertTrue(bulk.all { it["source"]!!.jsonPrimitive.content == "scan" && it["scanId"]!!.jsonPrimitive.content == "scn_9" })
        val firstRow = bulk[0]["transactions"]!!.jsonArray[0].jsonObject
        assertEquals("item-0", firstRow["idempotencyKeyId"]!!.jsonPrimitive.content)
        assertEquals("income", firstRow["transactionType"]!!.jsonPrimitive.content)
        assertEquals("cat_a", firstRow["categoryId"]!!.jsonPrimitive.content)
        assertEquals("necessary", firstRow["mood"]!!.jsonPrimitive.content)
        val uncategorized = bulk[0]["transactions"]!!.jsonArray[1].jsonObject
        assertFalse("categoryId" in uncategorized)
        assertEquals("expense", uncategorized["transactionType"]!!.jsonPrimitive.content)
        assertEquals(
            (0 until 230).map { "item-$it" },
            bulk.flatMap { chunk -> chunk["transactions"]!!.jsonArray.map { it.jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content } },
        )
        assertEquals(listOf(100 to 230, 200 to 230, 230 to 230), progress)
        assertEquals(230, outcome.saved)
        assertEquals(0, outcome.failed)
        assertEquals(listOf(setOf(Aggregate.TRANSACTION)), events)
        // The refresh covers the dates of the saved rows.
        assertEquals(listOf(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 20)), refreshes)
    }

    @Test
    fun `failed indexes map to the item of that chunk and duplicates count as saved`() = runTest {
        val calls = AtomicInteger(0)
        respond = {
            if (calls.incrementAndGet() == 2) {
                MockResponse(code = 201, body = """{"data":{"results":[],"failed":[{"index":2,"reason":"Amount must be positive"},{"index":99,"reason":"x"},{"index":150,"reason":"out of range"}],"duplicates":3}}""")
            } else {
                MockResponse(code = 201, body = """{"data":{"results":[],"duplicates":1}}""")
            }
        }

        val outcome = repository.save("scn_9", items(130))

        // Chunk 2 started at row 100: index 2 is row 102, index 99 is row 199 (does not exist there, so chunk size 30 rules it out).
        assertEquals(mapOf("item-102" to "Amount must be positive"), outcome.failures)
        assertEquals(1, outcome.failed)
        assertEquals(129, outcome.saved)
    }

    @Test
    fun `a failing chunk reports how many rows were handled and keeps the earlier chunk's refresh`() = runTest {
        val calls = AtomicInteger(0)
        respond = { if (calls.incrementAndGet() == 1) MockResponse(code = 201, body = """{"data":{"results":[]}}""") else MockResponse(code = 500, body = """{"message":"boom"}""") }

        val failure = runCatching { repository.save("scn_9", items(150)) }.exceptionOrNull() as ScanSaveFailure

        assertEquals(100, failure.handled)
        assertEquals(100, failure.outcome.saved)
        assertEquals(500, (failure.cause as ApiError.Server).status)
        assertEquals(1, refreshes.size)
    }

    @Test
    fun `a not found scan on save is a server error the screen can explain`() = runTest {
        respond = { MockResponse(code = 404, body = """{"message":"Scan not found","error":"ScanNotFound"}""") }
        val failure = runCatching { repository.save("scn_9", items(1)) }.exceptionOrNull() as ScanSaveFailure
        assertEquals("ScanNotFound", (failure.cause as ApiError.Server).serverMessage)
        assertTrue(refreshes.isEmpty())
    }

    // endregion

    @Test
    fun `credit estimate is one per file and one per started block of ten pdf pages`() = runTest {
        preparer.pageCounts = mapOf("Long.pdf" to 25, "Odd.pdf" to null, "Ten.pdf" to 10)
        fun pdf(name: String) = ScanInput(ScanInputKind.PDF, ScanInputOrigin.FILES, ScanSource.Content(Uri.parse("content://fake/$name")), name)
        val estimate = repository.estimateCredits(listOf(image("a.png"), image("b.png"), pdf("Long.pdf"), pdf("Odd.pdf"), pdf("Ten.pdf")))
        assertEquals(2 + 3 + 1 + 1, estimate)
    }

    @Test
    fun `the real uploader posts fields before the file with no auth header and maps failures`() = runTest {
        val real = newRepository(OkHttpScanFileUploader(OkHttpClient.Builder().retryOnConnectionFailure(false).build(), kotlinx.coroutines.Dispatchers.IO))
        preparer.documentsPerInput = { listOf(preparer.document("file-bytes", "image/jpeg", ScanDocumentKind.IMAGE, "x.jpg")) }
        uploadServer.enqueue(MockResponse(code = 204))

        real.submit(listOf(image("x.jpg")), context)

        val s3 = uploadServer.takeRequest()
        assertEquals("POST", s3.method)
        assertNull(s3.headers["Authorization"])
        val multipart = s3.body!!.utf8()
        assertTrue(multipart.indexOf("name=\"key\"") < multipart.indexOf("name=\"policy\""))
        assertTrue(multipart.indexOf("name=\"policy\"") < multipart.indexOf("name=\"file\"; filename=\"upload\""))
        assertTrue(multipart.contains("Content-Type: image/jpeg"))
        assertTrue(multipart.contains("file-bytes"))

        uploadServer.enqueue(MockResponse(code = 403))
        val error = runCatching { real.submit(listOf(image("x.jpg")), context) }.exceptionOrNull()
        assertEquals(ScanFailure.UploadFailed(403), error)
    }

    @Test
    fun `scan ready detail from the first request path is decoded through the repository`() = runTest {
        respond = { MockResponse(body = requireNotNull(javaClass.classLoader!!.getResource("contract/scanReadyListDetailFixture.json")).readText()) }
        val detail = repository.get("scn_0002")
        assertEquals(6, detail.items!!.size)
        assertEquals("/scans/scn_0002", requests.first().url.encodedPath)
        assertEquals(setOf("duplicate", "payment", "rule"), detail.items!!.mapNotNull { it.skipReason }.toSet())
        assertEquals(6, detail.items!!.map { it.id }.toSet().size)
    }
}
