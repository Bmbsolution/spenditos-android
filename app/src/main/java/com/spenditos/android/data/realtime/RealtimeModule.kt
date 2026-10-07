package com.spenditos.android.data.realtime

import com.spenditos.android.core.config.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeIoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeUrl

@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {
    @Provides
    @Singleton
    fun realtimeApi(retrofit: Retrofit): RealtimeApi = retrofit.create(RealtimeApi::class.java)

    @Provides
    @Singleton
    fun webSocketConnector(base: OkHttpClient): WebSocketConnector {
        val client = base.newBuilder()
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .pingInterval(300, TimeUnit.SECONDS)
            .build()
        return OkHttpWebSocketConnector(client)
    }

    @Provides
    @Singleton
    fun foregroundMonitor(): ForegroundMonitor = ProcessForegroundMonitor()

    @Provides
    @Singleton
    fun realtimeClock(): RealtimeClock = SystemRealtimeClock()

    @Provides
    @Singleton
    @RealtimeIoDispatcher
    fun realtimeDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @RealtimeScope
    fun realtimeScope(@RealtimeIoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    @RealtimeUrl
    fun webSocketUrl(environment: AppEnvironment): String = environment.webSocketUrl
}
