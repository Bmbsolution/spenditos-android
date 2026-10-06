package com.spenditos.android.data.onboarding

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {
    @Provides
    @Singleton
    fun onboardingApi(retrofit: Retrofit): OnboardingApi = retrofit.create(OnboardingApi::class.java)

    @Provides
    @Singleton
    fun onboardingActions(repository: OnboardingRepository): OnboardingActions = repository
}
