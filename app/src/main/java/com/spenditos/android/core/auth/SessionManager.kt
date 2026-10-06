package com.spenditos.android.core.auth

import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.AuthRequiredException
import com.spenditos.android.core.network.KeystoreUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val email: String?) : SessionState
}

data class AuthTokens(val accessToken: String, val refreshToken: String, val idToken: String)

/** Calls `POST /auth/refresh {refreshToken}` → `{accessToken, idToken}` (refresh token is not rotated). */
fun interface TokenRefresher {
    suspend fun refresh(refreshToken: String): RefreshedTokens
}

data class RefreshedTokens(val accessToken: String, val idToken: String)

/** Wall clock in epoch seconds; injectable for tests. */
fun interface EpochClock {
    fun nowSeconds(): Long
}

/**
 * Owns the signed-in session. Same rules as iOS `TokenManager`:
 * - proactive refresh when the access token expires within 300 s;
 * - one refresh at a time, shared by every caller (single flight);
 * - a refresh that finishes after sign-out/sign-in (session generation changed) is dropped;
 * - local auth state is cleared ONLY when `/auth/refresh` itself returns 401 (or the
 *   refresh token is missing). Network/5xx/Keystore failures keep the refresh token.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher,
    private val clock: EpochClock,
    private val environment: AppEnvironment,
) {
    private val generation = AtomicLong(0)
    private val refreshMutex = Mutex()
    private var inFlight: Deferred<Unit>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server ended the session (refresh rejected), not on user sign-out. */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    val currentUserId: String? get() = (state.value as? SessionState.SignedIn)?.userId

    private fun initialState(): SessionState {
        if (environment.isHarness) {
            return SessionState.SignedIn(environment.harnessToken.removePrefix("e2e-"), null)
        }
        return try {
            val hasSession = tokenStore.read(TokenKey.ACCESS) != null || tokenStore.read(TokenKey.REFRESH) != null
            val idToken = tokenStore.read(TokenKey.ID)
            val userId = idToken?.let(Jwt::subject)
            if (hasSession && userId != null) SessionState.SignedIn(userId, Jwt.email(idToken)) else SessionState.SignedOut
        } catch (_: KeystoreUnavailableException) {
            // Can't tell yet; don't destroy anything. The next token read decides.
            SessionState.SignedOut
        }
    }

    fun signIn(tokens: AuthTokens) {
        val userId = Jwt.subject(tokens.idToken) ?: throw ApiError.Decoding(IllegalStateException("id token has no sub"))
        generation.incrementAndGet()
        tokenStore.write(TokenKey.ACCESS, tokens.accessToken)
        tokenStore.write(TokenKey.REFRESH, tokens.refreshToken)
        tokenStore.write(TokenKey.ID, tokens.idToken)
        _state.value = SessionState.SignedIn(userId, Jwt.email(tokens.idToken))
    }

    /** User-initiated sign-out. The caller revokes the refresh token server-side first (best effort). */
    fun signOut() {
        clearTokens()
        _state.value = SessionState.SignedOut
    }

    /** The refresh token, for `POST /auth/logout`. */
    fun refreshTokenOrNull(): String? = try {
        tokenStore.read(TokenKey.REFRESH)
    } catch (_: KeystoreUnavailableException) {
        null
    }

    /**
     * A usable access token, refreshing first when it is missing or about to expire.
     * Throws [AuthRequiredException] when there is no session and
     * [KeystoreUnavailableException] when storage can't be read (both IOExceptions,
     * safe to throw from an OkHttp interceptor).
     */
    suspend fun validAccessToken(): String {
        if (environment.isHarness) return environment.harnessToken
        val access = tokenStore.read(TokenKey.ACCESS)
        if (access != null && !Jwt.isExpired(access, clock.nowSeconds())) return access
        if (tokenStore.read(TokenKey.REFRESH) == null) {
            expire()
            throw AuthRequiredException()
        }
        refresh()
        return tokenStore.read(TokenKey.ACCESS) ?: throw KeystoreUnavailableException()
    }

    /** Single-flight refresh; concurrent callers await the same request. */
    suspend fun refresh() {
        if (environment.isHarness) return
        val job = refreshMutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { performRefresh() }.also { inFlight = it }
        }
        job.await()
    }

    /** A second 401 after a refresh: drop only the access token, keep the refresh token. */
    fun invalidateAccessToken() {
        tokenStore.write(TokenKey.ACCESS, null)
    }

    private suspend fun performRefresh() {
        val refreshToken = tokenStore.read(TokenKey.REFRESH) ?: run {
            expire()
            throw AuthRequiredException()
        }
        val startedAt = generation.get()
        val result = try {
            refresher.refresh(refreshToken)
        } catch (e: ApiError.Server) {
            if (e.status == 401) {
                if (generation.get() == startedAt) expire()
                throw AuthRequiredException()
            }
            throw e.asIOException()
        } catch (e: ApiError) {
            // Network, decoding, keystore: transient. Keep the session.
            throw e.asIOException()
        }
        // Signed out or signed in again while the request was in flight: drop the result.
        if (generation.get() != startedAt || tokenStore.read(TokenKey.REFRESH) != refreshToken) return
        tokenStore.write(TokenKey.ACCESS, result.accessToken)
        tokenStore.write(TokenKey.ID, result.idToken)
    }

    private fun expire() {
        clearTokens()
        if (_state.value != SessionState.SignedOut) {
            _state.value = SessionState.SignedOut
            _expired.tryEmit(Unit)
        }
    }

    private fun clearTokens() {
        generation.incrementAndGet()
        TokenKey.entries.forEach { tokenStore.write(it, null) }
    }

    private fun ApiError.asIOException(): java.io.IOException = when (this) {
        is ApiError.KeystoreUnavailable -> KeystoreUnavailableException(this)
        else -> java.io.IOException(message, this)
    }
}
