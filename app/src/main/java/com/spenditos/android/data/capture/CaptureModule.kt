package com.spenditos.android.data.capture

import android.content.Context
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import java.time.LocalDate
import javax.inject.Singleton

interface CaptureDataSource {
    fun categories(): Flow<List<Category>>
    fun currency(): Flow<String>
    /** Signed-in user, so a saved capture is never resumed under another account. */
    val currentUserId: String?
}

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {
    @Provides
    @Singleton
    fun captureApi(retrofit: Retrofit): CaptureApi = retrofit.create(CaptureApi::class.java)

    @Provides
    @Singleton
    fun captureActions(repository: CaptureRepository): CaptureActions = repository

    @Provides
    fun captureDataSource(categories: CategoryRepository, settings: UserSettingsRepository, session: com.spenditos.android.core.auth.SessionManager): CaptureDataSource =
        object : CaptureDataSource {
            override fun categories(): Flow<List<Category>> = categories.observe()
            override fun currency(): Flow<String> = settings.currency()
            override val currentUserId: String? get() = session.currentUserId
        }

    @Provides
    fun captureTransactionRefresh(transactions: TransactionRepository): CaptureTransactionRefresh =
        object : CaptureTransactionRefresh {
            override suspend fun refresh(start: LocalDate, end: LocalDate) = transactions.refreshRange(start, end)
        }

    @Provides
    fun audioRecorder(@ApplicationContext context: Context): AudioRecorder = MediaRecorderAudioRecorder(context)
}
