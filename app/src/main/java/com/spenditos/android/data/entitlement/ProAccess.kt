package com.spenditos.android.data.entitlement

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.core.network.apiCall
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Store (Play Billing) entitlement. Inert on Android: purchases are being replaced, so it is always false. */
interface StoreEntitlement {
    val isActive: StateFlow<Boolean>
}

class InertStoreEntitlement @Inject constructor() : StoreEntitlement {
    override val isActive: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}

/**
 * The one rule for "is this user Pro": the store entitlement OR the server's `premiumStatus.isPremium`
 * (welcome Premium, admin grants, store). A server that predates `premiumStatus` sends only the raw
 * `premium.active`, which stands in for it so those accounts keep the Pro they had.
 */
fun resolveEffectivePro(storeIsPro: Boolean, serverStatus: PremiumStatus?, legacyPremiumActive: Boolean = false): Boolean =
    storeIsPro || (serverStatus?.isPremium ?: legacyPremiumActive)

/** What the app knows about the signed-in account's Pro access right now. */
data class ProAccessState(
    val storeIsPro: Boolean = false,
    /** Latest `premiumStatus` from a settings response; null until settings load, and on old servers. */
    val serverStatus: PremiumStatus? = null,
    /** Raw `premium.active` from the same response (used only while [serverStatus] is null). */
    val legacyPremiumActive: Boolean = false,
) {
    /** Every Pro gate uses this. */
    val effectiveIsPro: Boolean get() = resolveEffectivePro(storeIsPro, serverStatus, legacyPremiumActive)

    /** Pro only through the welcome period (no subscription or grant): the Pro screen should still sell. */
    val isWelcomeOnly: Boolean get() = !storeIsPro && serverStatus?.isWelcome == true

    /** Pro that will not lapse by itself soon: a subscription or an admin grant. */
    val hasLastingPro: Boolean get() = effectiveIsPro && !isWelcomeOnly
}

/**
 * Effective Pro for the signed-in account. Read [state] (or [current] for a synchronous decision),
 * never `UserSettings.premium` directly.
 */
interface ProAccess {
    val state: StateFlow<ProAccessState>

    /** Synchronous snapshot, always for the account signed in right now (never a previous one). */
    fun current(): ProAccessState

    val effectiveIsPro: Boolean get() = current().effectiveIsPro

    /**
     * Records the premium state from a settings response that was requested under [binding]
     * (taken before the request). Ignored when the session changed meanwhile, so a late response
     * can never apply a previous account's status.
     */
    fun apply(settings: UserSettings, binding: SessionBinding?)

    /** Reloads `/user/settings` for the premium state; throttled to once a minute unless [force]. Best effort. */
    suspend fun refresh(force: Boolean = false)

    /** Forgets the server status (sign-out / account change). */
    fun clear()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProAccessScope

@Singleton
class ProAccessRepository @Inject constructor(
    private val api: UserApi,
    private val session: SessionManager,
    private val store: StoreEntitlement,
    private val foreground: ForegroundMonitor,
    private val clock: EpochClock,
    @ProAccessScope private val scope: CoroutineScope,
) : ProAccess {
    private data class Held(val userId: String, val status: PremiumStatus?, val legacyActive: Boolean, val appliedAt: Long)

    private val held = MutableStateFlow<Held?>(null)
    private var started = false

    private fun compute(held: Held?, sessionState: SessionState, storeIsPro: Boolean): ProAccessState {
        val mine = held?.takeIf { (sessionState as? SessionState.SignedIn)?.userId == it.userId }
        return ProAccessState(storeIsPro = storeIsPro, serverStatus = mine?.status, legacyPremiumActive = mine?.legacyActive == true)
    }

    override val state: StateFlow<ProAccessState> = combine(held, session.state, store.isActive, ::compute)
        .stateIn(scope, SharingStarted.Eagerly, compute(held.value, session.state.value, store.isActive.value))

    override fun current(): ProAccessState = compute(held.value, session.state.value, store.isActive.value)

    /** Starts once per process: refresh on foreground (throttled) and drop the status when the account changes. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            foreground.isForeground.collect { isForeground -> if (isForeground) refresh() }
        }
        scope.launch {
            session.state.collect { sessionState ->
                val owner = held.value?.userId ?: return@collect
                if ((sessionState as? SessionState.SignedIn)?.userId != owner) clear()
            }
        }
    }

    override fun apply(settings: UserSettings, binding: SessionBinding?) {
        if (binding == null || session.currentBinding != binding) return
        held.value = Held(binding.userId, settings.premiumStatus, settings.premium?.active == true, clock.nowSeconds())
    }

    override suspend fun refresh(force: Boolean) {
        val binding = session.currentBinding ?: return
        if (!force) {
            val last = held.value?.takeIf { it.userId == binding.userId }?.appliedAt
            if (last != null && clock.nowSeconds() - last < REFRESH_INTERVAL_SECONDS) return
        }
        try {
            val settings = apiCall { api.settings() }
            apply(settings, binding)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort; keep the last known value.
        }
    }

    override fun clear() {
        held.value = null
    }

    companion object {
        const val REFRESH_INTERVAL_SECONDS = 60L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProAccessModule {
    @Binds abstract fun proAccess(impl: ProAccessRepository): ProAccess

    @Binds abstract fun storeEntitlement(impl: InertStoreEntitlement): StoreEntitlement

    companion object {
        @Provides
        @Singleton
        @ProAccessScope
        fun proAccessScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
