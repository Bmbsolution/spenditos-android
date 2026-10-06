package com.spenditos.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.data.auth.AuthRepository
import com.spenditos.android.data.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val email: String? = null, val unsyncedCount: Int = 0, val envName: String = "", val stage: String = "")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    session: SessionManager,
    transactions: TransactionRepository,
    environment: AppEnvironment,
) : ViewModel() {
    private val email = (session.state.value as? SessionState.SignedIn)?.email

    val state: StateFlow<SettingsUiState> = transactions.observeQueue()
        .map { SettingsUiState(email, it.size, environment.displayName, environment.stage) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(email, 0, environment.displayName, environment.stage))

    /** Plain sign-out keeps unsynced work queued for this account (iOS #227). */
    fun signOutMessage(): String {
        val count = state.value.unsyncedCount
        if (count == 0) return "Are you sure you want to sign out?"
        val noun = if (count == 1) "transaction" else "transactions"
        return "Are you sure you want to sign out?\n\n$count unsynced $noun will stay queued on this device and sync the next time you sign in to this account."
    }

    fun signOut() = viewModelScope.launch { auth.signOut() }
}
