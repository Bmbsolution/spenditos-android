package com.spenditos.android.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spenditos.android.core.config.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, PendingMutationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SpenditosDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun mutations(): MutationDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, environment: AppEnvironment): SpenditosDatabase =
        // One database per environment so staging rows never mix with prod.
        Room.databaseBuilder(context, SpenditosDatabase::class.java, "spenditos-${environment.storageNamespace}.db").build()

    @Provides fun transactionDao(db: SpenditosDatabase) = db.transactions()

    @Provides fun categoryDao(db: SpenditosDatabase) = db.categories()

    @Provides fun mutationDao(db: SpenditosDatabase) = db.mutations()
}
