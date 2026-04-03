package com.bmbsolution.spenditos.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.billing.RevenueCatService
import com.bmbsolution.spenditos.data.model.User
import com.bmbsolution.spenditos.data.model.UserSettings
import com.bmbsolution.spenditos.data.model.UserSettingsUpdateRequest
import com.bmbsolution.spenditos.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val revenueCatService: RevenueCatService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            userRepository.getUser()
                .collect { result ->
                    result
                        .onSuccess { user ->
                            val isPro = revenueCatService.checkProAccess()
                            _uiState.update { 
                                it.copy(
                                    user = user,
                                    isPro = isPro,
                                    isLoading = false,
                                    error = null
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Failed to load user"
                                )
                            }
                        }
                }
        }
    }

    fun updateSettings(settingsUpdate: UserSettingsUpdateRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            userRepository.updateSettings(settingsUpdate)
                .onSuccess { updatedSettings ->
                    _uiState.update { state ->
                        state.copy(
                            user = state.user?.copy(settings = updatedSettings),
                            isSaving = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "Failed to update settings"
                        )
                    }
                }
        }
    }

    fun updateMainIncome(income: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            userRepository.updateMainIncome(income)
                .onSuccess { updatedUser ->
                    _uiState.update { 
                        it.copy(
                            user = updatedUser,
                            isSaving = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "Failed to update income"
                        )
                    }
                }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            
            userRepository.logout()
                .onSuccess {
                    _uiState.update { it.copy(isLoggingOut = false) }
                    onLogoutComplete()
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoggingOut = false,
                            error = error.message ?: "Logout failed"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showImportData() {
        _uiState.update { it.copy(showImportData = true) }
    }

    fun hideImportData() {
        _uiState.update { it.copy(showImportData = false) }
    }

    fun showEditIncome() {
        _uiState.update { it.copy(showEditIncome = true) }
    }

    fun hideEditIncome() {
        _uiState.update { it.copy(showEditIncome = false) }
    }
}

data class SettingsUiState(
    val user: User? = null,
    val isPro: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val error: String? = null,
    val showImportData: Boolean = false,
    val showEditIncome: Boolean = false
)
