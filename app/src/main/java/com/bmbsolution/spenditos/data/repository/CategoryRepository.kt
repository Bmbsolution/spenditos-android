package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.model.Category
import com.bmbsolution.spenditos.data.remote.api.CategoryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryApi: CategoryApi
) {
    fun getCategories(): Flow<Result<List<Category>>> = flow {
        try {
            val response = categoryApi.list()
            emit(Result.success(response.data))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun createCategory(name: String, type: String, icon: String? = null, color: String? = null): Result<Category> {
        return try {
            val categoryMap = mutableMapOf("name" to name, "type" to type)
            icon?.let { categoryMap["icon"] = it }
            color?.let { categoryMap["color"] = it }
            val response = categoryApi.create(categoryMap)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCategory(id: String, name: String? = null, icon: String? = null, color: String? = null): Result<Category> {
        return try {
            val updates = mutableMapOf<String, String>()
            name?.let { updates["name"] = it }
            icon?.let { updates["icon"] = it }
            color?.let { updates["color"] = it }
            val response = categoryApi.update(id, updates)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(id: String): Result<Unit> {
        return try {
            categoryApi.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderCategories(categoryIds: List<String>): Result<Unit> {
        return try {
            categoryApi.reorder(categoryIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
