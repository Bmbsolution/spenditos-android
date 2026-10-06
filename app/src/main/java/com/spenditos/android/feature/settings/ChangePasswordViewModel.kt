package com.spenditos.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val success: Boolean = false,
) {
    val requirements: List<PasswordRequirement> get() = passwordRequirements(newPassword)
    val canSave: Boolean get() =
        !isSaving && currentPassword.isNotEmpty() && requirements.first().isMet && newPassword == confirmPassword
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val settings: UserSettingsActions,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrentPasswordChange(value: String) = _state.update { it.copy(currentPassword = value, error = null, success = false) }

    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, error = null, success = false) }

    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null, success = false) }

    fun changePassword() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true, error = null, isOffline = false, success = false) }
        viewModelScope.launch {
            val result = runCatching { settings.changePassword(current.currentPassword, current.newPassword) }
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            isSaving = false,
                            success = true,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            isOffline = error is ApiError.Network,
                            error = when (error) {
                                is ApiError.Network -> "You're offline. Try again when you're connected."
                                is ApiError.Server -> CHANGE_PASSWORD_ERROR_COPY[error.serverMessage] ?: error.serverMessage
                                else -> "Couldn't change your password. Try again."
                            },
                        )
                    }
                },
            )
        }
    }
}

/** Error codes of `POST /user/change-password` (packages/functions/src/user.ts:797-812). */
private val CHANGE_PASSWORD_ERROR_COPY = mapOf(
    "InvalidPassword" to "Your current password is incorrect.",
    "WeakPassword" to "Your new password doesn't meet the requirements.",
    "RateLimited" to "Too many attempts. Try again later.",
)
