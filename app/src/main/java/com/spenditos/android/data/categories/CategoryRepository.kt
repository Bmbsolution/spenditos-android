package com.spenditos.android.data.categories

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.db.CategoryDao
import com.spenditos.android.core.db.CategoryEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Categories, cached for offline pickers (iOS `CategoryCacheStore`). */
@Singleton
class CategoryRepository @Inject constructor(
    private val api: CategoriesApi,
    private val dao: CategoryDao,
    private val session: SessionManager,
    private val invalidations: FinanceInvalidationBus,
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

    suspend fun create(request: CategoryCreateRequest): Category {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val created = apiCall { api.create(request) }
        val current = dao.observe(userId).first().map { it.toModel() }
        refreshAfterWrite(userId, current.filterNot { it.id == created.id } + created)
        return created
    }

    suspend fun update(id: String, request: CategoryUpdateRequest): Category {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val updated = apiCall { api.update(id, request) }
        val current = dao.observe(userId).first().map { it.toModel() }
        refreshAfterWrite(userId, current.filterNot { it.id == updated.id } + updated)
        return updated
    }

    suspend fun delete(id: String) {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        apiCallUnit { api.delete(id) }
        val current = dao.observe(userId).first().map { it.toModel() }
        refreshAfterWrite(userId, current.filterNot { it.id == id })
    }

    private suspend fun refreshAfterWrite(userId: String, categories: List<Category>) {
        try {
            try {
                dao.replaceAll(userId, categories.map { it.toEntity(userId) })
            } finally {
                try {
                    refresh()
                } catch (_: ApiError) {
                    // The write response has already updated the local cache. Keep the
                    // successful write successful if the follow-up refresh is offline.
                }
            }
        } finally {
            invalidations.invalidate(Aggregate.CATEGORY)
        }
    }

    private fun Category.toEntity(userId: String) =
        CategoryEntity(id, userId, name, color, icon, isIncome, sortOrder, isSystemSuggestion)

    private fun CategoryEntity.toModel() = Category(id, userId, name, color, icon, isIncome, sortOrder, isSystemSuggestion)
}
