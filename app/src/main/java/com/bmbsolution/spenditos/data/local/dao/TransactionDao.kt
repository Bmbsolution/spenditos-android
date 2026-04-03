package com.bmbsolution.spenditos.data.local.dao

import androidx.room.*
import com.bmbsolution.spenditos.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC")
    fun getByCategory(categoryId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'expense'")
    suspend fun getExpenseCount(): Int

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'expense'")
    suspend fun getTotalExpenses(): Double?
}
