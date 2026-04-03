package com.bmbsolution.spenditos.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bmbsolution.spenditos.data.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userDataStore

    companion object {
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val EMAIL_NOTIFICATIONS = booleanPreferencesKey("email_notifications")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        private val WEEK_STARTS_ON = intPreferencesKey("week_starts_on")
        private val DEFAULT_VIEW = stringPreferencesKey("default_view")
        private val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val emailNotifications: Flow<Boolean> = dataStore.data.map { it[EMAIL_NOTIFICATIONS] ?: true }
    val darkMode: Flow<Boolean?> = dataStore.data.map { it[DARK_MODE] }
    val defaultCurrency: Flow<String> = dataStore.data.map { it[DEFAULT_CURRENCY] ?: "USD" }
    val weekStartsOn: Flow<Int> = dataStore.data.map { it[WEEK_STARTS_ON] ?: 0 }
    val defaultView: Flow<String> = dataStore.data.map { it[DEFAULT_VIEW] ?: "dashboard" }
    val privacyMode: Flow<Boolean> = dataStore.data.map { it[PRIVACY_MODE] ?: false }

    suspend fun getSettings(): UserSettings {
        return dataStore.data.first().let { prefs ->
            UserSettings(
                notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: true,
                emailNotifications = prefs[EMAIL_NOTIFICATIONS] ?: true,
                darkMode = prefs[DARK_MODE],
                defaultCurrency = prefs[DEFAULT_CURRENCY] ?: "USD",
                weekStartsOn = prefs[WEEK_STARTS_ON] ?: 0,
                defaultView = prefs[DEFAULT_VIEW] ?: "dashboard"
            )
        }
    }

    suspend fun saveSettings(settings: UserSettings) {
        dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            prefs[EMAIL_NOTIFICATIONS] = settings.emailNotifications
            settings.darkMode?.let { prefs[DARK_MODE] = it }
            prefs[DEFAULT_CURRENCY] = settings.defaultCurrency
            prefs[WEEK_STARTS_ON] = settings.weekStartsOn
            prefs[DEFAULT_VIEW] = settings.defaultView
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        dataStore.edit { it[PRIVACY_MODE] = enabled }
    }

    suspend fun getPrivacyMode(): Boolean {
        return dataStore.data.first()[PRIVACY_MODE] ?: false
    }
}
