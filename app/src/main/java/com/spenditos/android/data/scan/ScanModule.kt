package com.spenditos.android.data.scan

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** App-lifetime scope of the [ScanCoordinator]'s listeners. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScanScope

@Module
@InstallIn(SingletonComponent::class)
object ScanModule {
    @Provides
    @Singleton
    fun scanApi(retrofit: Retrofit): ScanApi = retrofit.create(ScanApi::class.java)

    @Provides
    @Singleton
    @ScanScope
    fun scanScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanBindsModule {
    @Binds abstract fun actions(impl: ScanRepository): ScanActions

    @Binds abstract fun preparer(impl: AndroidScanDocumentPreparer): ScanDocumentPreparer

    @Binds abstract fun uploader(impl: OkHttpScanFileUploader): ScanFileUploader

    @Binds abstract fun inputs(impl: AndroidScanInputFactory): ScanInputFactory

    @Binds abstract fun clipboard(impl: AndroidClipboardImages): ClipboardImages

    @Binds abstract fun rates(impl: OkHttpExchangeRates): ExchangeRates

    @Binds abstract fun time(impl: SystemScanTimeSource): ScanTimeSource

    @Binds abstract fun session(impl: SessionManagerScanSession): ScanSession

    @Binds abstract fun store(impl: PrefsScanStateStore): ScanStateStore
}
