package com.bmbsolution.spenditos.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_ID = stringPreferencesKey("user_id")
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }

    suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getRefreshToken(): String? = refreshToken.first()
    suspend fun getUserId(): String? = userId.first()

    suspend fun saveTokens(accessToken: String, refreshToken: String? = null) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[REFRESH_TOKEN] = it }
        }
    }

    suspend fun saveUserId(userId: String) {
        dataStore.edit { prefs ->
            prefs[USER_ID] = userId
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
