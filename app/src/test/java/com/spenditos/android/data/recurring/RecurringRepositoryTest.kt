package com.spenditos.android.data.recurring

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.testing.OfflineInterceptor
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.YearMonth

class RecurringRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var repository: RecurringRepository
    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RecurringApi::class.java)
        session = SessionManager(FakeTokenStore.signedIn("user-fake-1"), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())
        repository = RecurringRepository(api, session, FinanceInvalidationBus())
    }

    @After
    fun tearDown() = server.close()

    private fun template(
        amountVaries: Boolean = false,
        active: Boolean = true,
        description: String = "Fake recurring item",
    ) = """{"id":"tmpl-fake-1","userId":"user-fake-1","categoryId":"cat-fake-1","description":"$description","amount":12.5,"type":"fixed","frequency":"monthly","firstOccurrenceDate":"2026-01-15","occurrencesRemaining":null,"isActive":$active,"amountVaries":$amountVaries}"""

    private fun writeResponse(body: String = template()) = MockResponse(code = 200, body = """{"data":$body}""")
    private fun generateResponse() = MockResponse(body = """{"data":{"message":"Generation complete","generated":1,"skipped":0,"errors":0}}""")
    private fun listResponse(body: String = template()) = MockResponse(body = """{"data":[$body]}""")
    private fun settingsResponse(id: String? = null) = MockResponse(
        body = """{"data":{"userId":"user-fake-1","currency":"CAD","mainIncomeTemplateId":${id?.let { "\"$it\"" } ?: "null"}}}""",
    )

    private fun request(amountVaries: Boolean = false, isActive: Boolean = true) = CreateRecurringTemplateRequest(
        categoryId = "cat-fake-1",
        description = "Fake recurring item",
        amount = 12.5,
        frequency = "monthly",
        firstOccurrenceDate = "2026-01-15",
        occurrencesRemaining = JsonNull,
        secondOccurrenceDay = JsonNull,
        amountVaries = amountVaries,
        isActive = isActive,
    )

    private fun update() = UpdateRecurringTemplateRequest(
        description = "Changed fake item",
        occurrencesRemaining = JsonNull,
        secondOccurrenceDay = JsonNull,
        amountVaries = true,
    )

    private suspend fun failure(block: suspend () -> Any?): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "expected failure" }

    @Test
    fun `refresh reads and stores templates and main income and maps errors`() = runTest {
        server.enqueue(listResponse())
        server.enqueue(settingsResponse("tmpl-fake-1"))
        repository.refresh()
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals("tmpl-fake-1", repository.observe().first().single().id)
        assertEquals("tmpl-fake-1", repository.observeMainIncomeId().first())

        server.enqueue(MockResponse(code = 400, body = """{"error":"Bad query"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.refresh() } is ApiError.Network)
    }

    @Test
    fun `cached recurring rows are hidden after switching accounts`() = runTest {
        server.enqueue(listResponse())
        server.enqueue(settingsResponse("tmpl-fake-1"))
        repository.refresh()
        assertEquals(1, repository.observe().first().size)

        session.signIn(
            AuthTokens(
                TestJwt.token("user-fake-2", NOW + 3600, nonce = "access-2"),
                "refresh-user-fake-2",
                TestJwt.token("user-fake-2", NOW + 3600, nonce = "id-2"),
            ),
        )
        assertTrue(repository.observe().first().isEmpty())
        assertEquals(null, repository.observeMainIncomeId().first())
    }

    @Test
    fun `settings read maps a client error after the list succeeds`() = runTest {
        server.enqueue(listResponse())
        server.enqueue(MockResponse(code = 400, body = """{"error":"Settings unavailable"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)

        server.enqueue(listResponse())
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)
    }

    @Test
    fun `settings read maps a network failure after the list succeeds`() = runTest {
        var calls = 0
        val failSettings = Interceptor { chain ->
            calls++
            if (calls == 2) throw IOException("offline on settings read")
            chain.proceed(chain.request())
        }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(failSettings).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RecurringApi::class.java)
        val repo = RecurringRepository(api, session, FinanceInvalidationBus())
        server.enqueue(listResponse())
        assertTrue(failure { repo.refresh() } is ApiError.Network)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `create follows with put when server drops amount changes and then refreshes`() = runTest {
        val invalidations = FinanceInvalidationBus()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RecurringApi::class.java)
        val repo = RecurringRepository(api, session, invalidations)
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }
        server.enqueue(writeResponse()) // the current POST response omits amountVaries
        server.enqueue(writeResponse(template(amountVaries = true)))
        server.enqueue(generateResponse())
        server.enqueue(listResponse(template(amountVaries = true)))
        server.enqueue(settingsResponse("tmpl-fake-1"))

        val created = repo.create(request(amountVaries = true))
        assertTrue(created.amountVaries)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/recurring", post.url.encodedPath)
        assertEquals(true, SpenditosJson.parseToJsonElement(post.body!!.utf8()).jsonObject["amountVaries"]!!.jsonPrimitive.content.toBoolean())
        val followup = server.takeRequest()
        assertEquals("PUT", followup.method)
        assertEquals("/recurring/tmpl-fake-1", followup.url.encodedPath)
        assertTrue(SpenditosJson.parseToJsonElement(followup.body!!.utf8()).jsonObject["amountVaries"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("/transactions/generate", server.takeRequest().url.encodedPath)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals(setOf(Aggregate.RECURRING, Aggregate.TRANSACTION), event.await())
    }

    @Test
    fun `create maps client and network failures`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid recurring data"}"""))
        assertTrue(failure { repository.create(request()) } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.create(request()) } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.create(request()) } is ApiError.Network)
    }

    @Test
    fun `create succeeds when the follow up put fails because the post already committed`() = runTest {
        // Failing here would invite a second Save and a duplicate template (POST is not idempotent).
        server.enqueue(writeResponse())
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid update"}"""))
        server.enqueue(generateResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        val created = repository.create(request(amountVaries = true))
        assertEquals("tmpl-fake-1", created.id)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("/transactions/generate", server.takeRequest().url.encodedPath)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals(5, server.requestCount)
        assertEquals("tmpl-fake-1", repository.observe().first().single().id)
    }

    @Test
    fun `inactive create stays saved when the pause put is offline`() = runTest {
        // POST succeeds, the pause PUT fails at the transport level, the refresh succeeds.
        var calls = 0
        val failPut = Interceptor { chain ->
            calls++
            if (calls == 2) throw IOException("offline on pause put")
            chain.proceed(chain.request())
        }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(failPut).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(RecurringApi::class.java)
        server.enqueue(writeResponse(template(active = false)))
        server.enqueue(listResponse(template(active = false)))
        server.enqueue(settingsResponse())
        val created = RecurringRepository(api, session, FinanceInvalidationBus()).create(request(isActive = false))
        assertEquals(false, created.isActive)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `inactive create is marked as paused with the backend update field`() = runTest {
        server.enqueue(writeResponse(template(active = false)))
        server.enqueue(writeResponse(template(active = false)))
        server.enqueue(listResponse(template(active = false)))
        server.enqueue(settingsResponse())

        repository.create(request(isActive = false))
        val post = server.takeRequest()
        val pause = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("PUT", pause.method)
        val body = SpenditosJson.parseToJsonElement(pause.body!!.utf8()).jsonObject
        assertEquals("false", body["isActive"]!!.jsonPrimitive.content)
        assertTrue(body["pausedAt"]!!.jsonPrimitive.content.isNotBlank())
        server.takeRequest()
        server.takeRequest()
    }

    @Test
    fun `update sends null to clear the saved occurrence cap and refreshes`() = runTest {
        server.enqueue(writeResponse(template(amountVaries = true, description = "Changed fake item")))
        server.enqueue(listResponse(template(amountVaries = true, description = "Changed fake item")))
        server.enqueue(settingsResponse())
        val updated = repository.update("tmpl-fake-1", update())
        assertEquals("Changed fake item", updated.description)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/recurring/tmpl-fake-1", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertTrue(body["occurrencesRemaining"] is JsonNull)
        assertTrue(body["secondOccurrenceDay"] is JsonNull)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `update maps client and network failures`() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":"Not found"}"""))
        assertTrue(failure { repository.update("tmpl-fake-1", update()) } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.update("tmpl-fake-1", update()) } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.update("tmpl-fake-1", update()) } is ApiError.Network)
    }

    @Test
    fun `delete accepts empty success and refreshes and maps errors`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "{}"))
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.delete("tmpl-fake-1")
        val deleted = server.takeRequest()
        assertEquals("DELETE", deleted.method)
        assertEquals("/recurring/tmpl-fake-1", deleted.url.encodedPath)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 404, body = """{"error":"Not found"}"""))
        assertTrue(failure { repository.delete("tmpl-fake-1") } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.delete("tmpl-fake-1") } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.delete("tmpl-fake-1") } is ApiError.Network)
    }

    @Test
    fun `main income patch can explicitly clear the id and maps errors`() = runTest {
        server.enqueue(settingsResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.setMainIncome(null)
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/user/settings/main-income", patch.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(patch.body!!.utf8()).jsonObject
        assertTrue(body["mainIncomeTemplateId"] is JsonNull)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)

        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid input"}"""))
        assertTrue(failure { repository.setMainIncome("tmpl-fake-1") } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.setMainIncome("tmpl-fake-1") } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.setMainIncome("tmpl-fake-1") } is ApiError.Network)
    }

    @Test
    fun `generate posts month and safely accepts a retry with the same target date`() = runTest {
        repeat(2) { attempt ->
            server.enqueue(MockResponse(body = """{"data":{"message":"Generation complete","generated":${if (attempt == 0) 2 else 0},"skipped":${if (attempt == 0) 0 else 2},"errors":0}}"""))
            server.enqueue(listResponse())
            server.enqueue(settingsResponse())
        }
        val first = repository.generate(YearMonth.of(2026, 1))
        val firstRequest = server.takeRequest()
        assertEquals("POST", firstRequest.method)
        assertEquals("/transactions/generate", firstRequest.url.encodedPath)
        val firstBody = SpenditosJson.parseToJsonElement(firstRequest.body!!.utf8()).jsonObject
        assertEquals("2026-01-15", firstBody["targetDate"]!!.jsonPrimitive.content)
        assertEquals(2, first.generated)
        server.takeRequest(); server.takeRequest()

        val retry = repository.generate(YearMonth.of(2026, 1))
        val retryRequest = server.takeRequest()
        assertEquals(firstBody, SpenditosJson.parseToJsonElement(retryRequest.body!!.utf8()).jsonObject)
        assertEquals(0, retry.generated)
        assertEquals(2, retry.skipped)
        server.takeRequest(); server.takeRequest()
    }

    @Test
    fun `generate targets mid month so a zone behind UTC cannot shift it to the previous month`() {
        assertEquals("2026-01-15", generateTargetDate(YearMonth.of(2026, 1)))
        assertEquals("2026-02-15", generateTargetDate(YearMonth.of(2026, 2)))
        assertEquals("2025-12-15", generateTargetDate(YearMonth.of(2025, 12)))
    }

    @Test
    fun `generate maps client and network failures`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid date"}"""))
        assertTrue(failure { repository.generate(YearMonth.of(2026, 1)) } is ApiError.Server)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.generate(YearMonth.of(2026, 1)) } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.generate(YearMonth.of(2026, 1)) } is ApiError.Network)
    }

    @Test
    fun `a new item is logged from today onward for this month, using the mid-month target`() = runTest {
        server.enqueue(writeResponse())
        server.enqueue(generateResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.create(request())
        server.takeRequest() // POST /recurring
        val generate = server.takeRequest()
        assertEquals("/transactions/generate", generate.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(generate.body!!.utf8()).jsonObject
        val today = java.time.LocalDate.now()
        assertEquals(today.withDayOfMonth(15).toString(), body["targetDate"]!!.jsonPrimitive.content)
        assertEquals(today.toString(), body["fromDate"]!!.jsonPrimitive.content)
        assertEquals("tmpl-fake-1", body["templateIds"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `a failed log from today never fails the create`() = runTest {
        server.enqueue(writeResponse())
        server.enqueue(MockResponse(code = 500, body = """{"error":"InternalError"}"""))
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        assertEquals("tmpl-fake-1", repository.create(request()).id)
    }

    @Test
    fun `an inactive new item is not logged`() = runTest {
        server.enqueue(writeResponse(template(active = false)))
        server.enqueue(writeResponse(template(active = false)))
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.create(request(isActive = false))
        server.takeRequest() // POST
        server.takeRequest() // PUT pausedAt
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
    }

    // Lifecycle (#403): request bodies exactly as iOS sends them, with real JSON nulls.

    private val lifecycleNow = java.time.Instant.parse("2026-10-26T12:00:00.789Z")

    private suspend fun lifecycleBody(call: suspend () -> Unit): kotlinx.serialization.json.JsonObject {
        server.enqueue(writeResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        call()
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/recurring/tmpl-fake-1", put.url.encodedPath)
        assertEquals("/recurring", server.takeRequest().url.encodedPath)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        return SpenditosJson.parseToJsonElement(put.body!!.utf8()).jsonObject
    }

    @Test
    fun `pause sends isActive false, a whole-second pausedAt and sync`() = runTest {
        val body = lifecycleBody { repository.pause("tmpl-fake-1", lifecycleNow) }
        assertEquals(setOf("isActive", "pausedAt", "upcomingEntries"), body.keys)
        assertEquals("false", body["isActive"]!!.jsonPrimitive.content)
        assertEquals("2026-10-26T12:00:00Z", body["pausedAt"]!!.jsonPrimitive.content)
        assertEquals("sync", body["upcomingEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `resume sends isActive true and sync only`() = runTest {
        val body = lifecycleBody { repository.resume("tmpl-fake-1") }
        assertEquals(setOf("isActive", "upcomingEntries"), body.keys)
        assertEquals("true", body["isActive"]!!.jsonPrimitive.content)
        assertEquals("sync", body["upcomingEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `end sends endedAt and an explicit null pausedAt`() = runTest {
        val body = lifecycleBody { repository.end("tmpl-fake-1", keepUpcoming = false, now = lifecycleNow) }
        assertEquals(setOf("isActive", "endedAt", "pausedAt", "upcomingEntries"), body.keys)
        assertEquals("false", body["isActive"]!!.jsonPrimitive.content)
        assertEquals("2026-10-26T12:00:00Z", body["endedAt"]!!.jsonPrimitive.content)
        assertTrue(body["pausedAt"] is JsonNull)
        assertEquals("sync", body["upcomingEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `end with a last payment still due this month leaves upcomingEntries out`() = runTest {
        val body = lifecycleBody { repository.end("tmpl-fake-1", keepUpcoming = true, now = lifecycleNow) }
        assertEquals(setOf("isActive", "endedAt", "pausedAt"), body.keys)
        assertTrue(body["pausedAt"] is JsonNull)
    }

    @Test
    fun `end after the next payment sends occurrencesRemaining 1 and nothing else`() = runTest {
        val body = lifecycleBody { repository.endAfterNextPayment("tmpl-fake-1") }
        assertEquals(setOf("occurrencesRemaining"), body.keys)
        assertEquals("1", body["occurrencesRemaining"]!!.jsonPrimitive.content)
    }

    @Test
    fun `start again sends the date, an explicit null cap and sync`() = runTest {
        val body = lifecycleBody { repository.startAgain("tmpl-fake-1", java.time.LocalDate.of(2026, 11, 5)) }
        assertEquals(setOf("isActive", "firstOccurrenceDate", "occurrencesRemaining", "upcomingEntries"), body.keys)
        assertEquals("true", body["isActive"]!!.jsonPrimitive.content)
        assertEquals("2026-11-05", body["firstOccurrenceDate"]!!.jsonPrimitive.content)
        assertTrue(body["occurrencesRemaining"] is JsonNull)
        assertEquals("sync", body["upcomingEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `undoing an end after the next payment restores the previous cap or sends null`() {
        fun encode(request: UpdateRecurringTemplateRequest) =
            SpenditosJson.encodeToString(UpdateRecurringTemplateRequest.serializer(), request)
        assertEquals("""{"occurrencesRemaining":5}""", encode(BillLifecycleRequests.restoreOccurrences(5)))
        assertEquals("""{"occurrencesRemaining":null}""", encode(BillLifecycleRequests.restoreOccurrences(null)))
        // The raw JSON carries the nulls: Kotlin nulls are omitted, JsonNull values are written.
        assertEquals(
            """{"isActive":false,"pausedAt":null,"endedAt":"2026-10-26T12:00:00Z"}""".toJsonObject(),
            encode(BillLifecycleRequests.end(lifecycleNow, keepUpcoming = true)).toJsonObject(),
        )
    }

    private fun String.toJsonObject() = SpenditosJson.parseToJsonElement(this).jsonObject

    @Test
    fun `a lifecycle write refreshes the list and announces recurring and transaction changes`() = runTest {
        val invalidations = FinanceInvalidationBus()
        val repo = RecurringRepository(
            Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
                .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
                .build().create(RecurringApi::class.java),
            session,
            invalidations,
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }
        server.enqueue(writeResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repo.pause("tmpl-fake-1")
        assertEquals(setOf(Aggregate.RECURRING, Aggregate.TRANSACTION), event.await())
    }

    @Test
    fun `lifecycle writes map client and network failures`() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":"Not found"}"""))
        assertTrue(failure { repository.end("tmpl-fake-1") } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.pause("tmpl-fake-1") } is ApiError.Network)
    }

    // History (#403)

    private fun historyResponse() = MockResponse(
        body = """{"data":{"tmpl-fake-1":{"count":2,"total":229.72,"firstDate":"2026-09-30","lastDate":"2026-10-14","entries":[{"id":"txn-hist-2","date":"2026-10-14","amount":114.86,"description":"Klarna Airbnb"},{"id":"txn-hist-1","date":"2026-09-30","amount":114.86,"description":"Klarna Airbnb"}]},"tmpl-fake-2":{"count":0,"total":0,"firstDate":null,"lastDate":null}}}""",
    )

    @Test
    fun `history asks for the ids with literal commas and parses the summaries`() = runTest {
        server.enqueue(historyResponse())
        val result = repository.history(listOf("tmpl-fake-1", "tmpl-fake-2"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/recurring/history", request.url.encodedPath)
        assertEquals("ids=tmpl-fake-1,tmpl-fake-2", request.url.encodedQuery)

        val first = result.getValue("tmpl-fake-1")
        assertEquals(2, first.count)
        assertEquals(229.72, first.total, 0.0)
        assertEquals("2026-09-30", first.firstDate)
        assertEquals("2026-10-14", first.lastDate)
        assertEquals(listOf("txn-hist-2", "txn-hist-1"), first.entries!!.map { it.id })
        assertEquals(114.86, first.entries!!.first().amount, 0.0)
        val empty = result.getValue("tmpl-fake-2")
        assertEquals(0, empty.count)
        assertEquals(null, empty.firstDate)
        assertEquals(null, empty.entries)
    }

    @Test
    fun `history with entries adds includeEntries, caps at 50 ids and skips empty requests`() = runTest {
        server.enqueue(historyResponse())
        repository.history(listOf("tmpl-fake-1"), includeEntries = true)
        assertEquals("ids=tmpl-fake-1&includeEntries=true", server.takeRequest().url.encodedQuery)

        server.enqueue(historyResponse())
        repository.history((1..60).map { "id$it" })
        val ids = server.takeRequest().url.encodedQuery!!.removePrefix("ids=").split(",")
        assertEquals(50, ids.size)
        assertEquals("id50", ids.last())

        assertTrue(repository.history(emptyList()).isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `history maps client and network failures`() = runTest {
        server.enqueue(MockResponse(code = 500, body = """{"error":"InternalError"}"""))
        assertTrue(failure { repository.history(listOf("tmpl-fake-1")) } is ApiError.Server)
        offline.failNext = 1
        assertTrue(failure { repository.history(listOf("tmpl-fake-1")) } is ApiError.Network)
    }

    // Month entries (#403)

    private fun entriesPage(id: String, hasMore: Boolean, cursor: String? = null) = MockResponse(
        body = """{"data":{"transactions":[{"id":"$id","userId":"user-fake-1","categoryId":"cat-fake-1","templateId":"tmpl-fake-1","amount":12.5,"date":"2026-10-03"}],"pagination":{"hasMore":$hasMore,"nextCursor":${cursor?.let { "\"$it\"" } ?: "null"}}}}""",
    )

    @Test
    fun `month entries read the whole month and follow the cursor`() = runTest {
        server.enqueue(entriesPage("e1", hasMore = true, cursor = "c1"))
        server.enqueue(entriesPage("e2", hasMore = false))
        val entries = repository.monthEntries(YearMonth.of(2026, 10))
        assertEquals(listOf("e1", "e2"), entries.map { it.id })
        val first = server.takeRequest()
        assertEquals("/transactions", first.url.encodedPath)
        assertEquals("2026-10-01", first.url.queryParameter("startDate"))
        assertEquals("2026-10-31", first.url.queryParameter("endDate"))
        assertEquals("200", first.url.queryParameter("limit"))
        assertEquals(null, first.url.queryParameter("cursor"))
        assertEquals("c1", server.takeRequest().url.queryParameter("cursor"))
    }

    @Test
    fun `month entries map network failures`() = runTest {
        offline.failNext = 1
        assertTrue(failure { repository.monthEntries(YearMonth.of(2026, 10)) } is ApiError.Network)
    }

    @Test
    fun `generate sends the selected template ids and omits them for the whole month`() = runTest {
        server.enqueue(generateResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.generate(YearMonth.of(2026, 10), listOf("tmpl-fake-1", "tmpl-fake-2"))
        val withIds = SpenditosJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("2026-10-15", withIds["targetDate"]!!.jsonPrimitive.content)
        assertEquals(listOf("tmpl-fake-1", "tmpl-fake-2"), withIds["templateIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        server.takeRequest(); server.takeRequest()

        server.enqueue(generateResponse())
        server.enqueue(listResponse())
        server.enqueue(settingsResponse())
        repository.generate(YearMonth.of(2026, 10))
        assertEquals(setOf("targetDate"), SpenditosJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject.keys)
    }
}
