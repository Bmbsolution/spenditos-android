package com.spenditos.android.testing

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Deterministic "no connection" for tests: the next [failNext] requests throw IOException before
 * reaching the server, like a dropped connection does in production (becomes ApiError.Network).
 * More reliable than closing MockWebServer sockets, which depends on connection reuse.
 */
class OfflineInterceptor : Interceptor {
    @Volatile var failNext: Int = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        if (failNext > 0) {
            failNext--
            throw IOException("offline (test)")
        }
        return chain.proceed(chain.request())
    }
}
