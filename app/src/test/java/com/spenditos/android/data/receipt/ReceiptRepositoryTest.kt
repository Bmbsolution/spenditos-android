package com.spenditos.android.data.receipt

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

class ReceiptRepositoryTest {
    private val apiServer = MockWebServer()
    private val uploadServer = MockWebServer()
    private val apiOffline = OfflineInterceptor()
    private val uploadOffline = OfflineInterceptor()
    private lateinit var image: File
    private lateinit var repository: ReceiptRepository

    @Before
    fun setUp() {
        apiServer.start()
        uploadServer.start()
        val apiClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(apiOffline)
            .build()
        val uploadClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(uploadOffline)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(apiServer.url("/"))
            .client(apiClient)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReceiptApi::class.java)
        repository = ReceiptRepository(api, uploadClient)
        image = File.createTempFile("fake-receipt-", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    }

    @After
    fun tearDown() {
        image.delete()
        apiServer.close()
        uploadServer.close()
    }

    @Test
    fun `uploads multipart fields before file without authorization and resolves on third poll`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake","status":"pending"}}"""))
        apiServer.enqueue(pendingStatus())
        apiServer.enqueue(pendingStatus())
        apiServer.enqueue(successStatus())

        val stages = mutableListOf<ReceiptScanStage>()
        val result = repository.scan(image) { stages += it }

        assertEquals(ReceiptResult(amount = 12.5, description = "Cafe", date = "2026-09-15", merchantName = "Cafe", categoryHint = "Food"), result)
        assertTrue(!image.exists())
        assertEquals(listOf(ReceiptScanStage.UPLOADING, ReceiptScanStage.ANALYZING, ReceiptScanStage.POLLING), stages)
        assertEquals(5, apiServer.requestCount)
        assertEquals(1, uploadServer.requestCount)
        val uploadRequest = uploadServer.takeRequest()
        assertEquals("POST", uploadRequest.method)
        assertEquals("multipart/form-data", uploadRequest.headers["Content-Type"]?.substringBefore(';'))
        assertEquals(null, uploadRequest.headers["Authorization"])
        val multipart = uploadRequest.body!!.utf8()
        val keyField = multipart.indexOf("name=\"key\"")
        val policyField = multipart.indexOf("name=\"policy\"")
        val filePart = multipart.indexOf("name=\"file\"; filename=\"upload\"")
        assertTrue(keyField >= 0 && policyField > keyField && filePart > policyField)
        assertTrue(multipart.contains("Content-Type: image/jpeg"))

        val requests = List(5) { apiServer.takeRequest() }
        val traceIds = requests.map { it.headers["X-Spenditos-Trace-Id"] }
        assertTrue(traceIds.all { it != null && it.matches(Regex("[0-9a-f]{32}")) })
        assertEquals(1, traceIds.distinct().size)
        assertEquals("/transactions/upload-url", requests[0].url.encodedPath)
        assertEquals("/transactions/analyze-image", requests[1].url.encodedPath)
        assertTrue(requests.drop(2).all { it.url.encodedPath == "/transactions/analysis/analysis-fake" })
    }

    @Test
    fun `failed analysis status becomes a scan failure`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake"}}"""))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake","status":"failed","error":"Image analysis failed. Please try again."}}"""))

        val error = runCatching { repository.scan(image) }.exceptionOrNull()
        assertEquals(ReceiptScanFailure.AnalysisFailed, error)
    }

    @Test
    fun `polling pending analysis gives up after fifteen polls two seconds apart`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake"}}"""))
        repeat(15) { apiServer.enqueue(pendingStatus()) }

        val error = runCatching { repository.scan(image) }.exceptionOrNull()
        assertEquals(ReceiptScanFailure.AnalysisTimeout, error)
        assertEquals(28_000L, testScheduler.currentTime) // 14 waits of 2 s between 15 polls
        assertEquals(17, apiServer.requestCount)
    }

    @Test
    fun `quota exhausted response is preserved as server error`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(code = 403, body = """{"error":"QuotaExhausted","quotaType":"receiptScans","limit":3,"used":3,"remaining":0,"resetDate":"2026-10-01"}"""))

        val error = runCatching { repository.scan(image) }.exceptionOrNull()
        assertTrue(error is ApiError.Server)
        val serverError = error as ApiError.Server
        assertEquals(403, serverError.status)
        assertEquals("QuotaExhausted", serverError.serverMessage)
        assertTrue(!image.exists())
    }

    @Test
    fun `upload url client rejection is preserved as server error`() = runTest {
        apiServer.enqueue(MockResponse(code = 400, body = """{"message":"Unsupported file type"}"""))

        val error = runCatching { repository.scan(image) }.exceptionOrNull()
        assertTrue(error is ApiError.Server)
        val serverError = error as ApiError.Server
        assertEquals(400, serverError.status)
        assertEquals("Unsupported file type", serverError.serverMessage)
        assertEquals(0, uploadServer.requestCount)
        assertTrue(!image.exists())
    }

    @Test
    fun `network failure at upload url is mapped`() = runTest {
        apiOffline.failNext = 1
        assertNetworkFailure { repository.scan(image) }
        assertEquals(0, apiServer.requestCount)
    }

    @Test
    fun `network failure during direct upload is mapped`() = runTest {
        enqueueUploadUrl()
        uploadOffline.failNext = 1
        assertNetworkFailure { repository.scan(image) }
        assertEquals(1, apiServer.requestCount)
        assertEquals(0, uploadServer.requestCount)
    }

    @Test
    fun `network failure starting analysis is mapped`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        assertNetworkFailure {
            repository.scan(image) { stage -> if (stage == ReceiptScanStage.ANALYZING) apiOffline.failNext = 1 }
        }
        assertEquals(1, uploadServer.requestCount)
    }

    @Test
    fun `network failure polling analysis is mapped`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(MockResponse(body = """{"data":{"analysisId":"analysis-fake"}}"""))
        assertNetworkFailure {
            repository.scan(image) { stage -> if (stage == ReceiptScanStage.POLLING) apiOffline.failNext = 1 }
        }
        assertEquals(2, apiServer.requestCount)
    }

    @Test
    fun `S3 rejects a non success status`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 403))

        val error = runCatching { repository.scan(image) }.exceptionOrNull()
        assertEquals(ReceiptScanFailure.UploadFailed(403), error)
        assertTrue(!image.exists())
    }

    private fun enqueueUploadUrl() {
        apiServer.enqueue(
            MockResponse(
                body = """{"data":{"uploadUrl":"${uploadServer.url("/presigned-post")}","fields":{"key":"users/fake/receipt.jpg","policy":"fake-policy"},"documentKey":"users/fake/receipt.jpg"}}""",
            ),
        )
    }

    private fun pendingStatus() = MockResponse(body = """{"data":{"analysisId":"analysis-fake","status":"processing"}}""")

    private fun successStatus() = MockResponse(
        body = """{"data":{"analysisId":"analysis-fake","status":"success","result":{"amount":12.5,"description":"Cafe","merchantName":"Cafe","date":"2026-09-15","categoryHint":"Food"}}}""",
    )

    private suspend fun assertNetworkFailure(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected ApiError.Network, got ${error?.javaClass?.simpleName}", error is ApiError.Network)
        assertNotNull(error)
        assertTrue(!image.exists())
    }
}
