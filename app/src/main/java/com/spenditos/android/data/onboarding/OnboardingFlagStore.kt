package com.spenditos.android.data.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "This account finished onboarding on this device", namespaced per user (iOS
 * `UserPreferences.hasCompletedOnboarding(for:)`). It survives sign-out on purpose, so a returning
 * user is not sent through setup again just because the settings request failed.
 */
interface OnboardingFlagStore {
    suspend fun isCompleted(userId: String): Boolean

    suspend fun markCompleted(userId: String)
}

private val Context.onboardingPrefs by preferencesDataStore(name = "onboarding_prefs")

@Singleton
class DataStoreOnboardingFlagStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingFlagStore {
    override suspend fun isCompleted(userId: String): Boolean =
        context.onboardingPrefs.data.first()[key(userId)] == true

    override suspend fun markCompleted(userId: String) {
        context.onboardingPrefs.edit { it[key(userId)] = true }
    }

    private fun key(userId: String) = booleanPreferencesKey("completed.$userId")
}
