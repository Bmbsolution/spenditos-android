package com.spenditos.android.data.auth

import com.spenditos.android.core.auth.AuthTokens
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.TokenRefresher
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.data.model.LoginRequest
import com.spenditos.android.data.model.LogoutRequest
import com.spenditos.android.data.model.RefreshRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val session: SessionManager,
    private val push: com.spenditos.android.data.push.PushRegistrar,
    private val purchases: com.spenditos.android.data.purchases.PurchasesService,
    private val userApi: com.spenditos.android.data.user.UserApi,
    private val transactions: com.spenditos.android.data.transactions.TransactionRepository,
) {
    /** Throws ApiError.Server(401) for wrong credentials, Server(403) for an unconfirmed email. */
    suspend fun login(email: String, password: String) {
        val response = apiCall { api.login(LoginRequest(email.trim(), password)) }
        session.signIn(AuthTokens(response.accessToken, response.refreshToken, response.idToken))
    }

    /** Revokes the refresh token (best effort, like iOS) and clears local auth state. */
    suspend fun signOut() {
        // While the access token still works: stop pushes for this user on this device.
        runCatching { push.unregister() }
        runCatching { purchases.logOut() }
        session.refreshTokenOrNull()?.let { token ->
            runCatching { apiCallUnit { api.logout(LogoutRequest(token)) } }
        }
        session.signOut()
    }

    /**
     * Asks the server to delete the account (soft delete, 30 days to cancel through support), then
     * signs out like iOS. Throws when the server call fails; the user stays signed in to retry.
     */
    suspend fun deleteAccount() {
        apiCallUnit { userApi.deleteAccount() }
        // The account is gone server-side: finish the local cleanup even if this coroutine is
        // cancelled (the user left the screen), or the device would stay signed in. Everything
        // after the server's yes is local, so it runs uncancellable; the best-effort wrappers
        // rethrow CancellationException so cancellation is never swallowed.
        withContext(NonCancellable) {
            bestEffort { push.unregister() }
            bestEffort { purchases.logOut() }
            // The account is gone, so its queued creates can never sync; drop them while
            // the user id is still known. A plain sign-out keeps the queue.
            bestEffort { transactions.dropQueue() }
            session.signOut()
        }
    }

    private inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

/** [TokenRefresher] backed by the public auth API (no auth interceptor, so no recursion). */
class ApiTokenRefresher @Inject constructor(private val api: AuthApi) : TokenRefresher {
    override suspend fun refresh(refreshToken: String): RefreshedTokens {
        val response = apiCall { api.refresh(RefreshRequest(refreshToken)) }
        return RefreshedTokens(response.accessToken, response.idToken)
    }
}
