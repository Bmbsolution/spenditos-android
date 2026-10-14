package com.spenditos.android.data.quota

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.model.UserQuotaPayload
import com.spenditos.android.testing.FakeProAccess
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.OfflineInterceptor
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class QuotaRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private var now = NOW
    private lateinit var session: SessionManager
    private lateinit var api: QuotaApi
    private val access = FakeProAccess()

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(QuotaApi::class.java)
        session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { now }, AppEnvironment())
    }

    @After
    fun tearDown() = server.close()

    private fun repository(scope: kotlinx.coroutines.CoroutineScope, api: QuotaApi = this.api) =
        QuotaRepository(api, session, access, EpochClock { now }, scope)

    private val newServer = """{"data":{"quota":{"month":"2026-10","is_pro":false,
        "usage":{"voice_entries":4,"receipt_scans":0,"imports":0,"sentence_entries":27,"scan_credits":7},
        "quotas":{
          "voice_entries":{"limit":10,"used":4,"remaining":6,"unlimited":false,"source":"freeTier"},
          "receipt_scans":{"limit":3,"used":0,"remaining":3,"unlimited":false,"source":"envDefault"},
          "imports":{"limit":null,"used":0,"remaining":null,"unlimited":true,"source":"unlimited"},
          "sentence_entries":{"limit":30,"used":27,"remaining":3,"unlimited":false,"source":"freeTier"},
          "scan_credits":{"limit":10,"used":7,"remaining":3,"unlimited":false,"source":"freeTier"}}}}}"""

    private val oldServer = """{"data":{"quota":{"month":"2026-10","is_pro":false,
        "usage":{"voice_entries":1,"receipt_scans":2,"imports":0},
        "quotas":{
          "voice_entries":{"limit":3,"used":1,"remaining":2,"unlimited":false,"source":"default"},
          "receipt_scans":{"limit":3,"used":2,"remaining":1,"unlimited":false,"source":"default"},
          "imports":{"limit":3,"used":0,"remaining":3,"unlimited":false,"source":"default"}}}}}"""

    @Test
    fun `reads the new buckets from GET user quota`() = runTest(UnconfinedTestDispatcher()) {
        server.enqueue(MockResponse(body = newServer))
        val repo = repository(backgroundScope)
        assertEquals(3, repo.remainingScanCredits())
        assertEquals(3, repo.remainingSentenceEntries())
        assertEquals(6, repo.remainingVoiceEntries())
        assertEquals("/user/quota", server.takeRequest().url.encodedPath)
        assertEquals(1, server.requestCount)
        assertEquals(SentenceMeter(27, 3), repo.sentenceMeter())
        assertEquals(3, repo.overview.first { it != null }!!.remainingScanCredits)
    }

    @Test
    fun `an old server still decodes and scans fall back to receipt scans`() = runTest(UnconfinedTestDispatcher()) {
        server.enqueue(MockResponse(body = oldServer))
        val repo = repository(backgroundScope)
        assertEquals(1, repo.remainingScanCredits())
        assertEquals(QuotaOverview.UNLIMITED, repo.remainingSentenceEntries())
        assertEquals(0, repo.sentenceMeter()!!.used)
        assertNull(repo.sentenceMeter()!!.remaining)
    }

    @Test
    fun `pro never asks the server and is unlimited`() = runTest(UnconfinedTestDispatcher()) {
        access.setPro(true)
        val repo = repository(backgroundScope)
        assertEquals(QuotaOverview.UNLIMITED, repo.remainingScanCredits())
        assertEquals(QuotaOverview.UNLIMITED, repo.remainingSentenceEntries())
        assertNull(repo.sentenceMeter())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unreachable server never blocks`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        offline.failNext = 1
        assertEquals(QuotaOverview.UNLIMITED, repo.remainingScanCredits())
        assertNull(repo.overview.value)
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertEquals(QuotaOverview.UNLIMITED, repo.remainingSentenceEntries())
    }

    @Test
    fun `the cache lasts five minutes and invalidate forces a reload`() = runTest(UnconfinedTestDispatcher()) {
        server.enqueue(MockResponse(body = newServer))
        server.enqueue(MockResponse(body = oldServer))
        server.enqueue(MockResponse(body = newServer))
        val repo = repository(backgroundScope)
        repo.remainingScanCredits()
        now += 200
        repo.remainingScanCredits()
        assertEquals("cached", 1, server.requestCount)

        repo.invalidate()
        assertEquals("old server body now", 1, repo.remainingScanCredits())
        assertEquals(2, server.requestCount)

        now += 301
        repo.remainingScanCredits()
        assertEquals("expired", 3, server.requestCount)
    }

    @Test
    fun `numbers never leak to the next account`() = runTest(UnconfinedTestDispatcher()) {
        server.enqueue(MockResponse(body = newServer))
        val repo = repository(backgroundScope)
        repo.refresh()
        assertEquals(3, repo.overview.first { it != null }!!.remainingScanCredits)

        session.signOut()
        session.signIn(AuthTokens(TestJwt.token("user-2", NOW + 3600, nonce = "a2"), "refresh-user-2", TestJwt.token("user-2", NOW + 3600, nonce = "i2")))
        assertNull(repo.current())
        assertNull(repo.overview.first { it == null })
    }

    @Test
    fun `a response that lands after an account switch is dropped`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<ApiEnvelope<UserQuotaPayload>>()
        val slow = object : QuotaApi { override suspend fun quota() = gate.await() }
        val repo = repository(backgroundScope, slow)
        val pending = async { repo.refresh(force = true) }

        session.signOut()
        session.signIn(AuthTokens(TestJwt.token("user-2", NOW + 3600, nonce = "a2"), "refresh-user-2", TestJwt.token("user-2", NOW + 3600, nonce = "i2")))
        val payload = SpenditosJson.decodeFromString(ApiEnvelope.serializer(UserQuotaPayload.serializer()), newServer)
        gate.complete(payload)
        pending.await()

        assertNull(repo.current())
    }
}
