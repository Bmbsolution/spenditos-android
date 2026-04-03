package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.local.dao.TransactionDao
import com.bmbsolution.spenditos.data.local.entity.TransactionEntity
import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.TransactionApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionApi: TransactionApi,
    private val transactionDao: TransactionDao
) {
    fun getTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAll()

    suspend fun fetchTransactions(filters: Map<String, String> = emptyMap()): Result<List<Transaction>> {
        return try {
            val response = transactionApi.list(filters)
            Result.success(response.transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTransaction(request: TransactionCreateRequest): Result<Pair<Transaction, TransactionGamificationDetails>> {
        return try {
            val response = transactionApi.create(request)
            val details = response.details?.let { json ->
                // Parse gamification details from response
                TransactionGamificationDetails()
            } ?: TransactionGamificationDetails()
            Result.success(response.data to details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(id: String, request: TransactionUpdateRequest): Result<Transaction> {
        return try {
            val transaction = transactionApi.update(id, request)
            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(id: String): Result<Unit> {
        return try {
            transactionApi.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bulkDelete(ids: List<String>): Result<Unit> {
        return try {
            transactionApi.bulkDelete(BulkDeleteRequest(ids))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateMonthlyTransactions(targetDate: String? = null, templateIds: List<String>? = null): Result<GenerateTransactionsResult> {
        return try {
            val result = transactionApi.generateMonthly(GenerateTransactionsRequest(targetDate, templateIds))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
