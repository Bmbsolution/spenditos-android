package com.spenditos.android.core.network

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.data.auth.AuthApi
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Retrofit without the auth interceptor: auth routes only (login, refresh, logout). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun baseClient(environment: AppEnvironment): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(TraceHeadersInterceptor())
        .apply {
            if (environment.isDebug) {
                // BASIC = method, path, status, timing. Never bodies (financial data) and
                // the Authorization header is redacted anyway.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    @PublicApi
    fun publicRetrofit(base: OkHttpClient, environment: AppEnvironment): Retrofit = retrofit(base, environment)

    @Provides
    @Singleton
    fun authedRetrofit(base: OkHttpClient, environment: AppEnvironment, session: SessionManager): Retrofit =
        retrofit(base.newBuilder().addInterceptor(AuthInterceptor(session)).build(), environment)

    private fun retrofit(client: OkHttpClient, environment: AppEnvironment): Retrofit = Retrofit.Builder()
        .baseUrl(environment.apiBaseUrl)
        .client(client)
        .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun authApi(@PublicApi retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun transactionsApi(retrofit: Retrofit): TransactionsApi = retrofit.create(TransactionsApi::class.java)

    @Provides @Singleton
    fun categoriesApi(retrofit: Retrofit): CategoriesApi = retrofit.create(CategoriesApi::class.java)

    @Provides @Singleton
    fun userApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)
}
