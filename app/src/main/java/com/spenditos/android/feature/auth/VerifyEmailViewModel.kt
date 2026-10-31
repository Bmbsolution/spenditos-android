package com.spenditos.android.feature.auth

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.auth.AccountOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyEmailUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val isVerified: Boolean = false,
    val resendSecondsRemaining: Int = 0,
    val error: String? = null,
    val notice: String? = null,
) {
    val canVerify: Boolean get() = code.length == 6 && !isLoading && !isVerified
    val canResend: Boolean get() = resendSecondsRemaining == 0 && !isLoading && !isVerified
}

sealed interface VerifyEmailEvent {
    data object Verified : VerifyEmailEvent
    data object CodeResent : VerifyEmailEvent
}

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(private val accounts: AccountOperations) : ViewModel() {
    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()
    private val _events = Channel<VerifyEmailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var cooldownJob: Job? = null

    fun onCodeChange(value: String) = _state.update {
        it.copy(code = value.filter { digit -> digit in '0'..'9' }.take(6), error = null)
    }

    fun verify(email: String) {
        val current = _state.value
        if (!current.canVerify) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = runCatching { accounts.confirmRegistration(email, current.code) }
            result.fold(
                onSuccess = {
                    cooldownJob?.cancel()
                    _state.update { it.copy(code = "", isLoading = false, isVerified = true, notice = Strings.get(R.string.auth_email_confirmed_sign_in)) }
                    _events.send(VerifyEmailEvent.Verified)
                },
                onFailure = { error -> _state.update { it.copy(isLoading = false, error = accountErrorMessage(error)) } },
            )
        }
    }

    fun resend(email: String) {
        if (!_state.value.canResend) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = runCatching { accounts.resendCode(email) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, resendSecondsRemaining = RESEND_COOLDOWN_SECONDS, notice = Strings.get(R.string.auth_code_sent_check_your_email)) }
                    startCooldown()
                    _events.send(VerifyEmailEvent.CodeResent)
                },
                onFailure = { error -> _state.update { it.copy(isLoading = false, error = accountErrorMessage(error)) } },
            )
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (_state.value.resendSecondsRemaining > 0) {
                delay(1_000)
                _state.update { it.copy(resendSecondsRemaining = (it.resendSecondsRemaining - 1).coerceAtLeast(0)) }
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 30
    }
}
