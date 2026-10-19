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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs by preferencesDataStore(name = "user_prefs")

interface UserSettingsActions {
    fun currency(): Flow<String>
    /** The saved display name, remembered per user for offline use; blank when none is set. */
    fun displayName(): Flow<String> = flowOf("")
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
    private val proAccess: com.spenditos.android.data.entitlement.ProAccess,
) : UserSettingsActions {
    override fun currency(): Flow<String> = context.userPrefs.data.map { prefs ->
        session.currentUserId?.let { prefs[currencyKey(it)] } ?: DEFAULT_CURRENCY
    }

    override fun displayName(): Flow<String> = context.userPrefs.data.map { prefs ->
        session.currentUserId?.let { prefs[displayNameKey(it)] }.orEmpty()
    }

    override suspend fun refresh(): UserSettings {
        // Bound before the request: a response that lands after an account switch is not applied.
        val binding = session.currentBinding
        val settings = apiCall { api.settings() }
        proAccess.apply(settings, binding)
        cacheCurrency(settings)
        return settings
    }

    override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings {
        val binding = session.currentBinding
        val updated = apiCall { api.updateSettings(update) }
        proAccess.apply(updated, binding)
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
        context.userPrefs.edit {
            it[currencyKey(settings.userId)] = settings.currency
            val name = settings.displayName?.trim().orEmpty()
            if (name.isEmpty()) it.remove(displayNameKey(settings.userId)) else it[displayNameKey(settings.userId)] = name
        }
    }

    private fun currencyKey(userId: String) = stringPreferencesKey("currency.$userId")

    private fun displayNameKey(userId: String) = stringPreferencesKey("displayName.$userId")

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
