package com.spenditos.android.data.user

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.model.ChangePasswordRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

interface UserSettingsActions {
    fun currency(): Flow<String>
    suspend fun refresh(): UserSettings
    suspend fun updateSettings(update: UserSettingsUpdate): UserSettings
    suspend fun changePassword(currentPassword: String, newPassword: String)
}

/** `GET /user/settings`, with the display currency remembered per user for offline use. */
@Singleton
class UserSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: UserApi,
    private val session: SessionManager,
) : UserSettingsActions {
    override fun currency(): Flow<String> = context.userPrefs.data.map { prefs ->
        session.currentUserId?.let { prefs[currencyKey(it)] } ?: DEFAULT_CURRENCY
    }

    override suspend fun refresh(): UserSettings {
        val settings = apiCall { api.settings() }
        cacheCurrency(settings)
        return settings
    }

    override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings {
        val updated = apiCall { api.updateSettings(update) }
        var refreshed = updated
        try {
            cacheCurrency(updated)
        } finally {
            try {
                refreshed = refresh()
            } catch (_: ApiError) {
                // The PUT response is authoritative if the follow-up read is offline.
            }
        }
        return refreshed
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiCallUnit { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }
    }

    private suspend fun cacheCurrency(settings: UserSettings) {
        context.userPrefs.edit { it[currencyKey(settings.userId)] = settings.currency }
    }

    private fun currencyKey(userId: String) = stringPreferencesKey("currency.$userId")

    private companion object {
        const val DEFAULT_CURRENCY = "USD"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserSettingsActionsModule {
    @Binds
    @Singleton
    abstract fun bindUserSettingsActions(repository: UserSettingsRepository): UserSettingsActions
}
