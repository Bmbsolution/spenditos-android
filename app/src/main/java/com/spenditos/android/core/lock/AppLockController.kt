package com.spenditos.android.core.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

enum class LockState { UNKNOWN, LOCKED, UNLOCKED }

fun interface ElapsedClock { fun elapsedMillis(): Long }

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppLockScope

/**
 * Owns the lock state. UNKNOWN covers the instant between process start and the first read of the
 * setting, so a restored session never flashes content before the lock decision is made.
 */
@Singleton
class AppLockController @Inject constructor(
    private val session: SessionManager,
    private val settings: AppLockSettings,
    private val authenticator: BiometricAuthenticator,
    private val clock: ElapsedClock,
    @AppLockScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundedAt: Long? = null
    private var started = false

    fun start(lifecycleOwner: LifecycleOwner = ProcessLifecycleOwner.get()) {
        if (started) return
        started = true
        val restoredAtLaunch = session.state.value is SessionState.SignedIn
        scope.launch {
            val enabled = settings.enabled.first()
            val usable = authenticator.availability() == LockAvailability.AVAILABLE
            val lock = AppLockPolicy.shouldLockOnColdLaunch(
                isSignedIn = session.state.value is SessionState.SignedIn,
                restoredAtLaunch = restoredAtLaunch,
                lockEnabled = enabled && usable,
            )
            _state.value = if (lock) LockState.LOCKED else LockState.UNLOCKED
            // The credential was removed since the lock was switched on: it cannot protect anything now.
            if (enabled && !usable) settings.setEnabled(false)
        }
        scope.launch {
            session.state.collect { s -> if (s is SessionState.SignedOut && _state.value == LockState.LOCKED) _state.value = LockState.UNLOCKED }
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { onBackgrounded() }
            override fun onStart(owner: LifecycleOwner) { onForegrounded() }
        })
    }

    internal fun onBackgrounded() { backgroundedAt = clock.elapsedMillis() }

    internal fun onForegrounded() {
        val away = backgroundedAt?.let { clock.elapsedMillis() - it }
        backgroundedAt = null
        scope.launch {
            val enabled = settings.enabled.first() && authenticator.availability() == LockAvailability.AVAILABLE
            if (AppLockPolicy.shouldLockOnForeground(
                    backgroundMillis = away,
                    isSignedIn = session.state.value is SessionState.SignedIn,
                    lockEnabled = enabled,
                    alreadyLocked = _state.value == LockState.LOCKED,
                )
            ) _state.value = LockState.LOCKED
        }
    }

    fun unlock() { _state.value = LockState.UNLOCKED }
}

@Module
@InstallIn(SingletonComponent::class)
object AppLockModule {
    @Provides
    @Singleton
    @AppLockScope
    fun lockScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun elapsedClock(): ElapsedClock = ElapsedClock { SystemClock.elapsedRealtime() }

    @Provides
    @Singleton
    fun lockSettings(impl: DataStoreAppLockSettings): AppLockSettings = impl

    @Provides
    @Singleton
    fun authenticator(impl: SystemBiometricAuthenticator): BiometricAuthenticator = impl
}
