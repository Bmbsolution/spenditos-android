package com.spenditos.android.core.network

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenKey
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val refreshes = AtomicInteger()
    private val newAccess = TestJwt.token("user-1", NOW + 7200, nonce = "new")
    private lateinit var store: FakeTokenStore
    private lateinit var session: SessionManager
    private lateinit var client: OkHttpClient
    private val executor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        server.start()
        store = FakeTokenStore.signedIn()
        session = SessionManager(store, { refreshes.incrementAndGet(); RefreshedTokens(newAccess, TestJwt.token("user-1", NOW + 7200, nonce = "id2")) }, EpochClock { NOW }, AppEnvironment())
        client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build()
    }

    @After
    fun tearDown() {
        executor.shutdown()
        server.close()
    }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private fun getBound(path: String, binding: SessionBinding) =
        client.newCall(Request.Builder().url(server.url(path)).tag(SessionBinding::class.java, binding).build()).execute()

    private fun switchToSecondAccount() {
        session.signOut()
        store.values[TokenKey.ACCESS] = TestJwt.token("user-2", NOW + 3600, nonce = "b-access")
        store.values[TokenKey.REFRESH] = "refresh-user-2"
        store.values[TokenKey.ID] = TestJwt.token("user-2", NOW + 3600, nonce = "b-id")
        session.signIn(AuthTokens(TestJwt.token("user-2", NOW + 3600, nonce = "b-access"), "refresh-user-2", TestJwt.token("user-2", NOW + 3600, nonce = "b-id")))
    }

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

    @Test
    fun `a request bound to a previous session is refused instead of using the new account's token`() {
        val binding = session.currentBinding!!
        switchToSecondAccount()
        try {
            getBound("/transactions", binding).close()
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
        assertEquals(0, server.requestCount)
    }

    /**
     * A client whose inner interceptor runs [afterResponse] once the server's reply is read but
     * before [AuthInterceptor] handles it, so a session change lands at an exact point (no timing).
     */
    private fun clientChangingSessionOnReply(afterResponse: () -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor { chain -> chain.proceed(chain.request()).also { afterResponse() } }
            .build()

    private fun executeBound(client: OkHttpClient, binding: SessionBinding) {
        client.newCall(Request.Builder().url(server.url("/transactions")).tag(SessionBinding::class.java, binding).build())
            .execute().close()
    }

    @Test
    fun `a bound request is refused, not rejected as unauthenticated, when the session ended before it was sent`() {
        val binding = session.currentBinding!!
        session.signOut() // tokens gone: an unbound request would fail with AuthRequiredException
        try {
            getBound("/transactions", binding).close()
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the 401 retry of a bound request aborts when the account switched before the refresh`() {
        val binding = session.currentBinding!!
        server.enqueue(MockResponse(code = 401))
        val client = clientChangingSessionOnReply(::switchToSecondAccount)

        try {
            executeBound(client, binding)
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
        assertEquals(1, server.requestCount) // no retry with the new account's token
        assertTrue(store.values.containsKey(TokenKey.ACCESS)) // the new session's tokens are untouched
    }

    @Test
    fun `the 401 retry of a bound request aborts when the session ended before the refresh`() {
        val binding = session.currentBinding!!
        server.enqueue(MockResponse(code = 401))
        val client = clientChangingSessionOnReply(session::signOut)

        try {
            executeBound(client, binding)
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
        assertEquals(1, server.requestCount)
        assertEquals(0, refreshes.get())
    }

    @Test
    fun `the 401 retry of a bound request aborts when the account switched during the refresh`() {
        lateinit var switching: SessionManager
        switching = SessionManager(
            store,
            {
                switching.signOut()
                switching.signIn(AuthTokens(TestJwt.token("user-2", NOW + 3600, nonce = "b"), "refresh-user-2", TestJwt.token("user-2", NOW + 3600, nonce = "bid")))
                RefreshedTokens(newAccess, TestJwt.token("user-1", NOW + 7200, nonce = "id2"))
            },
            EpochClock { NOW },
            AppEnvironment(),
        )
        val binding = switching.currentBinding!!
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(switching)).build()
        server.enqueue(MockResponse(code = 401))

        try {
            executeBound(client, binding)
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
        assertEquals(1, server.requestCount)
    }
}
