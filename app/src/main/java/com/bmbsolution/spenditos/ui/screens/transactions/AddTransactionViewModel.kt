package com.bmbsolution.spenditos.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.Category
import com.bmbsolution.spenditos.data.model.Transaction
import com.bmbsolution.spenditos.data.model.TransactionCreateRequest
import com.bmbsolution.spenditos.data.model.TransactionGamificationDetails
import com.bmbsolution.spenditos.data.model.TransactionUpdateRequest
import com.bmbsolution.spenditos.data.repository.CategoryRepository
import com.bmbsolution.spenditos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getCategories().collect { result ->
                result.onSuccess { categories ->
                    _uiState.update { it.copy(categories = categories, isLoadingCategories = false) }
                }.onFailure { error ->
                    _uiState.update { it.copy(categoryError = error.message, isLoadingCategories = false) }
                }
            }
        }
    }

    fun setEditMode(transaction: Transaction) {
        _uiState.update { state ->
            state.copy(
                isEditMode = true,
                transactionId = transaction.id,
                description = transaction.description,
                amount = transaction.amount.toString(),
                type = transaction.type,
                selectedCategory = transaction.category,
                date = transaction.date.take(10), // Extract YYYY-MM-DD from ISO string
                isRecurring = transaction.isRecurring
            )
        }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description, descriptionError = null) }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount, amountError = null) }
    }

    fun updateType(type: String) {
        _uiState.update { it.copy(type = type) }
    }

    fun updateCategory(category: Category?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateDate(date: String) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateIsRecurring(isRecurring: Boolean) {
        _uiState.update { it.copy(isRecurring = isRecurring) }
    }

    fun saveTransaction(onSuccess: (TransactionGamificationDetails?) -> Unit) {
        val validationResult = validate()
        if (validationResult.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    descriptionError = validationResult["description"],
                    amountError = validationResult["amount"]
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val amountValue = _uiState.value.amount.toDoubleOrNull() ?: 0.0
            val dateString = _uiState.value.date.ifEmpty {
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            }

            if (_uiState.value.isEditMode) {
                // Update existing transaction
                val request = TransactionUpdateRequest(
                    description = _uiState.value.description,
                    amount = amountValue,
                    type = _uiState.value.type,
                    categoryId = _uiState.value.selectedCategory?.id,
                    date = dateString
                )

                transactionRepository.updateTransaction(_uiState.value.transactionId, request)
                    .onSuccess { transaction ->
                        _uiState.update { it.copy(isSaving = false, savedTransaction = transaction) }
                        onSuccess(null)
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSaving = false, error = error.message) }
                    }
            } else {
                // Create new transaction
                val request = TransactionCreateRequest(
                    description = _uiState.value.description,
                    amount = amountValue,
                    type = _uiState.value.type,
                    categoryId = _uiState.value.selectedCategory?.id,
                    date = dateString,
                    isRecurring = _uiState.value.isRecurring
                )

                transactionRepository.createTransaction(request)
                    .onSuccess { (transaction, gamificationDetails) ->
                        _uiState.update { it.copy(isSaving = false, savedTransaction = transaction) }
                        onSuccess(gamificationDetails)
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSaving = false, error = error.message) }
                    }
            }
        }
    }

    private fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (_uiState.value.description.isBlank()) {
            errors["description"] = "Description is required"
        }

        val amountValue = _uiState.value.amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            errors["amount"] = "Valid amount is required"
        }

        return errors
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { AddTransactionUiState() }
        loadCategories()
    }
}

data class AddTransactionUiState(
    val isEditMode: Boolean = false,
    val transactionId: String = "",
    val description: String = "",
    val descriptionError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val type: String = "expense", // "expense" or "income"
    val selectedCategory: Category? = null,
    val categories: List<Category> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val categoryError: String? = null,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val isRecurring: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedTransaction: Transaction? = null
)
