package com.spenditos.android.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spenditos.android.core.config.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, PendingMutationEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SpenditosDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun mutations(): MutationDao
}

/** 1 -> 2: additive nullable `transactions.debtId` (docs/features/debts.md). Existing rows keep null. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `debtId` TEXT")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, environment: AppEnvironment): SpenditosDatabase =
        // One database per environment so staging rows never mix with prod.
        Room.databaseBuilder(context, SpenditosDatabase::class.java, "spenditos-${environment.storageNamespace}.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun transactionDao(db: SpenditosDatabase) = db.transactions()

    @Provides fun categoryDao(db: SpenditosDatabase) = db.categories()

    @Provides fun mutationDao(db: SpenditosDatabase) = db.mutations()
}
