package com.spenditos.android.core.sync

import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReconciliationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds abstract fun syncRequester(impl: SyncScheduler): SyncRequester

    @Binds abstract fun connectivity(impl: NetworkMonitor): ConnectivityStatus

    companion object {
        @Provides
        @Singleton
        @ReconciliationScope
        fun reconciliationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
