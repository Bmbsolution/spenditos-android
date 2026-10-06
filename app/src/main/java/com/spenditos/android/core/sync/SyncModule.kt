package com.spenditos.android.core.sync

import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds abstract fun syncRequester(impl: SyncScheduler): SyncRequester

    @Binds abstract fun connectivity(impl: NetworkMonitor): ConnectivityStatus
}
