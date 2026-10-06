package com.spenditos.android.data.recurring

import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import javax.inject.Singleton

interface RecurringCategorySource {
    fun observe(): Flow<List<Category>>
    suspend fun refresh()
}

@Module
@InstallIn(SingletonComponent::class)
object RecurringModule {
    @Provides
    @Singleton
    fun recurringApi(retrofit: Retrofit): RecurringApi = retrofit.create(RecurringApi::class.java)

    @Provides
    @Singleton
    fun recurringActions(repository: RecurringRepository): RecurringActions = repository

    @Provides
    @Singleton
    fun recurringCategorySource(repository: CategoryRepository): RecurringCategorySource =
        object : RecurringCategorySource {
            override fun observe() = repository.observe()
            override suspend fun refresh() = repository.refresh()
        }
}
