package com.spenditos.android.data.categories

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.db.CategoryDao
import com.spenditos.android.core.db.CategoryEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Categories, cached for offline pickers (iOS `CategoryCacheStore`). */
@Singleton
class CategoryRepository @Inject constructor(
    private val api: CategoriesApi,
    private val dao: CategoryDao,
    private val session: SessionManager,
) {
    fun observe(): Flow<List<Category>> {
        val userId = session.currentUserId ?: return emptyFlow()
        return dao.observe(userId).map { rows -> rows.map { it.toModel() } }
    }

    suspend fun refresh() {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val categories = apiCall { api.list() }
        dao.replaceAll(userId, categories.map { it.toEntity(userId) })
    }

    private fun Category.toEntity(userId: String) =
        CategoryEntity(id, userId, name, color, icon, isIncome, sortOrder, isSystemSuggestion)

    private fun CategoryEntity.toModel() = Category(id, userId, name, color, icon, isIncome, sortOrder, isSystemSuggestion)
}
