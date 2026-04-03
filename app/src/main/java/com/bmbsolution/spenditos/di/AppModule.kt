package com.bmbsolution.spenditos.di

import android.content.Context
import androidx.room.Room
import com.bmbsolution.spenditos.BuildConfig
import com.bmbsolution.spenditos.data.local.db.SpenditosDatabase
import com.bmbsolution.spenditos.data.local.preferences.AuthPreferences
import com.bmbsolution.spenditos.data.remote.api.*
import com.bmbsolution.spenditos.data.remote.interceptor.AuthInterceptor
import com.bmbsolution.spenditos.data.remote.interceptor.HeaderInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        headerInterceptor: HeaderInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    // API Providers
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTransactionApi(retrofit: Retrofit): TransactionApi = retrofit.create(TransactionApi::class.java)

    @Provides
    @Singleton
    fun provideBudgetApi(retrofit: Retrofit): BudgetApi = retrofit.create(BudgetApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideGamificationApi(retrofit: Retrofit): GamificationApi = retrofit.create(GamificationApi::class.java)

    @Provides
    @Singleton
    fun provideRecurringTemplateApi(retrofit: Retrofit): RecurringTemplateApi = retrofit.create(RecurringTemplateApi::class.java)

    @Provides
    @Singleton
    fun provideImportApi(retrofit: Retrofit): ImportApi = retrofit.create(ImportApi::class.java)

    @Provides
    @Singleton
    fun provideStatementImportApi(retrofit: Retrofit): StatementImportApi = retrofit.create(StatementImportApi::class.java)

    @Provides
    @Singleton
    fun provideCategoryApi(retrofit: Retrofit): CategoryApi = retrofit.create(CategoryApi::class.java)

    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)

    @Provides
    @Singleton
    fun provideReportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton
    fun provideExportApi(retrofit: Retrofit): ExportApi = retrofit.create(ExportApi::class.java)

    @Provides
    @Singleton
    fun provideReceiptScanApi(retrofit: Retrofit): ReceiptScanApi = retrofit.create(ReceiptScanApi::class.java)

    @Provides
    @Singleton
    fun provideMerchantMappingApi(retrofit: Retrofit): MerchantMappingApi = retrofit.create(MerchantMappingApi::class.java)

    @Provides
    @Singleton
    fun provideAudioEntryApi(retrofit: Retrofit): AudioEntryApi = retrofit.create(AudioEntryApi::class.java)

    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): GroupApi = retrofit.create(GroupApi::class.java)

    // Database
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpenditosDatabase {
        return Room.databaseBuilder(
            context,
            SpenditosDatabase::class.java,
            "spenditos.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(db: SpenditosDatabase) = db.transactionDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: SpenditosDatabase) = db.categoryDao()
}
