package com.bmbsolution.spenditos.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.model.Budget
import com.bmbsolution.spenditos.data.model.BudgetCreateRequest
import com.bmbsolution.spenditos.data.model.BudgetUpdateRequest
import com.bmbsolution.spenditos.data.model.Category
import com.bmbsolution.spenditos.data.repository.BudgetRepository
import com.bmbsolution.spenditos.data.repository.CategoryRepository
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
class AddBudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBudgetUiState())
    val uiState: StateFlow<AddBudgetUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getCategories().collect { result ->
                result.onSuccess { categories ->
                    // Filter to expense categories only for budgets
                    val expenseCategories = categories.filter { it.type == "expense" || it.type == null }
                    _uiState.update { it.copy(categories = expenseCategories, isLoadingCategories = false) }
                }.onFailure { error ->
                    _uiState.update { it.copy(categoryError = error.message, isLoadingCategories = false) }
                }
            }
        }
    }

    fun setEditMode(budget: Budget) {
        _uiState.update { state ->
            state.copy(
                isEditMode = true,
                budgetId = budget.id,
                name = budget.name,
                amount = budget.amount.toString(),
                period = budget.period,
                selectedCategoryIds = budget.categoryIds.toMutableList(),
                rollover = budget.rollover,
                startDate = budget.startDate,
                endDate = budget.endDate
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount, amountError = null) }
    }

    fun updatePeriod(period: String) {
        _uiState.update { it.copy(period = period) }
    }

    fun toggleCategory(categoryId: String) {
        _uiState.update { state ->
            val newSelection = state.selectedCategoryIds.toMutableList()
            if (newSelection.contains(categoryId)) {
                newSelection.remove(categoryId)
            } else {
                newSelection.add(categoryId)
            }
            state.copy(selectedCategoryIds = newSelection, categoriesError = null)
        }
    }

    fun updateRollover(rollover: Boolean) {
        _uiState.update { it.copy(rollover = rollover) }
    }

    fun updateStartDate(date: String?) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun updateEndDate(date: String?) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun saveBudget(onSuccess: () -> Unit) {
        val validationResult = validate()
        if (validationResult.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    nameError = validationResult["name"],
                    amountError = validationResult["amount"],
                    categoriesError = validationResult["categories"]
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val amountValue = _uiState.value.amount.toDoubleOrNull() ?: 0.0

            if (_uiState.value.isEditMode) {
                // Update existing budget
                val request = BudgetUpdateRequest(
                    name = _uiState.value.name,
                    amount = amountValue,
                    period = _uiState.value.period,
                    categoryIds = _uiState.value.selectedCategoryIds,
                    rollover = _uiState.value.rollover
                )

                budgetRepository.updateBudget(_uiState.value.budgetId, request)
                    .onSuccess { budget ->
                        _uiState.update { it.copy(isSaving = false, savedBudget = budget) }
                        onSuccess()
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSaving = false, error = error.message) }
                    }
            } else {
                // Create new budget
                val request = BudgetCreateRequest(
                    name = _uiState.value.name,
                    amount = amountValue,
                    period = _uiState.value.period,
                    categoryIds = _uiState.value.selectedCategoryIds,
                    startDate = _uiState.value.startDate,
                    endDate = _uiState.value.endDate,
                    rollover = _uiState.value.rollover
                )

                budgetRepository.createBudget(request)
                    .onSuccess { budget ->
                        _uiState.update { it.copy(isSaving = false, savedBudget = budget) }
                        onSuccess()
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSaving = false, error = error.message) }
                    }
            }
        }
    }

    private fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (_uiState.value.name.isBlank()) {
            errors["name"] = "Budget name is required"
        }

        val amountValue = _uiState.value.amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            errors["amount"] = "Valid amount is required"
        }

        if (_uiState.value.selectedCategoryIds.isEmpty()) {
            errors["categories"] = "Select at least one category"
        }

        return errors
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { AddBudgetUiState() }
        loadCategories()
    }
}

data class AddBudgetUiState(
    val isEditMode: Boolean = false,
    val budgetId: String = "",
    val name: String = "",
    val nameError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val period: String = "monthly", // "weekly", "monthly", "yearly"
    val selectedCategoryIds: List<String> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val categoryError: String? = null,
    val categoriesError: String? = null,
    val rollover: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedBudget: Budget? = null
)
