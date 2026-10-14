package com.spenditos.android.data.merchants

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class MerchantMappingRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private val bus = FinanceInvalidationBus()
    private val tagged = mutableListOf<SessionBinding?>()
    private var binding: SessionBinding? = SessionBinding("user-1", 7)
    private lateinit var repository: MerchantMappingRepository

    @Before
    fun setUp() {
        server.start()
        val recordTag = Interceptor { chain ->
            tagged += chain.request().tag(SessionBinding::class.java)
            chain.proceed(chain.request())
        }
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).addInterceptor(recordTag).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType())).build()
            .create(MerchantMappingApi::class.java)
        repository = MerchantMappingRepository(api, { binding }, bus)
    }

    @After fun tearDown() { server.close() }

    private fun fixture(): String = requireNotNull(javaClass.classLoader).getResource("contract/handwritten/merchant-mappings.json")!!.readText()

    private fun one(rule: MerchantMapping) =
        SpenditosJson.encodeToString(ApiEnvelope.serializer(MerchantMapping.serializer()), ApiEnvelope(data = rule))

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    @Test
    fun `list decodes the server shape, keeps the action and drops a malformed entry`() = runTest {
        server.enqueue(json(200, fixture()))
        val rows = repository.list()
        assertEquals(3, rows.size)
        val tim = rows.first { it.cleanMerchantName == "Tim Hortons" }
        assertEquals("cat-fake", tim.categoryId)
        assertEquals(RuleAction.CATEGORIZE, tim.ruleAction)
        // Older rows have no action: they categorize.
        assertEquals(RuleAction.CATEGORIZE, rows.first { it.cleanMerchantName == "Amazon" }.ruleAction)
        assertEquals(RuleAction.SKIP, rows.first { it.rawPattern == "Payment Thank You" }.ruleAction)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `rules keeps only the user's own mappings`() = runTest {
        server.enqueue(json(200, fixture()))
        val rules = repository.rules()
        assertEquals(listOf("Tim Hortons", "Payment Thank You"), rules.map { it.cleanMerchantName })
        assertTrue(rules.all { it.source == "user" })
    }

    @Test
    fun `an upsert sends the merchant as pattern and name, the action and the category, bound to the session`() = runTest {
        server.enqueue(json(201, one(MerchantMapping("r1", "u", "Tim Hortons", "Tim Hortons", "cat-dining", action = "categorize"))))
        val saved = repository.upsertRule("Tim Hortons", RuleAction.CATEGORIZE, "cat-dining")
        assertEquals("r1", saved.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/settings/merchant-mappings", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).toString()
        assertEquals("""{"rawPattern":"Tim Hortons","cleanMerchantName":"Tim Hortons","action":"categorize","categoryId":"cat-dining"}""", body)
        assertEquals(listOf(binding), tagged)
    }

    @Test
    fun `a skip upsert carries no category and a 200 update is accepted like a 201`() = runTest {
        server.enqueue(json(200, one(MerchantMapping("r1", "u", "Payment", "Payment", action = "skip"))))
        repository.upsertRule("Payment", RuleAction.SKIP, "cat-ignored")
        val body = server.takeRequest().body!!.utf8()
        assertFalse(body.contains("categoryId"))
        assertTrue(body.contains("\"action\":\"skip\""))
    }

    @Test
    fun `update sends only the action and the category`() = runTest {
        server.enqueue(json(200, one(MerchantMapping("r1", "u", "Tim", "Tim", "cat-2", action = "categorize"))))
        repository.update("r1", RuleAction.CATEGORIZE, "cat-2")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/settings/merchant-mappings/r1", request.url.encodedPath)
        assertEquals("""{"action":"categorize","categoryId":"cat-2"}""", request.body!!.utf8())

        server.enqueue(json(200, one(MerchantMapping("r1", "u", "Tim", "Tim", action = "skip"))))
        repository.update("r1", RuleAction.SKIP, "cat-2")
        assertEquals("""{"action":"skip"}""", server.takeRequest().body!!.utf8())
        assertEquals(listOf(binding, binding), tagged)
    }

    @Test
    fun `delete accepts a 204`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        repository.delete("r1")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/settings/merchant-mappings/r1", request.url.encodedPath)
        assertEquals(listOf(binding), tagged)
    }

    @Test
    fun `match count percent-encodes the merchant and reads the count`() = runTest {
        server.enqueue(json(200, """{"data":{"count":7}}"""))
        assertEquals(7, repository.matchCount("Tim Hortons & Co #4521", "cat 1"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/settings/merchant-mappings/match-count", request.url.encodedPath)
        assertEquals("Tim Hortons & Co #4521", request.url.queryParameter("merchant"))
        assertEquals("cat 1", request.url.queryParameter("categoryId"))
        assertTrue(request.url.encodedQuery!!.contains("merchant=Tim%20Hortons%20%26%20Co%20%234521") || request.url.encodedQuery!!.contains("merchant=Tim+Hortons"))
    }

    @Test
    fun `apply posts without a body, is session bound and invalidates transactions only when something changed`() = runTest {
        val events = mutableListOf<Set<Aggregate>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.toList(events) }
        server.enqueue(json(200, """{"data":{"updated":7}}"""))
        assertEquals(7, repository.apply("r1"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/settings/merchant-mappings/r1/apply", request.url.encodedPath)
        assertEquals(0, request.body?.size ?: 0)
        assertEquals(listOf(binding), tagged)
        assertEquals(listOf(setOf(Aggregate.TRANSACTION)), events)

        server.enqueue(json(200, """{"data":{"updated":0}}"""))
        assertEquals(0, repository.apply("r1"))
        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `a skip rule cannot be applied (409)`() = runTest {
        server.enqueue(json(409, """{"message":"Skip rules do not change past transactions","error":"SkipRuleCannotApply"}"""))
        try { repository.apply("r1"); fail() } catch (e: ApiError.Server) {
            assertEquals(409, e.status)
            assertEquals("SkipRuleCannotApply", e.serverMessage)
        }
    }

    @Test
    fun `always upserts then applies under one binding`() = runTest {
        server.enqueue(json(201, one(MerchantMapping("r9", "u", "Tim Hortons", "Tim Hortons", "cat-dining", action = "categorize"))))
        server.enqueue(json(200, """{"data":{"updated":3}}"""))
        val result = repository.always("Tim Hortons", "cat-dining", alsoUpdatePast = true)
        assertEquals("r9", result.rule.id)
        assertEquals(3, result.pastUpdated)
        assertFalse(result.applyFailed)
        assertEquals("/settings/merchant-mappings", server.takeRequest().url.encodedPath)
        assertEquals("/settings/merchant-mappings/r9/apply", server.takeRequest().url.encodedPath)
        assertEquals(listOf(binding, binding), tagged)
    }

    @Test
    fun `always without the checkbox never calls apply, and a failed apply keeps the saved rule`() = runTest {
        server.enqueue(json(201, one(MerchantMapping("r9", "u", "Tim", "Tim", "c", action = "categorize"))))
        val plain = repository.always("Tim", "c", alsoUpdatePast = false)
        assertNull(plain.pastUpdated)
        assertEquals(1, server.requestCount)

        server.enqueue(json(200, one(MerchantMapping("r9", "u", "Tim", "Tim", "c", action = "categorize"))))
        server.enqueue(json(500, """{"message":"boom"}"""))
        val failedApply = repository.always("Tim", "c", alsoUpdatePast = true)
        assertEquals("r9", failedApply.rule.id)
        assertTrue(failedApply.applyFailed)
        assertNull(failedApply.pastUpdated)
    }

    @Test
    fun `a failed upsert throws so the learner can report it`() = runTest {
        server.enqueue(json(409, """{"message":"Category is being deleted","error":"CategoryBeingDeleted"}"""))
        try { repository.always("Tim", "c", alsoUpdatePast = true); fail() } catch (e: ApiError.Server) {
            assertEquals("CategoryBeingDeleted", e.serverMessage)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `writes need a signed-in session and send nothing without one`() = runTest {
        binding = null
        try { repository.upsertRule("Tim", RuleAction.CATEGORIZE, "c"); fail() } catch (e: ApiError.Unauthorized) { /* expected */ }
        try { repository.delete("r1"); fail() } catch (e: ApiError.Unauthorized) { /* expected */ }
        try { repository.apply("r1"); fail() } catch (e: ApiError.Unauthorized) { /* expected */ }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `4xx, 5xx and network failures are typed`() = runTest {
        server.enqueue(json(400, """{"message":"Validation failed","error":"ValidationError"}"""))
        try { repository.upsertRule("!!!", RuleAction.CATEGORIZE, "c"); fail() } catch (e: ApiError.Server) {
            assertEquals(400, e.status)
            assertEquals("ValidationError", e.serverMessage)
        }
        server.enqueue(json(500, """{"error":"Internal server error"}"""))
        try { repository.list(); fail() } catch (e: ApiError.Server) { assertEquals(500, e.status) }
        offline.failNext = 1
        try { repository.list(); fail() } catch (e: ApiError.Network) { /* expected */ }
    }
}
