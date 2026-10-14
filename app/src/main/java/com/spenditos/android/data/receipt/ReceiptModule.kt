package com.spenditos.android.data.receipt

import dagger.Module
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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

    /** Direct S3 uploads never use the authenticated API client or its interceptors. */
    @Provides
    @Singleton
    @ReceiptUploadClient
    fun receiptUploadClient(): OkHttpClient = OkHttpClient.Builder().build()
}
