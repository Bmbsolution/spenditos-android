package com.spenditos.android.data.transactions

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spenditos.android.core.auth.SessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.addFormPrefs by preferencesDataStore(name = "add_form_prefs")

/**
 * The category last saved with, per kind and per user (iOS `UserPreferences.lastExpenseCategoryId`
 * / `lastIncomeCategoryId`). The add form suggests it and lists it among the quick chips.
 */
interface LastUsedCategories {
    suspend fun get(isIncome: Boolean): String?
    suspend fun set(isIncome: Boolean, categoryId: String)
}

@Singleton
class LastUsedCategoriesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
) : LastUsedCategories {
    override suspend fun get(isIncome: Boolean): String? {
        val userId = session.currentUserId ?: return null
        return context.addFormPrefs.data.first()[key(userId, isIncome)]
    }

    override suspend fun set(isIncome: Boolean, categoryId: String) {
        val userId = session.currentUserId ?: return
        context.addFormPrefs.edit { it[key(userId, isIncome)] = categoryId }
    }

    private fun key(userId: String, isIncome: Boolean) =
        stringPreferencesKey("lastCategory.${if (isIncome) "income" else "expense"}.$userId")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LastUsedCategoriesModule {
    @Binds
    abstract fun bindLastUsedCategories(store: LastUsedCategoriesStore): LastUsedCategories
}
