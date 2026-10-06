package com.spenditos.android.data.planning

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.MonthlyPlanDraftEntry
import com.spenditos.android.data.model.MonthlyPlanTransactionUpdate
import com.spenditos.android.data.model.SaveMonthlyPlanDraftRequest
import com.spenditos.android.testing.OfflineInterceptor
import java.time.YearMonth
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MonthPlanRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var api: PlanningApi
    private lateinit var repository: MonthPlanRepository

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlanningApi::class.java)
        repository = MonthPlanRepository(api)
    }

    @After
    fun tearDown() = server.close()

    private fun response(body: String, code: Int = 200) = MockResponse(code = code, body = body)

    private fun planResponse() = response(
        """{"message":"ok","data":{"month":"2026-02","totals":{"expectedIncome":3200,"committedExpenses":1200,"flexibleExpenses":400,"plannedExpenses":1600,"availableToAllocate":1600},"categories":[],"scheduled":[],"needsIncomeSetup":false,"needsExpensePlan":false}}""",
    )

    private fun draftResponse(draft: String? = null) = response(
        if (draft == null) """{"data":{"draft":null}}""" else """{"data":{"draft":$draft}}""",
    )

    private fun categoriesResponse() = response("""{"data":[]}""")
    private fun recurringResponse() = response("""{"data":[]}""")
    private fun transactionsResponse() = response("""{"data":{"transactions":[],"pagination":{"hasMore":false}}}""")

    @Test
    fun `load combines plan draft categories and recurring data`() = kotlinx.coroutines.test.runTest {
        server.enqueue(planResponse())
        server.enqueue(draftResponse())
        server.enqueue(categoriesResponse())
        server.enqueue(recurringResponse())
        server.enqueue(transactionsResponse())

        val loaded = repository.load(YearMonth.of(2026, 2))

        assertEquals("2026-02", loaded.plan.month)
        assertNull(loaded.draft)
        assertTrue(loaded.categories.isEmpty())
        assertTrue(loaded.recurring.isEmpty())
        assertTrue(loaded.transactions.isEmpty())
        val requests = List(5) { server.takeRequest() }
        assertEquals(listOf("/planning/month", "/planning/draft", "/categories", "/recurring", "/transactions"), requests.map { it.url.encodedPath })
        assertEquals("2026-02", requests.first().url.queryParameter("month"))
    }

    @Test
    fun `pending count reads draft wrapper and counts only writes`() = kotlinx.coroutines.test.runTest {
        server.enqueue(draftResponse("""{"month":"2026-02","entries":[{"id":"fake-entry","idempotencyKeyId":"planning-fake-entry","date":"2026-02-02","description":"Fake item","amount":2,"categoryId":"fake-category","recurring":null}],"transactionDelta":{},"pendingEdits":[{"transactionId":"fake-txn","amount":3}],"pendingLogs":[{"templateId":"fake-item","month":"2026-02"}],"pendingDeletes":[{"transactionId":"fake-old"}],"pendingSkips":[{"templateId":"fake-skip","month":"2026-02","dates":["2026-02-04"]}],"updatedAt":"2026-01-01T00:00:00Z"}"""))

        assertEquals(5, repository.pendingChangeCount(YearMonth.of(2026, 2)))
        val request = server.takeRequest()
        assertEquals("/planning/draft", request.url.encodedPath)
        assertEquals("2026-02", request.url.queryParameter("month"))
    }

    @Test
    fun `pending count treats missing draft as zero and ignores a nested envelope shape`() = kotlinx.coroutines.test.runTest {
        server.enqueue(draftResponse())
        assertEquals(0, repository.pendingChangeCount(YearMonth.of(2026, 2)))
        server.takeRequest()

        server.enqueue(response("""{"data":{"data":{"draft":{"month":"2026-02","entries":[],"transactionDelta":{},"updatedAt":"x"}}}}"""))
        assertEquals(0, repository.pendingChangeCount(YearMonth.of(2026, 2)))
    }

    @Test
    fun `pending count maps client server and transport failures`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"error":"InvalidMonth"}""", 400))
        assertServerFailure { repository.pendingChangeCount(YearMonth.of(2026, 2)) }
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure { repository.pendingChangeCount(YearMonth.of(2026, 2)) }
        offline.failNext = 1
        assertNetworkFailure { repository.pendingChangeCount(YearMonth.of(2026, 2)) }
    }

    @Test
    fun `load maps client server and transport failures`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"error":"InvalidMonth"}""", 400))
        assertServerFailure { repository.load(YearMonth.of(2026, 2)) }
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure { repository.load(YearMonth.of(2026, 2)) }
        offline.failNext = 1
        assertNetworkFailure { repository.load(YearMonth.of(2026, 2)) }
    }

    @Test
    fun `every load endpoint propagates client server and network failures`() = kotlinx.coroutines.test.runTest {
        val successResponses: List<() -> MockResponse> = listOf(
            { planResponse() }, { draftResponse() }, { categoriesResponse() }, { recurringResponse() }, { transactionsResponse() },
        )
        for (endpoint in successResponses.indices) {
            for (status in listOf(400, 503)) {
                repeat(endpoint) { index -> server.enqueue(successResponses[index]()) }
                server.enqueue(response("""{"error":"Unavailable"}""", status))
                assertServerFailure { repository.load(YearMonth.of(2026, 2)) }
                repeat(endpoint + 1) { server.takeRequest() }
            }
        }

        for (endpoint in successResponses.indices) {
            repeat(endpoint) { index -> server.enqueue(successResponses[index]()) }
            val counter = AtomicInteger()
            val failingClient = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(Interceptor { chain ->
                if (counter.incrementAndGet() == endpoint + 1) throw IOException("offline test")
                chain.proceed(chain.request())
            }).build()
            val failingApi = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(failingClient)
                .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
                .build().create(PlanningApi::class.java)
            val failingRepository = MonthPlanRepository(failingApi)
            assertNetworkFailure { failingRepository.load(YearMonth.of(2026, 2)) }
            repeat(endpoint) { server.takeRequest() }
        }
    }

    @Test
    fun `save draft sends required nullable recurring field and never sends removed fields`() = kotlinx.coroutines.test.runTest {
        val entry = MonthlyPlanDraftEntry("fake-entry", "planning-fake-entry", "2026-02-02", "Fake item", 2.0, "fake-category")
        server.enqueue(response("""{"data":{"month":"2026-02","entries":[],"transactionDelta":{},"updatedAt":"2026-01-01T00:00:00Z"}}"""))

        val saved = repository.saveDraft(SaveMonthlyPlanDraftRequest("2026-02", listOf(entry)))

        assertEquals("2026-02", saved.month)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/planning/draft", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("2026-02", body["month"]!!.jsonPrimitive.content)
        assertTrue(body["budgetDelta"] == null)
        assertTrue(body["transactionDelta"]!!.jsonObject.isEmpty())
        assertTrue(body["entries"]!!.jsonArray.single().jsonObject["recurring"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `save draft maps client server and transport failures`() = kotlinx.coroutines.test.runTest {
        val body = SaveMonthlyPlanDraftRequest("2026-02", emptyList())
        server.enqueue(response("""{"error":"InvalidDraft"}""", 400))
        assertServerFailure { repository.saveDraft(body) }
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure { repository.saveDraft(body) }
        offline.failNext = 1
        assertNetworkFailure { repository.saveDraft(body) }
    }

    @Test
    fun `delete draft uses month query and handles failures`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"data":{"month":"2026-02","deleted":true}}"""))
        repository.deleteDraft(YearMonth.of(2026, 2))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("2026-02", request.url.queryParameter("month"))

        server.enqueue(response("""{"error":"InvalidMonth"}""", 400))
        assertServerFailure { repository.deleteDraft(YearMonth.of(2026, 2)) }
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure { repository.deleteDraft(YearMonth.of(2026, 2)) }
        offline.failNext = 1
        assertNetworkFailure { repository.deleteDraft(YearMonth.of(2026, 2)) }
    }

    @Test
    fun `transaction pages advance cursors and map failures`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"data":{"transactions":[{"id":"fake-txn-1","userId":"fake-user","categoryId":"fake-cat","amount":2,"date":"2026-02-02"}],"pagination":{"hasMore":true,"nextCursor":"fake-cursor"}}}"""))
        server.enqueue(response("""{"data":{"transactions":[{"id":"fake-txn-2","userId":"fake-user","categoryId":"fake-cat","amount":3,"date":"2026-02-03"}],"pagination":{"hasMore":false}}}"""))
        val rows = repository.transactions(YearMonth.of(2026, 2))
        assertEquals(listOf("fake-txn-1", "fake-txn-2"), rows.map { it.id })
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("2026-02-01", first.url.queryParameter("startDate"))
        assertEquals("2026-02-28", first.url.queryParameter("endDate"))
        assertNull(first.url.queryParameter("cursor"))
        assertEquals("fake-cursor", second.url.queryParameter("cursor"))

        server.enqueue(response("""{"error":"InvalidQuery"}""", 400))
        assertServerFailure { repository.transactions(YearMonth.of(2026, 2)) }
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure { repository.transactions(YearMonth.of(2026, 2)) }
        offline.failNext = 1
        assertNetworkFailure { repository.transactions(YearMonth.of(2026, 2)) }
    }

    @Test
    fun `skip generation and transaction edit writes use their backend paths`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"data":{"id":"fake-recurring","userId":"fake-user","categoryId":"fake-category","description":"Fake bill","amount":4,"type":"fixed","frequency":"monthly","firstOccurrenceDate":"2026-02-01"}}"""))
        repository.skipOccurrence("fake-recurring", "2026-02-03")
        val skip = server.takeRequest()
        assertEquals("POST", skip.method)
        assertEquals("/recurring/fake-recurring/skip", skip.url.encodedPath)
        assertEquals("2026-02-03", SpenditosJson.parseToJsonElement(skip.body!!.utf8()).jsonObject["date"]!!.jsonPrimitive.content)

        server.enqueue(response("""{"data":{"generated":1,"skipped":0,"errors":0}}"""))
        assertEquals(1, repository.generate(YearMonth.of(2026, 2), "fake-recurring").generated)
        val generate = server.takeRequest()
        assertEquals("POST", generate.method)
        assertEquals("/transactions/generate", generate.url.encodedPath)
        val generateBody = SpenditosJson.parseToJsonElement(generate.body!!.utf8()).jsonObject
        assertEquals("2026-02-01", generateBody["targetDate"]!!.jsonPrimitive.content)
        assertEquals("fake-recurring", generateBody["templateIds"]!!.jsonArray.single().jsonPrimitive.content)

        server.enqueue(response("""{"data":{"id":"fake-txn","userId":"fake-user","categoryId":"fake-category","description":"Fake transaction","amount":9,"date":"2026-02-04"}}"""))
        repository.updateTransaction("fake-txn", MonthlyPlanTransactionUpdate(amount = 9.0))
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/transactions/fake-txn", update.url.encodedPath)
    }

    @Test
    fun `skip generation and transaction edit writes map client server and network failures`() = kotlinx.coroutines.test.runTest {
        assertFailures { repository.skipOccurrence("fake-recurring", "2026-02-03") }
        assertFailures { repository.generate(YearMonth.of(2026, 2), "fake-recurring"); Unit }
        assertFailures { repository.updateTransaction("fake-txn", MonthlyPlanTransactionUpdate(amount = 9.0)); Unit }
    }

    @Test
    fun `transaction delete uses empty body and maps failures`() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse(code = 204))
        repository.deleteTransaction("fake-txn")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/transactions/fake-txn", request.url.encodedPath)

        assertFailures { repository.deleteTransaction("fake-txn") }
    }

    @Test
    fun `one-time create uses bulk and preserves the draft idempotency key`() = kotlinx.coroutines.test.runTest {
        server.enqueue(response("""{"data":{"results":[{"index":0,"transaction":{"id":"fake-created","userId":"fake-user","categoryId":"fake-category","description":"Fake item","amount":8,"date":"2026-02-20"}}]}}"""))
        val result = repository.createOneTime(MonthlyPlanDraftEntry("fake-entry", "planning-fake-entry", "2026-02-20", "Fake item", 8.0, "fake-category"))
        assertEquals("fake-created", result.results.single().transaction!!.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/transactions/bulk", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("manual", body["source"]!!.jsonPrimitive.content)
        val item = body["transactions"]!!.jsonArray.single().jsonObject
        assertEquals("planning-fake-entry", item["idempotencyKeyId"]!!.jsonPrimitive.content)
        assertTrue(item["recurring"] == null)

        server.enqueue(response("""{"data":{"results":[],"duplicates":1}}"""))
        assertEquals(1, repository.createOneTime(MonthlyPlanDraftEntry("fake-entry", "planning-fake-entry", "2026-02-20", "Fake item", 8.0, "fake-category")).duplicates)
        server.takeRequest()
    }

    @Test
    fun `one-time bulk create maps client server and network failures`() = kotlinx.coroutines.test.runTest {
        val entry = MonthlyPlanDraftEntry("fake-entry", "planning-fake-entry", "2026-02-20", "Fake item", 8.0, "fake-category")
        assertFailures { repository.createOneTime(entry); Unit }
    }

    private suspend fun assertFailures(block: suspend () -> Unit) {
        server.enqueue(response("""{"error":"InvalidRequest"}""", 400))
        assertServerFailure(block)
        server.enqueue(response("""{"error":"Unavailable"}""", 503))
        assertServerFailure(block)
        offline.failNext = 1
        assertNetworkFailure(block)
    }

    private suspend fun <T> assertServerFailure(block: suspend () -> T) {
        val result = runCatching { block() }.exceptionOrNull()
        assertTrue(result is ApiError.Server)
    }

    private suspend fun <T> assertNetworkFailure(block: suspend () -> T) {
        val result = runCatching { block() }.exceptionOrNull()
        assertTrue(result is ApiError.Network)
    }
}
