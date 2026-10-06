package com.spenditos.android.core.auth

import com.spenditos.android.data.auth.ApiTokenRefresher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds abstract fun tokenStore(impl: KeystoreTokenStore): TokenStore

    @Binds abstract fun tokenRefresher(impl: ApiTokenRefresher): TokenRefresher

    companion object {
        @Provides fun epochClock(): EpochClock = EpochClock { System.currentTimeMillis() / 1000 }
    }
}
