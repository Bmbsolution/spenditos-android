package com.bmbsolution.spenditos.ui.screens.splash

import androidx.lifecycle.ViewModel
import com.bmbsolution.spenditos.data.local.preferences.AuthPreferences
import com.bmbsolution.spenditos.data.local.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userPreferences: UserPreferences
) : ViewModel() {

    fun isFirstLaunch(): Boolean {
        return runBlocking {
            // Check if user has seen onboarding
            userPreferences.defaultView.first() == null
        }
    }

    fun isAuthenticated(): Boolean {
        return runBlocking {
            authPreferences.getAccessToken() != null
        }
    }
}
