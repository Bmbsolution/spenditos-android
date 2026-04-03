package com.bmbsolution.spenditos.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.Budget
import com.bmbsolution.spenditos.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditBudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditBudgetUiState())
    val uiState: StateFlow<EditBudgetUiState> = _uiState.asStateFlow()

    fun setBudget(budget: Budget) {
        _uiState.update {
            it.copy(
                budget = budget,
                isLoading = false,
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class EditBudgetUiState(
    val budget: Budget? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
