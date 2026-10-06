package com.spenditos.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.auth.AuthRepository
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
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun signIn() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Success flips SessionManager.state; AppRoot swaps to the main screens.
            val error = runCatching { auth.login(current.email, current.password) }.exceptionOrNull()
            _state.update { it.copy(isLoading = false, error = error?.let(::messageFor)) }
        }
    }

    /** Mirrors iOS AuthViewModel: 401 = wrong credentials, 403 = email not confirmed. */
    private fun messageFor(error: Throwable): String = when {
        error is ApiError.Server && error.status == 401 -> "Email or password is incorrect."
        error is ApiError.Server && error.status == 403 -> "Confirm your email before signing in. Check your inbox for the code."
        error is ApiError.Network -> "Can't reach Spenditos. Check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}
