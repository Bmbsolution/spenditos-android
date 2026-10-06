package com.spenditos.android.data.statements

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.AuthInterceptor
import com.spenditos.android.core.network.SpenditosJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StatementRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StatementIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object StatementModule {
    @Provides
    @Singleton
    @StatementRetrofit
    fun statementRetrofit(base: OkHttpClient, environment: AppEnvironment, session: SessionManager): Retrofit =
        Retrofit.Builder()
            .baseUrl(environment.apiBaseUrl)
            .client(
                base.newBuilder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .addInterceptor(AuthInterceptor(session))
                    .build(),
            )
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun statementApi(@StatementRetrofit retrofit: Retrofit): StatementApi = retrofit.create(StatementApi::class.java)

    @Provides
    @StatementIoDispatcher
    fun statementIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun statementImporter(repository: StatementRepository): StatementImporter = repository
}
