package com.bmbsolution.spenditos.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.Budget
import com.bmbsolution.spenditos.data.model.BudgetHistoryEntry
import com.bmbsolution.spenditos.data.model.OrphanBudget
import com.bmbsolution.spenditos.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
    }

    fun loadBudgets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            budgetRepository.getBudgets()
                .collect { result ->
                    result
                        .onSuccess { budgets ->
                            _uiState.update { 
                                it.copy(
                                    budgets = budgets,
                                    isLoading = false,
                                    error = null
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Failed to load budgets"
                                )
                            }
                        }
                }
        }
    }

    fun refresh() {
        loadBudgets()
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            
            budgetRepository.deleteBudget(id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            budgets = state.budgets.filter { it.id != id },
                            isDeleting = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Failed to delete budget"
                        )
                    }
                }
        }
    }

    fun loadBudgetHistory(budgetId: String) {
        viewModelScope.launch {
            budgetRepository.getBudgetHistory(budgetId)
                .onSuccess { history ->
                    _uiState.update { 
                        it.copy(
                            selectedBudgetHistory = history,
                            showHistorySheet = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(error = error.message ?: "Failed to load history")
                    }
                }
        }
    }

    fun loadOrphanBudgets() {
        viewModelScope.launch {
            budgetRepository.getOrphanBudgets()
                .onSuccess { orphans ->
                    _uiState.update { 
                        it.copy(
                            orphanBudgets = orphans,
                            showOrphansSheet = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(error = error.message ?: "Failed to load orphans")
                    }
                }
        }
    }

    fun hideHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false, selectedBudgetHistory = emptyList()) }
    }

    fun hideOrphansSheet() {
        _uiState.update { it.copy(showOrphansSheet = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class BudgetsUiState(
    val budgets: List<Budget> = emptyList(),
    val orphanBudgets: List<OrphanBudget> = emptyList(),
    val selectedBudgetHistory: List<BudgetHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showHistorySheet: Boolean = false,
    val showOrphansSheet: Boolean = false
)
