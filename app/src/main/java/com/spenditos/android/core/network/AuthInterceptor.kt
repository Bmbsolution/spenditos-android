package com.spenditos.android.core.network

import com.spenditos.android.core.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the bearer token and handles a 401 exactly like iOS `APIClient`:
 * refresh once (single flight), retry once; a second 401 drops only the access
 * token and returns the 401 to the caller. `/auth/` routes are public and pass through.
 */
class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/auth/")) return chain.proceed(request)

        val token = runBlocking { session.validAccessToken() }
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        if (response.code != 401) return response

        response.close()
        runBlocking { session.refresh() }
        val retryToken = runBlocking { session.validAccessToken() }
        val retry = chain.proceed(request.newBuilder().header("Authorization", "Bearer $retryToken").build())
        if (retry.code == 401) {
            // Could be authorizer propagation; keep the refresh token so the next call recovers.
            session.invalidateAccessToken()
        }
        return retry
    }
}
