package com.spenditos.android.data.entitlement

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.TestJwt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProAccessRepositoryTest {
    private class Api : UserApi {
        var calls = 0
        var gate: CompletableDeferred<UserSettings>? = null
        var next: UserSettings = UserSettings("user-1")
        override suspend fun settings(): ApiEnvelope<UserSettings> {
            calls++
            return ApiEnvelope(data = gate?.await() ?: next)
        }
        override suspend fun updateSettings(body: UserSettingsUpdate): ApiEnvelope<UserSettings> = error("not used")
        override suspend fun changePassword(body: ChangePasswordRequest) = error("not used")
        override suspend fun deleteAccount() = error("not used")
    }

    private var now = NOW
    private val api = Api()
    private val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { now }, AppEnvironment())
    private val foreground = MutableStateFlow(false)

    private fun repository(scope: kotlinx.coroutines.CoroutineScope, store: StoreEntitlement = InertStoreEntitlement()) = ProAccessRepository(
        api, session, store,
        object : ForegroundMonitor { override val isForeground: StateFlow<Boolean> = foreground },
        EpochClock { now }, scope,
    )

    private fun premium(reason: String = "welcome", on: Boolean = true) = UserSettings(
        userId = "user-1",
        premiumStatus = PremiumStatus(isPremium = on, expiresAt = "2026-11-12T00:00:00.000Z", reason = reason),
    )

    private fun signInAs(userId: String) {
        session.signOut()
        session.signIn(AuthTokens(TestJwt.token(userId, NOW + 3600, nonce = "a-$userId"), "refresh-$userId", TestJwt.token(userId, NOW + 3600, nonce = "i-$userId")))
    }

    @Test
    fun `a settings response sets effective pro, and nothing means free`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        assertFalse(repo.effectiveIsPro)

        repo.apply(premium(), session.currentBinding)
        assertTrue(repo.effectiveIsPro)
        assertTrue(repo.current().isWelcomeOnly)
        assertTrue(repo.state.value.effectiveIsPro)

        repo.apply(premium(on = false, reason = "none"), session.currentBinding)
        assertFalse(repo.effectiveIsPro)
    }

    @Test
    fun `an old server without premiumStatus keeps the pro it had through the raw premium flag`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.apply(UserSettings("user-1", premium = PremiumAccess(active = true)), session.currentBinding)
        assertTrue(repo.effectiveIsPro)
        repo.apply(UserSettings("user-1", premium = PremiumAccess(active = false)), session.currentBinding)
        assertFalse(repo.effectiveIsPro)
    }

    @Test
    fun `the store entitlement is one input`() = runTest(UnconfinedTestDispatcher()) {
        val store = object : StoreEntitlement { override val isActive = MutableStateFlow(true) }
        val repo = repository(backgroundScope, store)
        assertTrue(repo.effectiveIsPro)
        assertFalse(repo.current().isWelcomeOnly)
        repo.apply(premium(), session.currentBinding)
        assertFalse("a store entitlement is not welcome-only", repo.current().isWelcomeOnly)
    }

    @Test
    fun `sign-out clears premium and the next account never sees it`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.start()
        repo.apply(premium(), session.currentBinding)
        assertTrue(repo.effectiveIsPro)

        session.signOut()
        assertFalse("signed out: no premium, immediately", repo.effectiveIsPro)
        advanceUntilIdle()
        assertNull(repo.current().serverStatus)

        signInAs("user-2")
        assertFalse("another account starts free", repo.effectiveIsPro)
        assertFalse(repo.state.value.effectiveIsPro)
    }

    @Test
    fun `an account switch hides the previous account's premium even before anything clears it`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope) // start() not called: the derived state alone must hold
        repo.apply(premium(), session.currentBinding)
        signInAs("user-2")
        assertFalse(repo.effectiveIsPro)
        assertFalse(repo.state.value.effectiveIsPro)
    }

    @Test
    fun `an in-flight refresh never applies a previous account's status`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        api.gate = CompletableDeferred()
        val refresh = async { repo.refresh(force = true) }
        assertEquals(1, api.calls)

        signInAs("user-2") // switch while the request is in flight
        api.gate!!.complete(premium())
        refresh.await()

        assertFalse("user-1's response must not become user-2's Premium", repo.effectiveIsPro)
        assertNull(repo.current().serverStatus)
    }

    @Test
    fun `a response bound to a signed-out session is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        val binding = session.currentBinding
        session.signOut()
        repo.apply(premium(), binding)
        signInAs("user-1")
        assertFalse(repo.effectiveIsPro)
        repo.apply(premium(), null)
        assertFalse(repo.effectiveIsPro)
    }

    @Test
    fun `refresh is throttled to once a minute unless forced`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        api.next = premium()
        repo.refresh()
        assertEquals(1, api.calls)
        assertTrue(repo.effectiveIsPro)

        now += 30
        repo.refresh()
        assertEquals("within the minute", 1, api.calls)

        repo.refresh(force = true)
        assertEquals(2, api.calls)

        now += 61
        repo.refresh()
        assertEquals(3, api.calls)
    }

    @Test
    fun `coming to the foreground refreshes server premium`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        api.next = premium()
        repo.start()
        assertEquals("not in the foreground yet", 0, api.calls)
        foreground.value = true
        advanceUntilIdle()
        assertEquals(1, api.calls)
        assertTrue(repo.effectiveIsPro)
    }

    @Test
    fun `a failed refresh keeps the last known status`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.apply(premium(), session.currentBinding)
        api.gate = CompletableDeferred<UserSettings>().also { it.completeExceptionally(java.io.IOException("offline")) }
        repo.refresh(force = true)
        assertTrue(repo.effectiveIsPro)
    }
}
