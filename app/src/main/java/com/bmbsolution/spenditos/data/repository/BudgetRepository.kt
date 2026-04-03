package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.BudgetApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetApi: BudgetApi
) {
    fun getBudgets(): Flow<Result<List<Budget>>> = flow {
        try {
            val response = budgetApi.list()
            emit(Result.success(response.data))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun createBudget(request: BudgetCreateRequest): Result<Budget> {
        return try {
            val response = budgetApi.create(request)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBudget(id: String, request: BudgetUpdateRequest): Result<Budget> {
        return try {
            val response = budgetApi.update(id, request)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(id: String): Result<Unit> {
        return try {
            budgetApi.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBudgetHistory(budgetId: String): Result<List<BudgetHistoryEntry>> {
        return try {
            val response = budgetApi.getHistory(budgetId)
            Result.success(response.data.history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrphanBudgets(): Result<List<OrphanBudget>> {
        return try {
            val response = budgetApi.getOrphans()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
