package com.spenditos.android.data.capture

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CaptureRepositoryTest {
    private val apiServer = MockWebServer()
    private val uploadServer = MockWebServer()
    private val apiOffline = OfflineInterceptor()
    private val uploadOffline = OfflineInterceptor()
    private lateinit var repository: CaptureRepository
    private lateinit var context: Context
    private val refresh = RecordingRefresh()

    @Before
    fun setUp() {
        apiServer.start()
        uploadServer.start()
        context = ApplicationProvider.getApplicationContext<Context>()
        val api = Retrofit.Builder()
            .baseUrl(apiServer.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(apiOffline).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CaptureApi::class.java)
        val uploadClient = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(uploadOffline).build()
        repository = CaptureRepository(context, api, uploadClient, Dispatchers.IO, FinanceInvalidationBus(), refresh)
    }

    @After
    fun tearDown() {
        apiServer.close()
        uploadServer.close()
    }

    @Test
    fun `typed text uploads text plain document and sends the exact transcript analysis fields`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-text"}}"""))
        apiServer.enqueue(completedAnalysis())

        val parsed = repository.parseText("  24 lunch today  ", CaptureContext("CAD", "en-CA"), "typed", null)

        assertEquals(2, parsed.items.size)
        val uploadRequest = apiServer.takeRequest()
        val uploadBody = SpenditosJson.parseToJsonElement(uploadRequest.body!!.utf8()).jsonObject
        assertEquals("text/plain", uploadBody["mimeType"]!!.jsonPrimitive.content)
        assertEquals("audio", uploadBody["documentType"]!!.jsonPrimitive.content)
        val analyzeRequest = apiServer.takeRequest()
        assertEquals("/transactions/analyze-audio", analyzeRequest.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(analyzeRequest.body!!.utf8()).jsonObject
        assertEquals("audio-transcript", body["mode"]!!.jsonPrimitive.content)
        assertEquals("24 lunch today", body["transcript"]!!.jsonPrimitive.content)
        assertEquals("text/plain", body["mimeType"]!!.jsonPrimitive.content)
        assertEquals("typed", body["inputType"]!!.jsonPrimitive.content)
        assertEquals("en-CA", body["locale"]!!.jsonPrimitive.content)
        assertEquals(LocalDate.now().toString(), body["referenceDate"]!!.jsonPrimitive.content)
        assertEquals("CAD", body["userCurrency"]!!.jsonPrimitive.content)
        assertTrue("typed requests omit parentAnalysisId", "parentAnalysisId" !in body)
        val s3 = uploadServer.takeRequest()
        assertEquals("POST", s3.method)
        assertEquals(null, s3.headers["Authorization"])
        val multipart = s3.body!!.utf8()
        assertTrue(multipart.indexOf("name=\"key\"") < multipart.indexOf("name=\"policy\""))
        assertTrue(multipart.indexOf("name=\"policy\"") < multipart.indexOf("name=\"file\"; filename=\"upload\""))
        assertTrue(multipart.contains("Content-Type: text/plain"))
        assertTrue(multipart.contains("24 lunch today"))
        assertNoTextTempFiles()
    }

    @Test
    fun `voice is transcribed before parse and parse links to the transcription`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-transcript"}}"""))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-transcript","status":"success","transcript":"Lunch for 24 today.","captureSessionId":"analysis-fake-transcript"}}"""))
        val recording = File.createTempFile("capture-voice-test-", ".m4a").apply { writeBytes(byteArrayOf(4, 8, 15, 16)) }
        val transcript = repository.transcribeAudio(recording, CaptureContext("CAD", "en-CA"), hasSpeech = true)
        assertEquals("Lunch for 24 today.", transcript.text)
        assertFalse(recording.exists())

        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-parse"}}"""))
        apiServer.enqueue(completedAnalysis())
        repository.parseText(transcript.text, CaptureContext("CAD", "en-CA"), "voice", transcript.analysisId)

        val requests = List(6) { apiServer.takeRequest() }
        val transcribeBody = SpenditosJson.parseToJsonElement(requests[1].body!!.utf8()).jsonObject
        assertEquals("transcribe", transcribeBody["mode"]!!.jsonPrimitive.content)
        assertEquals("voice", transcribeBody["inputType"]!!.jsonPrimitive.content)
        val parseBody = SpenditosJson.parseToJsonElement(requests[4].body!!.utf8()).jsonObject
        assertEquals("audio-transcript", parseBody["mode"]!!.jsonPrimitive.content)
        assertEquals("analysis-fake-transcript", parseBody["parentAnalysisId"]!!.jsonPrimitive.content)
        // Current backend rejects parentAnalysisId when inputType is voice; typed marks this
        // transcript-only reparse so it does not reserve voice quota a second time.
        assertEquals("typed", parseBody["inputType"]!!.jsonPrimitive.content)
        assertNoTextTempFiles()
    }

    @Test
    fun `analysis failure exposes only a plain backend detail and removes the temporary text`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-fail"}}"""))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-fail","status":"failed","errorDetail":"Please include an amount and try again."}}"""))

        val error = runCatching { repository.parseText("some text", CaptureContext("USD"), "typed", null) }.exceptionOrNull()
        assertEquals(CaptureFailure.AnalysisFailed("Please include an amount and try again."), error)
        assertNoTextTempFiles()
    }

    @Test
    fun `poll timeout is count bounded at sixty requests`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-timeout"}}"""))
        repeat(60) { apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake-timeout","status":"processing"}}""")) }

        val error = runCatching { repository.parseText("text", CaptureContext("USD"), "typed", null) }.exceptionOrNull()
        assertEquals(CaptureFailure.AnalysisTimeout, error)
        assertEquals(59_000L, testScheduler.currentTime)
        assertEquals(62, apiServer.requestCount)
        assertNoTextTempFiles()
    }

    @Test
    fun `quota error and network failure remove the recording`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(code = 403, body = """{"error":"QuotaExhausted"}"""))
        val quotaFile = File.createTempFile("capture-voice-quota-", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val quotaError = runCatching { repository.transcribeAudio(quotaFile, CaptureContext("CAD"), true) }.exceptionOrNull()
        assertTrue(quotaError is ApiError.Server)
        assertEquals(403, (quotaError as ApiError.Server).status)
        assertFalse(quotaFile.exists())

        apiOffline.failNext = 1
        val offlineFile = File.createTempFile("capture-voice-offline-", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val networkError = runCatching { repository.transcribeAudio(offlineFile, CaptureContext("CAD"), true) }.exceptionOrNull()
        assertTrue(networkError is ApiError.Network)
        assertFalse(offlineFile.exists())
    }

    @Test
    fun `no speech and s3 rejection remove recording`() = runTest {
        val silent = File.createTempFile("capture-voice-silent-", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(CaptureFailure.NoSpeechDetected, runCatching { repository.transcribeAudio(silent, CaptureContext("USD"), false) }.exceptionOrNull())
        assertFalse(silent.exists())

        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 403))
        val rejected = File.createTempFile("capture-voice-s3-", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(CaptureFailure.UploadFailed(403), runCatching { repository.transcribeAudio(rejected, CaptureContext("USD"), true) }.exceptionOrNull())
        assertFalse(rejected.exists())
    }

    @Test
    fun `save reuses each item idempotency key and refreshes saved date range`() = runTest {
        apiServer.enqueue(MockResponse(body = """{"data":{"results":[{"index":0}]}}"""))
        apiServer.enqueue(MockResponse(body = """{"data":{"duplicates":1}}"""))
        val item = saveItem("key-fake-stable", LocalDate.parse("2026-10-04"))
        val first = repository.save(listOf(item), "text", "analysis-fake-parse")
        val second = repository.save(listOf(item), "text", "analysis-fake-parse")
        assertEquals(setOf("key-fake-stable"), first.savedIds)
        assertEquals(setOf("key-fake-stable"), second.savedIds)
        val firstBody = SpenditosJson.parseToJsonElement(apiServer.takeRequest().body!!.utf8()).jsonObject
        val secondBody = SpenditosJson.parseToJsonElement(apiServer.takeRequest().body!!.utf8()).jsonObject
        val firstKey = firstBody["transactions"]!!.jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content
        val secondKey = secondBody["transactions"]!!.jsonArray.single().jsonObject["idempotencyKeyId"]!!.jsonPrimitive.content
        assertEquals(firstKey, secondKey)
        assertEquals("text", firstBody["source"]!!.jsonPrimitive.content)
        assertEquals("analysis-fake-parse", firstBody["analysisId"]!!.jsonPrimitive.content)
        assertEquals(listOf(LocalDate.parse("2026-10-04") to LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04") to LocalDate.parse("2026-10-04")), refresh.calls)
    }

    @Test
    fun `bulk response maps failed index and treats a duplicate as saved`() = runTest {
        apiServer.enqueue(MockResponse(body = fixture("capture_bulk_response")))
        val items = listOf(
            saveItem("key-fake-0", LocalDate.parse("2026-10-01")),
            saveItem("key-fake-1", LocalDate.parse("2026-10-02")),
            saveItem("key-fake-2", LocalDate.parse("2026-10-03")),
            saveItem("key-fake-3", LocalDate.parse("2026-10-04")),
        )
        val result = repository.save(items, "voice", "analysis-fake-voice")
        assertEquals(setOf("key-fake-0", "key-fake-2", "key-fake-3"), result.savedIds)
        assertEquals(mapOf("key-fake-1" to "Category does not belong to this account"), result.failures)
        assertEquals(3, result.savedCount)
    }

    @Test
    fun `bulk save 4xx 5xx and network errors propagate for safe retry`() = runTest {
        val item = saveItem("key-fake-retry", LocalDate.now())
        apiServer.enqueue(MockResponse(code = 400, body = """{"message":"Invalid transaction"}"""))
        assertTrue(runCatching { repository.save(listOf(item), "text", null) }.exceptionOrNull() is ApiError.Server)
        apiServer.enqueue(MockResponse(code = 503, body = """{"message":"Unavailable"}"""))
        assertTrue(runCatching { repository.save(listOf(item), "text", null) }.exceptionOrNull() is ApiError.Server)
        apiOffline.failNext = 1
        val error = runCatching { repository.save(listOf(item), "text", null) }.exceptionOrNull()
        assertTrue(error is ApiError.Network)
        val requests = List(2) { apiServer.takeRequest() }
        assertTrue(requests.all { request ->
            SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject["transactions"]!!.toString().contains("key-fake-retry")
        })
    }

    @Test
    fun `parse upload url 4xx network and s3 network delete temporary text file`() = runTest {
        apiServer.enqueue(MockResponse(code = 400, body = """{"message":"Unsupported file type"}"""))
        assertTrue(runCatching { repository.parseText("sentence", CaptureContext("USD"), "typed", null) }.exceptionOrNull() is ApiError.Server)
        assertNoTextTempFiles()

        apiOffline.failNext = 1
        assertTrue(runCatching { repository.parseText("sentence", CaptureContext("USD"), "typed", null) }.exceptionOrNull() is ApiError.Network)
        assertNoTextTempFiles()

        enqueueUploadUrl()
        uploadOffline.failNext = 1
        assertTrue(runCatching { repository.parseText("sentence", CaptureContext("USD"), "typed", null) }.exceptionOrNull() is ApiError.Network)
        assertNoTextTempFiles()
    }

    @Test
    fun `close session is best effort for success 4xx and network failure`() = runTest {
        apiServer.enqueue(MockResponse(body = """{"data":{"captureSessionId":"analysis-fake-root","outcome":"abandoned"}}"""))
        repository.closeCaptureSession("analysis-fake-root", "abandoned")
        apiServer.enqueue(MockResponse(code = 404, body = """{"message":"Capture session not found"}"""))
        repository.closeCaptureSession("analysis-fake-root", "abandoned")
        apiOffline.failNext = 1
        repository.closeCaptureSession("analysis-fake-root", "abandoned")
        assertEquals(2, apiServer.requestCount)
        val body = SpenditosJson.parseToJsonElement(apiServer.takeRequest().body!!.utf8()).jsonObject
        assertEquals("close-session", body["action"]!!.jsonPrimitive.content)
        assertEquals("abandoned", body["outcome"]!!.jsonPrimitive.content)
    }

    private fun enqueueUploadUrl() {
        apiServer.enqueue(
            MockResponse(
                body = """{"data":{"uploadUrl":"${uploadServer.url("/presigned-post")}","fields":{"key":"users/fake-user/capture-fake","policy":"fake-policy"},"documentKey":"users/fake-user/capture-fake"}}""",
            ),
        )
    }

    private fun completedAnalysis() = MockResponse(body = fixture("capture_analysis_completed"))

    private fun fixture(name: String): String = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/$name.json")).readText()

    private fun saveItem(key: String, date: LocalDate) = CaptureSaveItem(12.5, date, null, "Fake item", key)

    private fun assertNoTextTempFiles() {
        assertTrue(context.cacheDir.listFiles()?.none { it.name.startsWith("capture-text-") } ?: true)
    }

    private class RecordingRefresh : CaptureTransactionRefresh {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        override suspend fun refresh(start: LocalDate, end: LocalDate) { calls += start to end }
    }
}
