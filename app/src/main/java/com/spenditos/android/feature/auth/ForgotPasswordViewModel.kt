package com.spenditos.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.auth.AccountOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ForgotPasswordStep { EMAIL, RESET }

data class ForgotPasswordUiState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val isLoading: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val canSendCode: Boolean get() = email.isNotBlank() && !isLoading && !isDone
    val canReset: Boolean get() = code.isNotBlank() && code.length <= 20 && newPassword.length in 8..128 && !isLoading && !isDone
}

sealed interface ForgotPasswordEvent {
    data object Done : ForgotPasswordEvent
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val accounts: AccountOperations) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()
    private val _events = Channel<ForgotPasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value.filter { c -> c in '0'..'9' }.take(20), error = null) }
    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, error = null) }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode || current.step != ForgotPasswordStep.EMAIL) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = runCatching { accounts.forgotPassword(current.email) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(step = ForgotPasswordStep.RESET, isLoading = false, notice = "If an account exists, a reset code is on its way.") }
                },
                onFailure = { error -> _state.update { it.copy(isLoading = false, error = accountErrorMessage(error)) } },
            )
        }
    }

    fun resetPassword() {
        val current = _state.value
        if (!current.canReset || current.step != ForgotPasswordStep.RESET) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = runCatching { accounts.confirmPassword(current.email, current.code, current.newPassword) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(code = "", newPassword = "", isLoading = false, isDone = true, notice = "Password reset. You can now sign in.") }
                    _events.send(ForgotPasswordEvent.Done)
                },
                onFailure = { error -> _state.update { it.copy(isLoading = false, error = accountErrorMessage(error)) } },
            )
        }
    }
}
