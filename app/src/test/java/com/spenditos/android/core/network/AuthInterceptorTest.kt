package com.spenditos.android.core.network

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenKey
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val refreshes = AtomicInteger()
    private val newAccess = TestJwt.token("user-1", NOW + 7200, nonce = "new")
    private lateinit var store: FakeTokenStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        store = FakeTokenStore.signedIn()
        val session = SessionManager(store, { refreshes.incrementAndGet(); RefreshedTokens(newAccess, TestJwt.token("user-1", NOW + 7200, nonce = "id2")) }, EpochClock { NOW }, AppEnvironment())
        client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build()
    }

    @After
    fun tearDown() = server.close()

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `adds bearer token`() {
        server.enqueue(MockResponse(code = 200))
        get("/transactions").close()
        assertEquals("Bearer ${store.values[TokenKey.ACCESS]}", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `auth routes go out without a token`() {
        server.enqueue(MockResponse(code = 200))
        get("/auth/login").close()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 refreshes once and retries with the new token`() {
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 200))
        val response = get("/transactions")
        assertEquals(200, response.code)
        response.close()
        server.takeRequest()
        assertEquals("Bearer $newAccess", server.takeRequest().headers["Authorization"])
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `second 401 drops only the access token`() {
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 401))
        val response = get("/transactions")
        assertEquals(401, response.code)
        response.close()
        assertNull(store.values[TokenKey.ACCESS])
        assertEquals("refresh-user-1", store.values[TokenKey.REFRESH])
    }
}
