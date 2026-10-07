package com.spenditos.android.data.push

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class PushRegistrarTest {
    private class FakeApi : PushApi {
        val registered = mutableListOf<DeviceTokenRequest>()
        val unregistered = mutableListOf<String>()
        var failRegister: Exception? = null
        override suspend fun register(body: DeviceTokenRequest) {
            failRegister?.let { throw it }
            registered += body
        }
        override suspend fun unregister(body: UnregisterDeviceTokenRequest) { unregistered += body.token }
    }

    private class FakeSettings(enabled: Boolean) : PushSettings {
        val flow = MutableStateFlow(enabled)
        var last: String? = null
        override val enabled = flow
        override suspend fun setEnabled(value: Boolean) { flow.value = value }
        override suspend fun lastRegistered() = last
        override suspend fun setLastRegistered(value: String?) { last = value }
    }

    private fun session(signedIn: Boolean = true) = SessionManager(
        if (signedIn) FakeTokenStore.signedIn("user-1") else FakeTokenStore(),
        { RefreshedTokens("access", "id") },
        EpochClock { NOW },
        AppEnvironment(),
    )

    private val token = "fcm-token-with-more-than-16-chars"

    private fun registrar(api: FakeApi, settings: FakeSettings, session: SessionManager = session(), fcm: String? = token) =
        PushRegistrar(api, session, { fcm }, settings, AppVersion("com.asaklex.spenditos", "0.1.0"))

    @Test
    fun `registers once per user and token while notifications are on`() = runTest {
        val api = FakeApi(); val settings = FakeSettings(true)
        val registrar = registrar(api, settings)
        registrar.syncRegistration()
        registrar.syncRegistration()
        assertEquals(1, api.registered.size)
        assertEquals(DeviceTokenRequest(token, "android", "com.asaklex.spenditos", "0.1.0"), api.registered.single())
        assertEquals("user-1|$token", settings.last)
    }

    @Test
    fun `a rotated token registers again`() = runTest {
        val api = FakeApi(); val settings = FakeSettings(true)
        val registrar = registrar(api, settings)
        registrar.syncRegistration()
        registrar.syncRegistration(newToken = "a-brand-new-fcm-token-value")
        assertEquals(listOf(token, "a-brand-new-fcm-token-value"), api.registered.map { it.token })
    }

    @Test
    fun `nothing is sent when notifications are off, signed out, or Firebase is not configured`() = runTest {
        val off = FakeApi(); registrar(off, FakeSettings(false)).syncRegistration()
        val out = FakeApi(); registrar(out, FakeSettings(true), session(signedIn = false)).syncRegistration()
        val noFcm = FakeApi(); registrar(noFcm, FakeSettings(true), fcm = null).syncRegistration()
        assertEquals(0, off.registered.size + out.registered.size + noFcm.registered.size)
    }

    @Test
    fun `a failed registration is swallowed and retried next time`() = runTest {
        val api = FakeApi().apply { failRegister = IOException("offline") }
        val settings = FakeSettings(true)
        val registrar = registrar(api, settings)
        registrar.syncRegistration()
        assertNull(settings.last)
        api.failRegister = null
        registrar.syncRegistration()
        assertEquals(1, api.registered.size)
    }

    @Test
    fun `switching off unregisters the stored token and forgets it`() = runTest {
        val api = FakeApi(); val settings = FakeSettings(true)
        val registrar = registrar(api, settings)
        registrar.syncRegistration()
        registrar.setEnabled(false)
        assertEquals(listOf(token), api.unregistered)
        assertNull(settings.last)
        assertEquals(false, settings.flow.value)
    }

    @Test
    fun `switching on registers right away`() = runTest {
        val api = FakeApi(); val settings = FakeSettings(false)
        registrar(api, settings).setEnabled(true)
        assertEquals(1, api.registered.size)
    }

    @Test
    fun `notification types map to the iOS deep links`() {
        assertEquals(DeepLink.ADD_TRANSACTION, DeepLink.fromType("streak_reminder"))
        assertEquals(DeepLink.ADD_TRANSACTION, DeepLink.fromType("add_transaction"))
        assertEquals(DeepLink.PROGRESS, DeepLink.fromType("challenge"))
        assertEquals(DeepLink.PROGRESS, DeepLink.fromType("challenge_complete"))
        assertEquals(DeepLink.PROGRESS, DeepLink.fromType("achievement"))
        assertEquals(DeepLink.PROGRESS, DeepLink.fromType("level_up"))
        assertNull(DeepLink.fromType("something_else"))
        assertNull(DeepLink.fromType(null))
    }
}
