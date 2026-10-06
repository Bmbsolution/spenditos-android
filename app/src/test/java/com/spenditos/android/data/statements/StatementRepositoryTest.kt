package com.spenditos.android.data.statements

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.data.model.StatementImportTransaction
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.StatementPreviewTransaction
import com.spenditos.android.data.model.resolveStatementCategoryId
import com.spenditos.android.data.model.toImportTransactions
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
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
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatementRepositoryTest {
    private val apiServer = MockWebServer()
    private val uploadServer = MockWebServer()
    private val apiOffline = OfflineInterceptor()
    private val uploadOffline = OfflineInterceptor()
    private var failApiPath: String? = null
    private lateinit var context: Context
    private lateinit var repository: StatementRepository
    private val sourceFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        apiServer.start()
        uploadServer.start()
        context = ApplicationProvider.getApplicationContext()
        statementDirectory().listFiles()?.forEach { it.delete() }
        val apiClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(Interceptor { chain ->
                if (failApiPath == chain.request().url.encodedPath) {
                    failApiPath = null
                    apiOffline.failNext = 1
                }
                apiOffline.intercept(chain)
            })
            .build()
        val uploadClient = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(uploadOffline).build()
        val api = Retrofit.Builder()
            .baseUrl(apiServer.url("/"))
            .client(apiClient)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StatementApi::class.java)
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())
        repository = StatementRepository(context, api, session, uploadClient, Dispatchers.Main)
    }

    @After
    fun tearDown() {
        sourceFiles.forEach { it.delete() }
        statementDirectory().listFiles()?.forEach { it.delete() }
        apiServer.close()
        uploadServer.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `analyze uploads fields before file without auth then posts filename and document key`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(analysisResponse())
        val source = source("statement.csv", "fake,csv,data".toByteArray())

        val result = repository.analyze(Uri.fromFile(source))

        assertEquals("statement.csv", result.filename)
        assertEquals("users/fake/statements/document-fake", result.documentKey)
        assertEquals(listOf("Date", "Details", "Amount"), result.analysis.headers)
        assertEquals(0, tempFiles().size)

        val uploadUrlRequest = apiServer.takeRequest()
        assertEquals("/transactions/upload-url", uploadUrlRequest.url.encodedPath)
        val uploadBody = SpenditosJson.parseToJsonElement(uploadUrlRequest.body!!.utf8()).jsonObject
        assertEquals("text/csv", uploadBody["mimeType"]!!.jsonPrimitive.content)
        assertEquals("bank_statement", uploadBody["documentType"]!!.jsonPrimitive.content)

        val analysisRequest = apiServer.takeRequest()
        assertEquals("/statements/analyze-spreadsheet", analysisRequest.url.encodedPath)
        val analysisBody = SpenditosJson.parseToJsonElement(analysisRequest.body!!.utf8()).jsonObject
        assertEquals("statement.csv", analysisBody["filename"]!!.jsonPrimitive.content)
        assertEquals("users/fake/statements/document-fake", analysisBody["documentKey"]!!.jsonPrimitive.content)

        val s3Request = uploadServer.takeRequest()
        assertEquals("POST", s3Request.method)
        assertNull(s3Request.headers["Authorization"])
        assertTrue(s3Request.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        val multipart = s3Request.body!!.utf8()
        val key = multipart.indexOf("name=\"key\"")
        val policy = multipart.indexOf("name=\"policy\"")
        val credential = multipart.indexOf("name=\"x-amz-credential\"")
        val file = multipart.indexOf("name=\"file\"; filename=\"upload\"")
        assertTrue(key >= 0 && policy > key && credential > policy && file > credential)
        assertTrue(multipart.contains("Content-Type: text/csv"))
    }

    @Test
    fun `non csv files are uploaded with the xlsx mime`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        apiServer.enqueue(analysisResponse())
        val source = source("statement.xlsx", byteArrayOf(1, 2, 3))

        repository.analyze(Uri.fromFile(source))

        val body = SpenditosJson.parseToJsonElement(apiServer.takeRequest().body!!.utf8()).jsonObject
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", body["mimeType"]!!.jsonPrimitive.content)
        val multipart = uploadServer.takeRequest().body!!.utf8()
        assertTrue(multipart.contains("Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    @Test
    fun `readability probe detects valid and missing file uris without a request`() = runTest {
        val readable = source("readable.csv", "fake".toByteArray())
        val missing = File(File(context.cacheDir, "statement-inputs"), "missing.csv")

        assertTrue(repository.isReadable(Uri.fromFile(readable)))
        assertFalse(repository.isReadable(Uri.fromFile(missing)))
        assertEquals(0, apiServer.requestCount)
        assertEquals(0, tempFiles().size)
    }

    @Test
    fun `upload url handles 4xx 5xx and network failures and always deletes the temp file`() = runTest {
        listOf(400, 503).forEach { status ->
            apiServer.enqueue(MockResponse(code = status, body = """{"message":"Upload URL failed"}"""))
            val error = failure { repository.analyze(Uri.fromFile(source("failed.csv", byteArrayOf(1, 2, 3)))) }
            assertTrue(error is ApiError.Server)
            assertEquals(status, (error as ApiError.Server).status)
            assertEquals(0, tempFiles().size)
        }

        apiOffline.failNext = 1
        val error = failure { repository.analyze(Uri.fromFile(source("offline.csv", byteArrayOf(1, 2, 3)))) }
        assertTrue(error is ApiError.Network)
        assertEquals(0, tempFiles().size)
        assertEquals(0, uploadServer.requestCount)
    }

    @Test
    fun `analyze endpoint handles 4xx 5xx and network failures with temp cleanup`() = runTest {
        listOf(400, 503).forEach { status ->
            enqueueUploadUrl()
            uploadServer.enqueue(MockResponse(code = 204))
            apiServer.enqueue(MockResponse(code = status, body = """{"message":"Analysis failed"}"""))
            val error = failure { repository.analyze(Uri.fromFile(source("analysis.csv", byteArrayOf(1, 2, 3)))) }
            assertTrue(error is ApiError.Server)
            assertEquals(status, (error as ApiError.Server).status)
            assertEquals(0, tempFiles().size)
            serverRequestsForAnalyze()
        }

        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 204))
        failApiPath = "/statements/analyze-spreadsheet"
        val error = failure { repository.analyze(Uri.fromFile(source("analysis-offline.csv", byteArrayOf(1, 2, 3)))) }
        assertTrue(error is ApiError.Network)
        assertEquals(0, tempFiles().size)
        assertEquals(5, apiServer.requestCount)
        assertEquals(3, uploadServer.requestCount)
    }

    @Test
    fun `s3 rejection and network failure delete temporary copies`() = runTest {
        enqueueUploadUrl()
        uploadServer.enqueue(MockResponse(code = 403))
        val rejected = failure { repository.analyze(Uri.fromFile(source("s3-reject.csv", byteArrayOf(1, 2, 3)))) }
        assertTrue(rejected is ApiError.Server)
        assertEquals(403, (rejected as ApiError.Server).status)
        assertNull(uploadServer.takeRequest().headers["Authorization"])
        assertEquals(0, tempFiles().size)

        enqueueUploadUrl()
        uploadOffline.failNext = 1
        val offline = failure { repository.analyze(Uri.fromFile(source("s3-offline.csv", byteArrayOf(1, 2, 3)))) }
        assertTrue(offline is ApiError.Network)
        assertEquals(0, tempFiles().size)
    }

    @Test
    fun `too large file is rejected before requesting an upload url`() = runTest {
        val source = source("large.csv", ByteArray(25 * 1024 * 1024 + 1))
        val error = failure { repository.analyze(Uri.fromFile(source)) }
        assertTrue(error is StatementFileTooLargeException)
        assertEquals(0, apiServer.requestCount)
        assertEquals(0, uploadServer.requestCount)
        assertEquals(0, tempFiles().size)
    }

    @Test
    fun `preview sends exact schema and handles success 4xx 5xx and network failures`() = runTest {
        apiServer.enqueue(previewResponse())
        val mapping = StatementColumnMapping(dateColumn = "Date", dateFormat = "MM/DD/YYYY", descriptionColumn = "Details", amountColumn = "Amount", confidence = 0.87)
        val result = repository.preview("statement.csv", "users/fake/statements/document-fake", mapping)
        assertEquals(2, result.transactions.size)
        assertEquals("Groceries", result.transactions.first().categoryId)
        val request = apiServer.takeRequest()
        assertEquals("/statements/preview-spreadsheet", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("statement.csv", body["filename"]!!.jsonPrimitive.content)
        assertEquals("users/fake/statements/document-fake", body["documentKey"]!!.jsonPrimitive.content)
        val mappingBody = body["mapping"]!!.jsonObject
        assertEquals(0.87, mappingBody["confidence"]!!.jsonPrimitive.double, 0.0001)
        assertNull(mappingBody["debitColumn"])

        listOf(400, 503).forEach { status ->
            apiServer.enqueue(MockResponse(code = status, body = """{"message":"Preview failed"}"""))
            val error = failure { repository.preview("statement.csv", "users/fake/key", mapping) }
            assertTrue(error is ApiError.Server)
            assertEquals(status, (error as ApiError.Server).status)
            apiServer.takeRequest()
        }
        apiOffline.failNext = 1
        val offline = failure { repository.preview("statement.csv", "users/fake/key", mapping) }
        assertTrue(offline is ApiError.Network)
        assertEquals(3, apiServer.requestCount)
    }

    @Test
    fun `import body and idempotency key are stable across retries and failures are mapped`() = runTest {
        val rows = listOf(StatementImportTransaction(LocalDate.parse("2026-09-01"), "Fake Market", -4.5, categoryId = "cat-fake"))
        val key = "fake-import-idempotency-key"
        apiServer.enqueue(importResponse())
        apiServer.enqueue(importResponse())
        repository.importTransactions(rows, key)
        repository.importTransactions(rows, key)

        repeat(2) {
            val request = apiServer.takeRequest()
            assertEquals("/statements/import-spreadsheet", request.url.encodedPath)
            assertEquals(key, request.headers["Idempotency-Key"])
            val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals(setOf("transactions"), body.keys)
            val transaction = body["transactions"]!!.jsonArray.single().jsonObject
            assertEquals("2026-09-01", transaction["date"]!!.jsonPrimitive.content)
            assertEquals(-4.5, transaction["amount"]!!.jsonPrimitive.double, 0.0001)
            assertNull(transaction["newCategoryName"])
        }

        listOf(400, 503).forEach { status ->
            apiServer.enqueue(MockResponse(code = status, body = """{"message":"Import failed"}"""))
            val error = failure { repository.importTransactions(rows, key) }
            assertTrue(error is ApiError.Server)
            assertEquals(status, (error as ApiError.Server).status)
            assertEquals(key, apiServer.takeRequest().headers["Idempotency-Key"])
        }
        apiOffline.failNext = 1
        val offline = failure { repository.importTransactions(rows, key) }
        assertTrue(offline is ApiError.Network)
        assertEquals(4, apiServer.requestCount)
    }

    @Test
    fun `category-name resolution is case insensitive and leaves unknown values unresolved`() {
        val categories = listOf(category("cat-food", "Groceries"), category("cat-income", "Salary", income = true))
        assertEquals("cat-food", resolveStatementCategoryId("groceries", categories))
        assertEquals("cat-income", resolveStatementCategoryId("SALARY", categories))
        assertNull(resolveStatementCategoryId("Unmatched", categories))
        assertNull(resolveStatementCategoryId(null, categories))

        val importRows = StatementPreviewResult(
            listOf(
                StatementPreviewTransaction(LocalDate.parse("2026-09-01"), "Raw shop", -3.0, merchant = "Shop", categoryId = "groceries"),
                StatementPreviewTransaction(LocalDate.parse("2026-09-02"), "Salary row", 8.0, categoryId = "Unknown"),
            ),
            categories,
        ).toImportTransactions()
        assertEquals("Shop", importRows[0].description)
        assertEquals(-3.0, importRows[0].amount, 0.0001)
        assertEquals("cat-food", importRows[0].categoryId)
        assertNull(importRows[1].categoryId)
        assertEquals(8.0, importRows[1].amount, 0.0001)
    }

    private fun enqueueUploadUrl() {
        apiServer.enqueue(
            MockResponse(
                body = """{"data":{"uploadUrl":"${uploadServer.url("/presigned-post")}","fields":{"key":"fake-key","policy":"fake-policy","x-amz-credential":"fake-credential"},"documentKey":"users/fake/statements/document-fake","expiresIn":300,"maxFileSizeBytes":26214400}}""",
            ),
        )
    }

    private fun analysisResponse() = MockResponse(
        body = """{"data":{"mapping":{"dateColumn":"Date","descriptionColumn":"Details","amountColumn":"Amount","confidence":0.87},"headers":["Date","Details","Amount"],"preview":[["09/01/2026","Fake Store",-4.5],[null,true,{"hidden":"value"}]]}}""",
    )

    private fun previewResponse() = MockResponse(
        body = """{"data":{"transactions":[{"date":"2026-09-01","description":"FAKE GROCERY","amount":-64.2,"merchant":"Fake Grocery","categoryId":"Groceries"},{"date":"2026-09-02","description":"FAKE PAYROLL","amount":2500,"merchant":"Fake Employer","categoryId":null}],"categories":[{"id":"cat-food","userId":"fake-user","name":"Groceries","color":"#4CAF50","icon":"cart","isIncome":false,"sortOrder":0,"isSystemSuggestion":true},{"id":"cat-salary","userId":"fake-user","name":"Salary","color":"#2196F3","icon":"income","isIncome":true,"sortOrder":1,"isSystemSuggestion":true}]}}""",
    )

    private fun importResponse() = MockResponse(
        body = """{"data":{"message":"Imported 1 transaction.","count":1,"stats":{"created":1,"updated":0,"skipped":0}}}""",
    )

    private fun serverRequestsForAnalyze() {
        apiServer.takeRequest()
        apiServer.takeRequest()
    }

    private fun category(id: String, name: String, income: Boolean = false) =
        Category(id, "fake-user", name, "#333333", isIncome = income)

    private fun source(name: String, bytes: ByteArray): File =
        File(File(context.cacheDir, "statement-inputs").apply { mkdirs() }, name).also { it.writeBytes(bytes); sourceFiles += it }

    private fun statementDirectory() = File(context.cacheDir, "statements")
    private fun tempFiles() = statementDirectory().listFiles()?.filter { it.name.startsWith("statement-") }.orEmpty()

    private suspend fun failure(block: suspend () -> Any?): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "expected failure" }
}
