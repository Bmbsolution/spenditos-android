package com.spenditos.android.data.monthbalance

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** iOS `MonthBalanceService` + `MonthBalanceEndpoints` tests: the local month goes up, the answer is online only and idempotent. */
class MonthBalanceRepositoryTest {
    private val server = MockWebServer()
    private val online = MutableStateFlow(true)
    private lateinit var session: SessionManager
    private lateinit var repository: MonthBalanceRepository

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/monthbalance/$name.json")).readText()

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
        session = SessionManager(FakeTokenStore.signedIn("user-fake-1"), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())
        repository = MonthBalanceRepository(
            retrofit.create(MonthBalanceApi::class.java),
            session,
            object : ConnectivityStatus { override val isOnline = online },
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `get sends the local month as a query parameter and decodes the card`() = runTest {
        server.enqueue(MockResponse(body = fixture("shortfall")))
        val data = repository.get("2026-10")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/month-balance", request.url.encodedPath)
        assertEquals("2026-10", request.url.queryParameter("month"))
        assertEquals(MonthBalanceKind.SHORTFALL, data.kind)
        assertTrue(data.showPrompt)
        assertEquals(-200.0, data.balance, 0.0)
    }

    @Test
    fun `set puts the month and the choice and returns the new state`() = runTest {
        server.enqueue(MockResponse(body = fixture("carried")))
        val data = repository.set("2026-10", MonthBalanceChoice.CARRIED)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/month-balance", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(setOf("month", "choice"), body.keys)
        assertEquals("\"2026-10\"", body.getValue("month").toString())
        assertEquals("\"carried\"", body.getValue("choice").toString())
        assertEquals(150.0, data.carriedIn, 0.0)
        assertFalse(data.showPrompt)
        // No Idempotency-Key header: the server record is one per user and month.
        assertEquals(null, request.headers["Idempotency-Key"])
    }

    @Test
    fun `an unknown kind from a newer server decodes as nothing to show`() = runTest {
        server.enqueue(MockResponse(body = """{"data":{"month":"2026-10","previousMonth":"2026-09","received":0,"spent":0,"balance":-5,"kind":"somethingNew","choice":"later","showPrompt":true}}"""))
        val data = repository.get("2026-10")
        assertFalse(data.showPrompt)
        assertEquals(null, data.choice)
    }

    @Test
    fun `an offline answer fails before anything is sent`() = runTest {
        online.value = false
        val error = runCatching { repository.set("2026-10", MonthBalanceChoice.DISMISSED) }.exceptionOrNull()
        assertTrue(error is ApiError.Network)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a signed out session sends nothing`() = runTest {
        val signedOut = SessionManager(FakeTokenStore(), { RefreshedTokens("access", "id") }, EpochClock { NOW }, AppEnvironment())
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType())).build()
        val repo = MonthBalanceRepository(retrofit.create(MonthBalanceApi::class.java), signedOut, object : ConnectivityStatus { override val isOnline = online })
        assertTrue(runCatching { repo.get("2026-10") }.exceptionOrNull() is ApiError.Unauthorized)
        assertTrue(runCatching { repo.set("2026-10", MonthBalanceChoice.DISMISSED) }.exceptionOrNull() is ApiError.Unauthorized)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unknown is never sent as an answer`() = runTest {
        val error = runCatching { repository.set("2026-10", MonthBalanceChoice.UNKNOWN) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }
}
