package com.bmbsolution.spenditos.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.Transaction
import com.bmbsolution.spenditos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val filters = buildMap {
                val currentFilters = _uiState.value.filters
                currentFilters.startDate?.let { put("startDate", it) }
                currentFilters.endDate?.let { put("endDate", it) }
                currentFilters.type?.let { put("type", it) }
                currentFilters.categoryId?.let { put("categoryId", it) }
                currentFilters.searchQuery?.takeIf { it.isNotBlank() }?.let { put("search", it) }
            }
            
            transactionRepository.fetchTransactions(filters)
                .onSuccess { transactions ->
                    _uiState.update { 
                        it.copy(
                            transactions = transactions,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load transactions"
                        )
                    }
                }
        }
    }

    fun refresh() {
        loadTransactions()
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            
            transactionRepository.deleteTransaction(id)
                .onSuccess {
                    // Remove from list
                    _uiState.update { state ->
                        state.copy(
                            transactions = state.transactions.filter { it.id != id },
                            isDeleting = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Failed to delete transaction"
                        )
                    }
                }
        }
    }

    fun updateFilters(filters: TransactionFilters) {
        _uiState.update { it.copy(filters = filters) }
        loadTransactions()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun showFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = true) }
    }

    fun hideFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun showAddTransactionSheet() {
        _uiState.update { it.copy(showAddTransaction = true) }
    }

    fun hideAddTransactionSheet() {
        _uiState.update { it.copy(showAddTransaction = false) }
    }
}

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val filters: TransactionFilters = TransactionFilters(),
    val showFilterSheet: Boolean = false,
    val showAddTransaction: Boolean = false
)

data class TransactionFilters(
    val startDate: String? = null,
    val endDate: String? = null,
    val type: String? = null, // "expense" or "income"
    val categoryId: String? = null,
    val searchQuery: String? = null
)
