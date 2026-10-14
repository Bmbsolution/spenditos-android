package com.spenditos.android.core.network

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Adds the bearer token and handles a 401 exactly like iOS `APIClient`:
 * refresh once (single flight), retry once; a second 401 drops only the access
 * token and returns the 401 to the caller. `/auth/` routes are public and pass through.
 *
 * A request tagged with a [SessionBinding] (a queued write) is refused with
 * [com.spenditos.android.core.auth.SessionChangedException] instead of being sent
 * or retried with another session's credentials.
 */
class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/auth/")) return chain.proceed(request)

        val binding = request.tag(SessionBinding::class.java)
        val token = runBlocking { tokenFor(binding) }
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        if (response.code != 401) return response

        response.close()
        try {
            runBlocking { session.refresh() }
        } catch (e: IOException) {
            // The refresh can fail only because the session this write belongs to ended or was
            // replaced meanwhile (no refresh token left, or the server expired it).
            session.rethrowUnlessSameSession(binding, e)
        }
        val retryToken = runBlocking { tokenFor(binding) }
        val retry = chain.proceed(request.newBuilder().header("Authorization", "Bearer $retryToken").build())
        if (retry.code == 401) {
            // Could be authorizer propagation; keep the refresh token so the next call recovers.
            session.invalidateAccessToken()
        }
        return retry
    }

    private suspend fun tokenFor(binding: SessionBinding?): String =
        binding?.let { session.accessTokenFor(it) } ?: session.validAccessToken()
}
