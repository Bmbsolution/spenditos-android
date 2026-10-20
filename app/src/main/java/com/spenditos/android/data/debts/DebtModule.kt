package com.spenditos.android.data.debts

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebtModule {
    @Provides
    @Singleton
    fun debtApi(retrofit: Retrofit): DebtApi = retrofit.create(DebtApi::class.java)

    @Provides
    @Singleton
    fun debtActions(repository: DebtRepository): DebtActions = repository
}
