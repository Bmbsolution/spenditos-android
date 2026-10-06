package com.spenditos.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.AccountOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPasswordText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val requirements: List<PasswordRequirement> get() = PasswordRules.requirements(password)
    val strength: PasswordStrength get() = PasswordRules.strength(password)
    val passwordsMatch: Boolean get() = password == confirmPasswordText
    val canRegister: Boolean get() = email.isNotBlank() && password.length <= 128 &&
        requirements.all { it.isMet } && passwordsMatch && !isLoading
}

sealed interface RegisterEvent {
    data class Registered(val email: String) : RegisterEvent
}

@HiltViewModel
class RegisterViewModel @Inject constructor(private val accounts: AccountOperations) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()
    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPasswordText = value, error = null) }

    fun register() {
        val current = _state.value
        if (!current.canRegister) return
        _state.update { it.copy(isLoading = true, error = null) }
        // Trimmed like sign-in: a keyboard's trailing space would fail the server's email check.
        val email = current.email.trim()
        viewModelScope.launch {
            val result = runCatching { accounts.register(email, current.password, ZoneId.systemDefault().id) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(password = "", confirmPasswordText = "", isLoading = false) }
                    _events.send(RegisterEvent.Registered(email))
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = accountErrorMessage(error)) }
                },
            )
        }
    }
}

internal fun accountErrorMessage(error: Throwable): String = when (error) {
    is ApiError.Network -> "Can't reach Spenditos. Check your connection and try again."
    is ApiError.Server -> ACCOUNT_ERROR_COPY[error.serverMessage] ?: error.serverMessage
    else -> "Something went wrong. Please try again."
}

/**
 * `ApiError.Server.serverMessage` is the envelope's `error` code when present (as iOS), so these
 * handler codes (packages/functions/src/auth.ts register 282-299, confirm-password 1028-1052,
 * confirm-registration 1093-1140) would otherwise be shown verbatim.
 */
private val ACCOUNT_ERROR_COPY = mapOf(
    "UserAlreadyExists" to "An account with this email already exists. Sign in or reset your password.",
    "InvalidPassword" to "That password doesn't meet the requirements.",
    "CodeMismatch" to "That code isn't right. Check your email and try again.",
    "ExpiredCode" to "That code has expired. Request a new one.",
)
