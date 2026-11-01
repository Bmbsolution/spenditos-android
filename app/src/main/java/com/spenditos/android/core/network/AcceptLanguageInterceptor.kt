package com.spenditos.android.core.network

import com.spenditos.android.core.i18n.AppLanguage
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

/** Sends the resolved app language on every API request (`fr-CA,fr;q=0.9`); the server uses it for auth emails. */
class AcceptLanguageInterceptor(private val locale: () -> Locale = { Locale.getDefault() }) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("Accept-Language", AppLanguage.acceptLanguage(locale()))
        return chain.proceed(builder.build())
    }
}
