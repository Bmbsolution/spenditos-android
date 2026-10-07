package com.spenditos.android.core.lock

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lockPrefs by preferencesDataStore(name = "lock_prefs")

/** Device-level switch, like iOS `UserPreferences.biometricLockEnabled`. */
interface AppLockSettings {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(value: Boolean)
}

@Singleton
class DataStoreAppLockSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLockSettings {
    override val enabled: Flow<Boolean> = context.lockPrefs.data.map { it[KEY] ?: false }

    override suspend fun setEnabled(value: Boolean) {
        context.lockPrefs.edit { it[KEY] = value }
    }

    private companion object {
        val KEY = booleanPreferencesKey("biometric_lock_enabled")
    }
}
