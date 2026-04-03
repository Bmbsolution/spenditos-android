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
class EditTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditTransactionUiState())
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    fun loadTransaction(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Note: The TransactionApi doesn't have a get by ID endpoint
            // We need to fetch from the repository that has local cache
            // For now, this would need to be passed in or fetched from cache
            // This is a placeholder - actual implementation would require:
            // 1. Adding GET /transactions/{id} endpoint to API
            // 2. Or fetching from local cache if available
            
            // Since we don't have a getById endpoint, we'll mark this as needing
            // the transaction to be passed in from the parent screen
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    error = "Transaction data should be passed from list"
                )
            }
        }
    }

    fun setTransaction(transaction: Transaction) {
        _uiState.update {
            it.copy(
                transaction = transaction,
                isLoading = false,
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class EditTransactionUiState(
    val transaction: Transaction? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
