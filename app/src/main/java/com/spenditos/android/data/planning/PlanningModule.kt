package com.spenditos.android.data.planning

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlanningModule {
    @Provides
    @Singleton
    fun planningApi(retrofit: Retrofit): PlanningApi = retrofit.create(PlanningApi::class.java)
}
