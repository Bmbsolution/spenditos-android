package com.spenditos.android.data.auth

import com.spenditos.android.core.network.PublicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    @Provides
    @Singleton
    fun accountApi(@PublicApi retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun accountOperations(repository: AccountRepository): AccountOperations = repository

    @Provides
    @Singleton
    fun loginOperations(repository: AuthRepository): LoginOperations = LoginOperations { email, password ->
        repository.login(email, password)
    }

    @Provides
    @Singleton
    fun accountDeletion(repository: AuthRepository): AccountDeletion = AccountDeletion { repository.deleteAccount() }
}

/** Settings > Profile > Delete Account; signs out on success, throws (still signed in) on failure. */
fun interface AccountDeletion {
    suspend fun deleteAccount()
}
