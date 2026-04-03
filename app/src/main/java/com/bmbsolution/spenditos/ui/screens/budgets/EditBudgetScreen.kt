package com.bmbsolution.spenditos.ui.screens.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.Budget

@Composable
fun EditBudgetScreen(
    budget: Budget,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit,
    viewModel: EditBudgetViewModel = hiltViewModel(),
    addBudgetViewModel: AddBudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Set the budget data for editing
    LaunchedEffect(budget) {
        viewModel.setBudget(budget)
        addBudgetViewModel.setEditMode(budget)
    }

    // Reuse the AddBudgetScreen UI
    AddBudgetScreen(
        onNavigateBack = onNavigateBack,
        onSaveComplete = onSaveComplete,
        viewModel = addBudgetViewModel
    )
}
