package com.spenditos.android.core.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DB 1 -> 2 adds the nullable `transactions.debtId`. A version-1 file is built from the exported
 * 1.json schema, a row is written, and Room (with schema validation) opens it through [MIGRATION_1_2].
 */
@RunWith(RobolectricTestRunner::class)
class DebtIdMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion1() {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`categoryId` TEXT NOT NULL, `description` TEXT NOT NULL, `date` TEXT NOT NULL, `mood` TEXT, `templateId` TEXT, " +
                        "`source` TEXT, `createdAt` TEXT, `updatedAt` TEXT, `syncStatus` TEXT NOT NULL, `clientMutationId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId_date` ON `transactions` (`userId`, `date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                        "`icon` TEXT, `isIncome` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `isSystemSuggestion` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_userId` ON `categories` (`userId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_mutations` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `kind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                        "`localTransactionId` TEXT NOT NULL, `status` TEXT NOT NULL, `failureCode` TEXT, `failureMessage` TEXT, `attemptCount` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_mutations_userId_status` ON `pending_mutations` (`userId`, `status`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '598ae7d2491052e238c560f600b67ef4')")
                db.execSQL(
                    "INSERT INTO transactions (id, userId, amount, categoryId, description, date, syncStatus) " +
                        "VALUES ('t1', 'u1', 12.5, 'c1', 'Lunch', '2026-10-01', 'SYNCED')",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun existingRowsSurviveWithNullDebtIdAndNewRowsStoreIt() = runBlocking {
        createVersion1()
        val db = Room.databaseBuilder(context, SpenditosDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.transactions()
            val old = dao.observeRange("u1", "2026-10-01", "2026-10-31").first()
            assertEquals(1, old.size)
            assertEquals("Lunch", old[0].description)
            assertNull(old[0].debtId)

            dao.upsert(
                listOf(
                    old[0].copy(id = "t2", debtId = "debt-car"),
                ),
            )
            val rows = dao.observeRange("u1", "2026-10-01", "2026-10-31").first().associateBy { it.id }
            assertEquals("debt-car", rows.getValue("t2").debtId)
            assertNull(rows.getValue("t1").debtId)
        } finally {
            db.close()
        }
    }
}
