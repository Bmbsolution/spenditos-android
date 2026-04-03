package com.bmbsolution.spenditos.ui.screens.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.Transaction
import com.bmbsolution.spenditos.data.model.TransactionGamificationDetails

@Composable
fun EditTransactionScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit,
    viewModel: EditTransactionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        }
        uiState.transaction != null -> {
            // Reuse AddTransactionScreen with edit mode
            EditTransactionContent(
                transaction = uiState.transaction!!,
                onNavigateBack = onNavigateBack,
                onSaveComplete = onSaveComplete
            )
        }
    }
}

@Composable
private fun EditTransactionContent(
    transaction: Transaction,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    // Set the transaction data for editing
    LaunchedEffect(transaction) {
        viewModel.setEditMode(transaction)
    }

    // Reuse the AddTransactionScreen UI
    AddTransactionScreen(
        onNavigateBack = onNavigateBack,
        onSaveComplete = { _ ->
            onSaveComplete()
        },
        viewModel = viewModel
    )
}
