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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val session: SessionManager,
    private val push: com.spenditos.android.data.push.PushRegistrar,
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
        session.refreshTokenOrNull()?.let { token ->
            runCatching { apiCallUnit { api.logout(LogoutRequest(token)) } }
        }
        session.signOut()
    }
}

/** [TokenRefresher] backed by the public auth API (no auth interceptor, so no recursion). */
class ApiTokenRefresher @Inject constructor(private val api: AuthApi) : TokenRefresher {
    override suspend fun refresh(refreshToken: String): RefreshedTokens {
        val response = apiCall { api.refresh(RefreshRequest(refreshToken)) }
        return RefreshedTokens(response.accessToken, response.idToken)
    }
}
