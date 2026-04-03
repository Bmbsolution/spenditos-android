package com.bmbsolution.spenditos.data.remote.interceptor

import com.bmbsolution.spenditos.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeaderInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-Platform", "android")
            .build()

        return chain.proceed(request)
    }
}
