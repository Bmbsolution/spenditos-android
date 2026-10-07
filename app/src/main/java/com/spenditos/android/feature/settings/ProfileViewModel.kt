package com.spenditos.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val currency: String = "USD",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val success: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: UserSettingsActions,
    private val accountDeletion: com.spenditos.android.data.auth.AccountDeletion,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** After both confirmations. Success signs out, which leaves this screen. */
    fun deleteAccount() {
        if (_state.value.isDeletingAccount) return
        _state.update { it.copy(isDeletingAccount = true, deleteError = null) }
        viewModelScope.launch {
            try {
                accountDeletion.deleteAccount()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is com.spenditos.android.core.network.ApiError.Network) {
                    "You're offline. Check your connection and try again."
                } else {
                    "Couldn't delete your account. Please try again."
                }
                _state.update { it.copy(deleteError = message) }
            } finally {
                _state.update { it.copy(isDeletingAccount = false) }
            }
        }
    }

    fun dismissDeleteError() = _state.update { it.copy(deleteError = null) }

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, success = false) }

    fun onCurrencyChange(value: String) = _state.update { it.copy(currency = value, success = false) }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, isOffline = false) }
        viewModelScope.launch {
            val result = runCatching { settings.refresh() }
            result.fold(
                onSuccess = { loaded ->
                    _state.update {
                        it.copy(
                            displayName = loaded.displayName.orEmpty(),
                            currency = loaded.currency,
                            isLoading = false,
                            error = null,
                            isOffline = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isOffline = error is ApiError.Network,
                            error = when (error) {
                                is ApiError.Network -> "You're offline. Profile details may be out of date."
                                is ApiError.Server -> error.serverMessage
                                else -> "Couldn't load your profile. Try again."
                            },
                        )
                    }
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving || current.currency.isBlank()) return
        _state.update { it.copy(isSaving = true, error = null, isOffline = false, success = false) }
        val displayName = current.displayName.trim().takeIf { it.isNotEmpty() }
        val update = UserSettingsUpdate(
            currency = current.currency,
            displayName = displayName?.let(::JsonPrimitive) ?: JsonNull,
        )
        viewModelScope.launch {
            val result = runCatching { settings.updateSettings(update) }
            result.fold(
                onSuccess = { saved ->
                    _state.update {
                        it.copy(
                            displayName = saved.displayName.orEmpty(),
                            currency = saved.currency,
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
                                is ApiError.Server -> error.serverMessage
                                else -> "Couldn't save your profile. Try again."
                            },
                        )
                    }
                },
            )
        }
    }
}
