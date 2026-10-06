package com.spenditos.android.data.user

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

/** `GET /user/settings`, with the display currency remembered per user for offline use. */
@Singleton
class UserSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: UserApi,
    private val session: SessionManager,
) {
    fun currency(): Flow<String> = context.userPrefs.data.map { prefs ->
        session.currentUserId?.let { prefs[currencyKey(it)] } ?: DEFAULT_CURRENCY
    }

    suspend fun refresh(): UserSettings {
        val settings = apiCall { api.settings() }
        context.userPrefs.edit { it[currencyKey(settings.userId)] = settings.currency }
        return settings
    }

    private fun currencyKey(userId: String) = stringPreferencesKey("currency.$userId")

    private companion object {
        const val DEFAULT_CURRENCY = "USD"
    }
}
