package com.spenditos.android.data.purchases

import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.entitlement.ProAccess
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.data.model.TolerantPremiumAccessSerializer
import com.spenditos.android.data.model.TolerantPremiumStatusSerializer
import com.spenditos.android.data.model.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

/** `POST /payments/token` (packages/functions/src/api/payments.ts). */
@Serializable
data class PaymentsToken(val token: String, val expiresAt: String? = null)

/** `POST /payments/sync`: the joined premium after the server pulled it from Paykit. */
@Serializable
data class PaymentsSyncResponse(
    val applied: Boolean = false,
    @Serializable(with = TolerantPremiumAccessSerializer::class)
    val premium: PremiumAccess? = null,
    @Serializable(with = TolerantPremiumStatusSerializer::class)
    val premiumStatus: PremiumStatus? = null,
)

interface PaymentsApi {
    @POST("payments/token")
    suspend fun token(): ApiEnvelope<PaymentsToken>

    @POST("payments/sync")
    suspend fun sync(): ApiEnvelope<PaymentsSyncResponse>
}

enum class PaymentsSyncResult { SYNCED, UNAVAILABLE, FAILED }

/** The Spenditos backend's side of payments. */
interface PaymentsBackend {
    /** The signed-in user; a Paykit instance belongs to exactly one. */
    fun currentUserId(): String?

    /** A Paykit user token. Throws [PaymentsUnavailableException] on 503, [ApiError] otherwise. */
    suspend fun token(): String

    /** Pulls access from Paykit into the server's copy and applies the answer to `ProAccess`. Never throws. */
    suspend fun sync(): PaymentsSyncResult

    /** [sync], only when Paykit's [version] is newer than the premium we hold. */
    suspend fun syncIfBehind(version: Long)

    /** Forgets the token (sign-out). */
    fun clear()
}

@Singleton
class PaymentsRepository @Inject constructor(
    private val api: PaymentsApi,
    private val session: SessionManager,
    private val proAccess: ProAccess,
    private val clock: EpochClock,
) : PaymentsBackend {
    private data class CachedToken(val userId: String, val token: String, val expiresAt: Long)

    private val mutex = Mutex()
    @Volatile private var cached: CachedToken? = null

    override fun currentUserId(): String? = session.currentUserId

    /** Cached: the SDK asks on every request and the endpoint is rate limited. */
    override suspend fun token(): String = mutex.withLock {
        // Bound before the request: a token minted across an account switch is never kept.
        val binding = session.currentBinding ?: throw ApiError.Unauthorized
        cached?.takeIf { it.userId == binding.userId && it.expiresAt - clock.nowSeconds() > TOKEN_MARGIN_SECONDS }?.let { return@withLock it.token }
        val minted = try {
            apiCall { api.token() }
        } catch (e: ApiError.Server) {
            if (e.status == 503) throw PaymentsUnavailableException()
            throw e
        }
        val expiresAt = PremiumStatus.parseInstant(minted.expiresAt)?.epochSecond ?: 0
        if (session.currentBinding == binding) cached = CachedToken(binding.userId, minted.token, expiresAt)
        minted.token
    }

    override suspend fun sync(): PaymentsSyncResult {
        val binding = session.currentBinding ?: return PaymentsSyncResult.FAILED
        return try {
            val synced = apiCall { api.sync() }
            proAccess.apply(UserSettings(binding.userId, premium = synced.premium, premiumStatus = synced.premiumStatus), binding)
            PaymentsSyncResult.SYNCED
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.Server) {
            if (e.status == 503) PaymentsSyncResult.UNAVAILABLE else PaymentsSyncResult.FAILED
        } catch (_: Exception) {
            PaymentsSyncResult.FAILED
        }
    }

    override suspend fun syncIfBehind(version: Long) {
        if (version > (proAccess.current().premium?.version ?: -1)) sync()
    }

    override fun clear() {
        cached = null
    }

    private companion object {
        const val TOKEN_MARGIN_SECONDS = 60L
    }
}
