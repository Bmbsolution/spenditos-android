package com.bmbsolution.spenditos.ui.screens.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.Budget
import com.bmbsolution.spenditos.data.model.Category
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetScreen(
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit = {},
    viewModel: AddBudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Budget" else "Add Budget") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveBudget {
                                onSaveComplete()
                                onNavigateBack()
                            }
                        },
                        enabled = !uiState.isSaving
                    ) {
                        Text(if (uiState.isEditMode) "Update" else "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Budget Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    leadingIcon = {
                        Icon(Icons.Default.Label, contentDescription = null)
                    },
                    isError = uiState.nameError != null,
                    supportingText = uiState.nameError?.let { Text(it) }
                )

                // Amount Field
                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text("Budget Amount *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    leadingIcon = {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { Text(it) }
                )

                // Period Selection
                Text(
                    text = "Budget Period",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PeriodChip(
                        text = "Weekly",
                        selected = uiState.period == "weekly",
                        onClick = { viewModel.updatePeriod("weekly") },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodChip(
                        text = "Monthly",
                        selected = uiState.period == "monthly",
                        onClick = { viewModel.updatePeriod("monthly") },
                        modifier = Modifier.weight(1f)
                    )
                    PeriodChip(
                        text = "Yearly",
                        selected = uiState.period == "yearly",
                        onClick = { viewModel.updatePeriod("yearly") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Categories Selection
                Text(
                    text = "Categories *",
                    style = MaterialTheme.typography.labelLarge
                )
                if (uiState.isLoadingCategories) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    CategorySelectionGrid(
                        categories = uiState.categories,
                        selectedCategoryIds = uiState.selectedCategoryIds,
                        onToggleCategory = { viewModel.toggleCategory(it) }
                    )
                    if (uiState.categoriesError != null) {
                        Text(
                            text = uiState.categoriesError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Date Range (Optional)
                Text(
                    text = "Date Range (Optional)",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Date
                    OutlinedTextField(
                        value = uiState.startDate ?: "Start Date",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Start") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showStartDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Start Date")
                            }
                        }
                    )

                    // End Date
                    OutlinedTextField(
                        value = uiState.endDate ?: "End Date",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("End") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showEndDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select End Date")
                            }
                        }
                    )
                }

                // Rollover Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = uiState.rollover,
                            onValueChange = { viewModel.updateRollover(it) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rollover Unused Budget",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Carry over unused amount to next period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.rollover,
                        onCheckedChange = null // Handled by parent toggleable
                    )
                }

                // Error Message
                if (uiState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Loading Indicator
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        DatePickerDialog(
            onDateSelected = { viewModel.updateStartDate(it) },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDateSelected = { viewModel.updateEndDate(it) },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@Composable
private fun PeriodChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CategorySelectionGrid(
    categories: List<Category>,
    selectedCategoryIds: List<String>,
    onToggleCategory: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.chunked(2).forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCategories.forEach { category ->
                    val isSelected = selectedCategoryIds.contains(category.id)
                    CategoryChip(
                        category = category,
                        selected = isSelected,
                        onClick = { onToggleCategory(category.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if odd number
                if (rowCategories.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = try {
        category.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.primary
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) backgroundColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) backgroundColor else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selection indicator
            Surface(
                shape = CircleShape,
                color = if (selected) backgroundColor else MaterialTheme.colorScheme.surface,
                border = if (!selected) {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                } else null,
                modifier = Modifier.size(20.dp)
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                        tint = Color.White
                    )
                }
            }

            // Category color dot
            Surface(
                shape = CircleShape,
                color = backgroundColor,
                modifier = Modifier.size(8.dp)
            ) {}

            // Category name
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDateSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = datePickerState.selectedDateMillis?.let { millis ->
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    onDateSelected(date)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDateSelected(null)
                    onDismiss()
                }
            ) {
                Text("Clear")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun parseColor(colorString: String?): Color {
    return try {
        if (colorString != null) {
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            MaterialTheme.colorScheme.primary
        }
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
}
