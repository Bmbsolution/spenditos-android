package com.spenditos.android.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeRange(userId: String, start: String, end: String): Flow<List<TransactionEntity>>

    @Upsert
    suspend fun upsert(rows: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun delete(userId: String, id: String)

    @Query("DELETE FROM transactions WHERE userId = :userId AND syncStatus = 'SYNCED' AND date BETWEEN :start AND :end")
    suspend fun deleteSyncedInRange(userId: String, start: String, end: String)

    /** Replaces the synced rows of a fully fetched range; queued rows are never touched. */
    @Transaction
    suspend fun replaceSyncedRange(userId: String, start: String, end: String, rows: List<TransactionEntity>) {
        deleteSyncedInRange(userId, start, end)
        upsert(rows)
    }

    @Query("UPDATE transactions SET syncStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun setStatus(userId: String, id: String, status: SyncStatus)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY sortOrder, name")
    fun observe(userId: String): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun clear(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rows: List<CategoryEntity>)

    @Transaction
    suspend fun replaceAll(userId: String, rows: List<CategoryEntity>) {
        clear(userId)
        insert(rows)
    }
}

@Dao
interface MutationDao {
    @Insert
    suspend fun insert(row: PendingMutationEntity)

    @Query("SELECT * FROM pending_mutations WHERE id = :id")
    suspend fun get(id: String): PendingMutationEntity?

    @Query("SELECT * FROM pending_mutations WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt LIMIT 1")
    suspend fun nextPending(userId: String): PendingMutationEntity?

    @Query("SELECT * FROM pending_mutations WHERE userId = :userId ORDER BY createdAt")
    fun observeAll(userId: String): Flow<List<PendingMutationEntity>>

    /** Deletes a queued create unless a sender holds it; returns 1 when it was deleted. */
    @Query("DELETE FROM pending_mutations WHERE id = :id AND status != 'IN_FLIGHT'")
    suspend fun deleteUnlessInFlight(id: String): Int

    /** Atomic claim; returns 1 only for the caller that won it. */
    @Query("UPDATE pending_mutations SET status = 'IN_FLIGHT', attemptCount = attemptCount + 1, lastAttemptAt = :now WHERE id = :id AND status = 'PENDING'")
    suspend fun claim(id: String, now: Long): Int

    @Query("UPDATE pending_mutations SET status = :status, failureCode = :code, failureMessage = :message WHERE id = :id")
    suspend fun release(id: String, status: MutationStatus, code: FailureCode?, message: String?)

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun delete(id: String)

    /** A process killed mid-send leaves IN_FLIGHT behind; resending is safe (idempotent key). */
    @Query("UPDATE pending_mutations SET status = 'PENDING' WHERE status = 'IN_FLIGHT' AND lastAttemptAt < :before")
    suspend fun releaseStaleClaims(before: Long)

    @Query("UPDATE pending_mutations SET status = 'PENDING', failureCode = NULL, failureMessage = NULL WHERE userId = :userId AND status = 'FAILED' AND failureCode = :code")
    suspend fun requeueFailures(userId: String, code: FailureCode)
}
