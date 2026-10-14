package com.spenditos.android.data.quota

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.entitlement.ProAccess
import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.model.UserQuotaPayload
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.http.GET
import javax.inject.Inject
import javax.inject.Singleton

interface QuotaApi {
    /** `GET /user/quota`; snake_case keys inside `data.quota`. */
    @GET("user/quota")
    suspend fun quota(): ApiEnvelope<UserQuotaPayload>
}

/**
 * Monthly free-tier quotas for the signed-in account (voice 10, sentences 30, scan credits 10 by default).
 * Display and pre-checks only: the server gates and is the source of truth, and an unreachable server
 * never blocks the user (remaining counts then read as unlimited).
 */
interface QuotaSource {
    /** Latest overview for the account signed in now; null until loaded, offline, or after an account change. */
    val overview: StateFlow<QuotaOverview?>

    /** Synchronous snapshot of [overview]. */
    fun current(): QuotaOverview?

    /** Loads the overview when the cache is older than 5 minutes (or [force]); null when unreachable. */
    suspend fun refresh(force: Boolean = false): QuotaOverview?

    /** Drops the cache so the next read hits the server (after a metered action). */
    fun invalidate()

    /** Scan credits left; [QuotaOverview.UNLIMITED] for Pro, an unmetered bucket, or an unknown (offline) state. */
    suspend fun remainingScanCredits(): Int

    suspend fun remainingSentenceEntries(): Int

    suspend fun remainingVoiceEntries(): Int

    /** Snapshot for the sentence counter and banner; null while the server is unreachable. */
    suspend fun sentenceMeter(forceRefresh: Boolean = false): SentenceMeter?
}

@Singleton
class QuotaRepository @Inject constructor(
    private val api: QuotaApi,
    private val session: SessionManager,
    private val proAccess: ProAccess,
    private val clock: EpochClock,
    @QuotaScope scope: CoroutineScope,
) : QuotaSource {
    private data class Cached(val userId: String, val overview: QuotaOverview, val fetchedAt: Long)

    private val cache = MutableStateFlow<Cached?>(null)
    private val mutex = Mutex()

    private fun forUser(cached: Cached?, sessionState: SessionState): QuotaOverview? =
        cached?.takeIf { (sessionState as? SessionState.SignedIn)?.userId == it.userId }?.overview

    // Derived from the session, so a previous account's numbers are never visible to the next one.
    override val overview: StateFlow<QuotaOverview?> = combine(cache, session.state, ::forUser)
        .stateIn(scope, SharingStarted.Eagerly, forUser(cache.value, session.state.value))

    override fun current(): QuotaOverview? = forUser(cache.value, session.state.value)

    override suspend fun refresh(force: Boolean): QuotaOverview? = mutex.withLock {
        val binding = session.currentBinding ?: return null
        val cached = cache.value?.takeIf { it.userId == binding.userId }
        if (!force && cached != null && clock.nowSeconds() - cached.fetchedAt < TTL_SECONDS) return cached.overview
        try {
            val payload = apiCall { api.quota() }
            // Signed out or switched account while the request was in flight: drop it.
            if (session.currentBinding == binding) {
                cache.value = Cached(binding.userId, payload.quota, clock.nowSeconds())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort; keep what we had.
        }
        current()
    }

    override fun invalidate() {
        cache.value = cache.value?.copy(fetchedAt = Long.MIN_VALUE / 2)
    }

    private suspend fun overviewOrNull(): QuotaOverview? {
        if (session.currentUserId == null) return null
        return refresh()
    }

    override suspend fun remainingScanCredits(): Int {
        if (proAccess.effectiveIsPro) return QuotaOverview.UNLIMITED
        return overviewOrNull()?.remainingScanCredits ?: QuotaOverview.UNLIMITED
    }

    override suspend fun remainingSentenceEntries(): Int {
        if (proAccess.effectiveIsPro) return QuotaOverview.UNLIMITED
        return overviewOrNull()?.remainingSentenceEntries ?: QuotaOverview.UNLIMITED
    }

    override suspend fun remainingVoiceEntries(): Int {
        if (proAccess.effectiveIsPro) return QuotaOverview.UNLIMITED
        return overviewOrNull()?.remainingVoiceEntries ?: QuotaOverview.UNLIMITED
    }

    override suspend fun sentenceMeter(forceRefresh: Boolean): SentenceMeter? {
        if (proAccess.effectiveIsPro) return null
        val overview = (if (forceRefresh) refresh(force = true) else overviewOrNull()) ?: return null
        return SentenceMeter.from(overview)
    }

    private companion object {
        const val TTL_SECONDS = 300L
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class QuotaScope

@Module
@InstallIn(SingletonComponent::class)
object QuotaModule {
    @Provides
    @Singleton
    @QuotaScope
    fun quotaScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun quotaApi(retrofit: Retrofit): QuotaApi = retrofit.create(QuotaApi::class.java)

    @Provides
    @Singleton
    fun quotaSource(repository: QuotaRepository): QuotaSource = repository
}
