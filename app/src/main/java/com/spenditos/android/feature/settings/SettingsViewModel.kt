package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.data.auth.AuthRepository
import com.spenditos.android.data.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import com.spenditos.android.core.lock.LockAuthResult
import com.spenditos.android.core.lock.LockAvailability
import com.spenditos.android.core.lock.BiometricAuthenticator
import com.spenditos.android.core.lock.AppLockSettings
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val email: String? = null,
    val unsyncedCount: Int = 0,
    val envName: String = "",
    val stage: String = "",
    val lockAvailable: Boolean = false,
    val lockEnabled: Boolean = false,
    val lockMessage: String? = null,
    val pushEnabled: Boolean = false,
    val currency: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    session: SessionManager,
    transactions: TransactionRepository,
    environment: AppEnvironment,
    private val lockSettings: AppLockSettings,
    private val authenticator: BiometricAuthenticator,
    private val push: com.spenditos.android.data.push.PushRegistrar,
    private val userSettings: com.spenditos.android.data.user.UserSettingsActions,
    private val purchases: com.spenditos.android.data.purchases.PurchasesService,
    private val proAccess: com.spenditos.android.data.entitlement.ProAccess,
    private val languageMirror: com.spenditos.android.data.user.LanguageMirror,
) : ViewModel() {
    /** Switches the app language now (UI recreates in it) and saves the account language right away. */
    fun setLanguage(choice: com.spenditos.android.core.i18n.LanguageChoice) {
        val account = com.spenditos.android.core.i18n.AppLanguage.apply(choice)
        languageMirror.saveNow(account)
    }

    /** Effective Pro (store OR server Premium) and the welcome-Premium row text. */
    val plan: StateFlow<SettingsPlan> = proAccess.state
        .map { SettingsPlanLabel.plan(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsPlanLabel.plan(proAccess.current()))

    private val _restoreMessage = MutableStateFlow<String?>(null)

    /** Result line under Restore Purchases; null while idle. */
    val restoreMessage: StateFlow<String?> = _restoreMessage
    private var restoring = false

    /** iOS Settings > Restore Purchases. Pro itself comes from the server, which the store webhook updates. */
    fun restorePurchases() {
        if (restoring) return
        restoring = true
        _restoreMessage.value = Strings.get(R.string.settings_restoring)
        viewModelScope.launch {
            _restoreMessage.value = when (val result = purchases.restore()) {
                com.spenditos.android.data.purchases.PurchaseResult.Success -> Strings.get(R.string.settings_purchases_restored_pro_turns_on)
                com.spenditos.android.data.purchases.PurchaseResult.Cancelled -> null
                com.spenditos.android.data.purchases.PurchaseResult.Pending -> Strings.get(R.string.common_your_purchase_is_pending_pro)
                is com.spenditos.android.data.purchases.PurchaseResult.Failed -> result.message
            }
            restoring = false
        }
    }

    private val lockMessage = MutableStateFlow<String?>(null)
    private val email = (session.state.value as? SessionState.SignedIn)?.email

    val state: StateFlow<SettingsUiState> = combine(transactions.observeQueue(), lockSettings.enabled, lockMessage, push.enabled, userSettings.currency()) { queue, lockOn, message, pushOn, currency ->
        SettingsUiState(
            email, queue.size, environment.displayName, environment.stage,
            lockAvailable = authenticator.availability() == LockAvailability.AVAILABLE,
            lockEnabled = lockOn,
            lockMessage = message,
            pushEnabled = pushOn,
            currency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(email, 0, environment.displayName, environment.stage))

    /** Turning notifications on registers this device's token; off removes it. */
    fun setPush(wanted: Boolean) {
        viewModelScope.launch { push.setEnabled(wanted) }
    }

    /** Switching the lock on proves the user can pass it first, so it can never lock them out. */
    fun setLock(activity: FragmentActivity, wanted: Boolean) {
        viewModelScope.launch {
            lockMessage.value = null
            if (!wanted) { lockSettings.setEnabled(false); return@launch }
            when (val result = authenticator.authenticate(activity, Strings.get(R.string.settings_turn_on_app_lock), Strings.get(R.string.settings_confirm_it_s_you))) {
                LockAuthResult.Success -> lockSettings.setEnabled(true)
                LockAuthResult.Cancelled -> Unit
                is LockAuthResult.Failed -> lockMessage.value = Strings.get(R.string.settings_couldn_t_turn_on_the)
            }
        }
    }

    /** Plain sign-out keeps unsynced work queued for this account (iOS #227). */
    fun signOutMessage(): String {
        val count = state.value.unsyncedCount
        if (count == 0) return Strings.get(R.string.settings_are_you_sure_you_want)
        return Strings.plural(R.plurals.settings_sign_out_unsynced, count)
    }

    /** Entering Settings is a settings load: pick up Premium changes (throttled, best effort). */
    fun refreshPremium() {
        viewModelScope.launch { proAccess.refresh() }
    }

    fun signOut() = viewModelScope.launch { auth.signOut() }
}
