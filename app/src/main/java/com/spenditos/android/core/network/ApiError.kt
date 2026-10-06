package com.spenditos.android.core.network

import java.io.IOException

/**
 * Errors surfaced by the data layer. Mirrors iOS `APIError` closely enough
 * that failure classification (offline queue, auth) can follow the same rules.
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Transport failure: no connection, timeout, TLS, DNS. Always retryable. */
    class Network(cause: Throwable) : ApiError("Network error", cause)

    /** No usable session (refresh token missing or definitively rejected). */
    data object Unauthorized : ApiError("Unauthorized") {
        private fun readResolve(): Any = Unauthorized
    }

    /** Android Keystore could not be read. Recoverable; never clears the session. */
    class KeystoreUnavailable(cause: Throwable? = null) : ApiError("Secure storage unavailable", cause)

    /** Non-2xx response with the envelope's `error ?: message`. */
    class Server(val status: Int, val serverMessage: String) : ApiError("HTTP $status: $serverMessage")

    /** 2xx response whose envelope has no `data`. */
    data object NoData : ApiError("Response had no data") {
        private fun readResolve(): Any = NoData
    }

    /** Body did not match the expected wire model. */
    class Decoding(cause: Throwable) : ApiError("Unexpected response format", cause)
}

/**
 * Thrown from OkHttp interceptors. OkHttp only tolerates [IOException] from an
 * interceptor on async calls, so auth failures travel as IOExceptions and are
 * mapped back to [ApiError] by [apiCall].
 */
class AuthRequiredException : IOException("Authentication required")

class KeystoreUnavailableException(cause: Throwable? = null) : IOException("Secure storage unavailable", cause)
