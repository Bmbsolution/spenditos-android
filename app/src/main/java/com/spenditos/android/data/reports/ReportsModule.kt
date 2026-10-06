package com.spenditos.android.data.reports

import com.spenditos.android.data.user.UserSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportsModule {
    @Provides
    @Singleton
    fun reportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton
    fun reportsRepository(api: ReportsApi, settings: UserSettingsRepository): ReportsRepository =
        ReportsRepository(api) { settings.currency() }

    @Provides
    @Singleton
    fun reportsDataSource(repository: ReportsRepository): ReportsDataSource = repository
}
