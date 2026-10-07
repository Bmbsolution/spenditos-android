package com.spenditos.android.data.gamification

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GamificationModule {
    @Provides
    @Singleton
    fun gamificationApi(retrofit: Retrofit): GamificationApi = retrofit.create(GamificationApi::class.java)

    @Provides
    @Singleton
    fun gamificationDataSource(repository: GamificationRepository): GamificationDataSource = repository
}
