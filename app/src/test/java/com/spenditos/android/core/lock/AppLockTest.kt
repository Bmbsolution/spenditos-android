package com.spenditos.android.core.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class AppLockPolicyTest {
    @Test
    fun `cold launch locks only a restored session with the lock on`() {
        assertTrue(AppLockPolicy.shouldLockOnColdLaunch(isSignedIn = true, restoredAtLaunch = true, lockEnabled = true))
        assertFalse(AppLockPolicy.shouldLockOnColdLaunch(isSignedIn = true, restoredAtLaunch = false, lockEnabled = true))
        assertFalse(AppLockPolicy.shouldLockOnColdLaunch(isSignedIn = false, restoredAtLaunch = true, lockEnabled = true))
        assertFalse(AppLockPolicy.shouldLockOnColdLaunch(isSignedIn = true, restoredAtLaunch = true, lockEnabled = false))
        assertFalse(AppLockPolicy.shouldLockOnColdLaunch(isSignedIn = true, restoredAtLaunch = true, lockEnabled = true, alreadyLocked = true))
    }

    @Test
    fun `foreground locks only after the grace period`() {
        val grace = AppLockPolicy.DEFAULT_GRACE_MILLIS
        assertFalse(AppLockPolicy.shouldLockOnForeground(grace - 1, isSignedIn = true, lockEnabled = true, alreadyLocked = false))
        assertTrue(AppLockPolicy.shouldLockOnForeground(grace, isSignedIn = true, lockEnabled = true, alreadyLocked = false))
        assertFalse(AppLockPolicy.shouldLockOnForeground(null, isSignedIn = true, lockEnabled = true, alreadyLocked = false))
        assertFalse(AppLockPolicy.shouldLockOnForeground(grace * 10, isSignedIn = false, lockEnabled = true, alreadyLocked = false))
        assertFalse(AppLockPolicy.shouldLockOnForeground(grace * 10, isSignedIn = true, lockEnabled = false, alreadyLocked = false))
        assertFalse(AppLockPolicy.shouldLockOnForeground(grace * 10, isSignedIn = true, lockEnabled = true, alreadyLocked = true))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppLockControllerTest {
    private class FakeSettings(initial: Boolean) : AppLockSettings {
        val flow = MutableStateFlow(initial)
        override val enabled = flow
        override suspend fun setEnabled(value: Boolean) { flow.value = value }
    }

    private class FakeAuthenticator(var availability: LockAvailability = LockAvailability.AVAILABLE) : BiometricAuthenticator {
        override fun availability() = availability
        override suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String?): LockAuthResult = LockAuthResult.Success
    }

    private var now = 0L
    private fun session(signedIn: Boolean) = SessionManager(
        if (signedIn) FakeTokenStore.signedIn("user-1") else FakeTokenStore(),
        { RefreshedTokens("access", "id") },
        EpochClock { NOW },
        AppEnvironment(),
    )

    private fun TestScope.controller(
        session: SessionManager,
        settings: AppLockSettings,
        auth: BiometricAuthenticator = FakeAuthenticator(),
    ): Pair<AppLockController, LifecycleRegistry> {
        val owner = object : LifecycleOwner { lateinit var registry: LifecycleRegistry; override val lifecycle: Lifecycle get() = registry }
        owner.registry = LifecycleRegistry.createUnsafe(owner)
        val controller = AppLockController(session, settings, auth, { now }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        controller.start(owner)
        return controller to owner.registry
    }

    @Test
    fun `restored session with the lock on starts locked`() = runTest {
        val (controller, _) = controller(session(signedIn = true), FakeSettings(true))
        assertEquals(LockState.LOCKED, controller.state.value)
    }

    @Test
    fun `restored session with the lock off starts unlocked`() = runTest {
        val (controller, _) = controller(session(signedIn = true), FakeSettings(false))
        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `signed-out launch is never locked`() = runTest {
        val (controller, _) = controller(session(signedIn = false), FakeSettings(true))
        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `unlock opens the lock and a short trip away keeps it open`() = runTest {
        val (controller, _) = controller(session(signedIn = true), FakeSettings(true))
        controller.unlock()
        now = 1_000
        controller.onBackgrounded()
        now = 1_000 + AppLockPolicy.DEFAULT_GRACE_MILLIS - 1
        controller.onForegrounded()
        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `a long trip away locks again`() = runTest {
        val (controller, _) = controller(session(signedIn = true), FakeSettings(true))
        controller.unlock()
        now = 1_000
        controller.onBackgrounded()
        now = 1_000 + AppLockPolicy.DEFAULT_GRACE_MILLIS
        controller.onForegrounded()
        assertEquals(LockState.LOCKED, controller.state.value)
    }

    @Test
    fun `a removed screen lock turns the setting off instead of trapping the user`() = runTest {
        val settings = FakeSettings(true)
        val (controller, _) = controller(session(signedIn = true), settings, FakeAuthenticator(LockAvailability.NOT_ENROLLED))
        assertEquals(LockState.UNLOCKED, controller.state.value)
        assertFalse(settings.flow.value)
    }
}
