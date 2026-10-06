package com.spenditos.android.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/** Diagnostics headers sent by iOS on every call (`X-Spenditos-Trace-Id`, `X-Spenditos-Session-Id`). */
class TraceHeadersInterceptor(private val sessionId: String = UUID.randomUUID().toString()) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("X-Spenditos-Session-Id", sessionId)
        if (request.header("X-Spenditos-Trace-Id") == null) {
            builder.header("X-Spenditos-Trace-Id", UUID.randomUUID().toString().replace("-", ""))
        }
        return chain.proceed(builder.build())
    }
}
