package com.spenditos.android.core.auth

import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.AuthRequiredException
import com.spenditos.android.core.network.KeystoreUnavailableException
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SessionManagerTest {
    private val clock = EpochClock { NOW }
    private val fresh = RefreshedTokens(TestJwt.token("user-1", NOW + 7200, nonce = "new"), TestJwt.token("user-1", NOW + 7200, nonce = "newid"))

    private fun manager(store: FakeTokenStore, refresher: TokenRefresher) = SessionManager(store, refresher, clock, AppEnvironment())

    @Test
    fun `restores a saved session as signed in`() {
        val session = manager(FakeTokenStore.signedIn("user-42"), { fresh })
        assertEquals(SessionState.SignedIn("user-42", "test@example.test"), session.state.value)
    }

    @Test
    fun `valid token is returned without refreshing`() = runTest {
        val calls = AtomicInteger()
        val store = FakeTokenStore.signedIn()
        val token = manager(store, { calls.incrementAndGet(); fresh }).validAccessToken()
        assertEquals(store.values[TokenKey.ACCESS], token)
        assertEquals(0, calls.get())
    }

    @Test
    fun `token expiring within 300s is refreshed proactively`() = runTest {
        val store = FakeTokenStore.signedIn(accessExp = NOW + 200)
        val token = manager(store, { fresh }).validAccessToken()
        assertEquals(fresh.accessToken, token)
        assertEquals(fresh.idToken, store.values[TokenKey.ID])
        assertEquals("refresh-user-1", store.values[TokenKey.REFRESH]) // not rotated
    }

    @Test
    fun `concurrent callers share one refresh`() = runTest {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10)
        val session = manager(store) { calls.incrementAndGet(); gate.await(); fresh }
        val callers = List(5) { async { session.validAccessToken() } }
        testScheduler.advanceUntilIdle() // every caller is now parked on the same in-flight refresh
        gate.complete(Unit)
        val tokens = callers.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(tokens.all { it == fresh.accessToken })
    }

    @Test
    fun `refresh rejected with 401 clears the session and reports expiry`() = runTest {
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10)
        val session = manager(store) { throw ApiError.Server(401, "Invalid refresh token") }
        val expired = async(start = CoroutineStart.UNDISPATCHED) { session.expired.first() }
        try {
            session.validAccessToken()
            fail("expected AuthRequiredException")
        } catch (_: AuthRequiredException) {
        }
        assertTrue(store.values.isEmpty())
        assertEquals(SessionState.SignedOut, session.state.value)
        assertNotNull(expired.await())
    }

    @Test
    fun `transient refresh failure keeps the refresh token`() = runTest {
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10)
        val session = manager(store) { throw ApiError.Network(IOException("timeout")) }
        try {
            session.validAccessToken()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e !is AuthRequiredException)
        }
        assertEquals("refresh-user-1", store.values[TokenKey.REFRESH])
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun `server error during refresh keeps the session`() = runTest {
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10)
        val session = manager(store) { throw ApiError.Server(503, "unavailable") }
        runCatching { session.validAccessToken() }
        assertEquals("refresh-user-1", store.values[TokenKey.REFRESH])
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun `refresh finishing after sign-out is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10)
        val session = manager(store) { gate.await(); fresh }
        val pending = async { runCatching { session.refresh() } }
        testScheduler.advanceUntilIdle()
        session.signOut()
        gate.complete(Unit)
        pending.await()
        assertNull(store.values[TokenKey.ACCESS])
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `missing refresh token signs out`() = runTest {
        val store = FakeTokenStore.signedIn(accessExp = NOW - 10).apply { values.remove(TokenKey.REFRESH) }
        val session = manager(store) { fresh }
        runCatching { session.validAccessToken() }.onSuccess { fail("expected failure") }
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `keystore unavailable never clears tokens`() = runTest {
        val store = FakeTokenStore.signedIn()
        val session = manager(store) { fresh }
        store.unavailable = true
        try {
            session.validAccessToken()
            fail("expected KeystoreUnavailableException")
        } catch (_: KeystoreUnavailableException) {
        }
        store.unavailable = false
        assertEquals(3, store.values.size)
    }

    @Test
    fun `invalidating the access token keeps the refresh token`() {
        val store = FakeTokenStore.signedIn()
        manager(store, { fresh }).invalidateAccessToken()
        assertNull(store.values[TokenKey.ACCESS])
        assertEquals("refresh-user-1", store.values[TokenKey.REFRESH])
    }

    @Test
    fun `accessTokenFor serves the session it was bound to`() = runTest {
        val store = FakeTokenStore.signedIn()
        val session = manager(store, { fresh })
        assertEquals(store.values[TokenKey.ACCESS], session.accessTokenFor(session.currentBinding!!))
    }

    @Test
    fun `accessTokenFor refuses a binding from another account's session`() = runTest {
        val store = FakeTokenStore.signedIn("user-1")
        val session = manager(store, { fresh })
        val binding = session.currentBinding!!

        session.signOut()
        store.values[TokenKey.ACCESS] = TestJwt.token("user-2", NOW + 3600)
        store.values[TokenKey.REFRESH] = "refresh-user-2"
        store.values[TokenKey.ID] = TestJwt.token("user-2", NOW + 3600)
        session.signIn(AuthTokens(TestJwt.token("user-2", NOW + 3600), "refresh-user-2", TestJwt.token("user-2", NOW + 3600)))

        try {
            session.accessTokenFor(binding)
            fail("expected SessionChangedException")
        } catch (_: SessionChangedException) {
        }
    }

    @Test
    fun `signing in again as the same user changes the binding generation`() {
        val store = FakeTokenStore.signedIn("user-1")
        val session = manager(store, { fresh })
        val first = session.currentBinding!!

        session.signIn(AuthTokens(TestJwt.token("user-1", NOW + 3600, nonce = "again"), "refresh-user-1", TestJwt.token("user-1", NOW + 3600, nonce = "again-id")))

        val second = session.currentBinding!!
        assertEquals(first.userId, second.userId)
        assertTrue(second.generation > first.generation)
    }
}
