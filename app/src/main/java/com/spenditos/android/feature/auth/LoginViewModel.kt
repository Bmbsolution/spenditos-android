package com.spenditos.android.feature.auth

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.LoginOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsVerification: Boolean = false,
    val verificationEmail: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: LoginOperations) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null, needsVerification = false, verificationEmail = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null, needsVerification = false, verificationEmail = null) }

    fun signIn() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null, needsVerification = false, verificationEmail = null) }
        viewModelScope.launch {
            // Success flips SessionManager.state; AppRoot swaps to the main screens.
            val error = runCatching { auth.login(current.email, current.password) }.exceptionOrNull()
            _state.update {
                it.copy(
                    password = if (error == null) "" else it.password,
                    isLoading = false,
                    error = error?.let(::messageFor),
                    needsVerification = error is ApiError.Server && error.status == 403,
                    verificationEmail = if (error is ApiError.Server && error.status == 403) current.email.trim() else null,
                )
            }
        }
    }

    /** Mirrors iOS AuthViewModel: 401 = wrong credentials, 403 = email not confirmed. */
    private fun messageFor(error: Throwable): String = when {
        error is ApiError.Server && error.status == 401 -> Strings.get(R.string.auth_email_or_password_is_incorrect)
        error is ApiError.Server && error.status == 403 -> Strings.get(R.string.auth_confirm_your_email_before_signing)
        error is ApiError.Network -> Strings.get(R.string.auth_can_t_reach_spenditos_check)
        else -> Strings.get(R.string.auth_something_went_wrong_please_try)
    }
}
