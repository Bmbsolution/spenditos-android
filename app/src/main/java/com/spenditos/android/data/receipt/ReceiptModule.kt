package com.spenditos.android.data.receipt

import dagger.Module
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReceiptUploadClient

/** Dispatcher for blocking image work; replaced in tests so scans can be awaited. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReceiptIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ReceiptModule {
    @Provides
    @ReceiptIoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun receiptApi(retrofit: Retrofit): ReceiptApi = retrofit.create(ReceiptApi::class.java)

    /** Direct S3 uploads never use the authenticated API client or its interceptors. */
    @Provides
    @Singleton
    @ReceiptUploadClient
    fun receiptUploadClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun receiptScanner(repository: ReceiptRepository): ReceiptScanner = repository

}
