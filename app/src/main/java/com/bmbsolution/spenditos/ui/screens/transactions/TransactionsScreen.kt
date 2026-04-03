package com.bmbsolution.spenditos.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.Transaction
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToEditTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadTransactions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showFilterSheet() }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddTransaction,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.updateFilters(
                            uiState.filters.copy(searchQuery = it)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                viewModel.updateFilters(uiState.filters.copy(searchQuery = null))
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )

                // Active filters chip row
                if (hasActiveFilters(uiState.filters)) {
                    ActiveFiltersRow(
                        filters = uiState.filters,
                        onClearFilters = {
                            searchQuery = ""
                            viewModel.updateFilters(TransactionFilters())
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Content
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isLoading && uiState.transactions.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (uiState.transactions.isEmpty()) {
                        EmptyTransactionsState(
                            onAddTransaction = onNavigateToAddTransaction,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        PullToRefreshBox(
                            state = pullRefreshState,
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Group by date
                                val grouped = uiState.transactions.groupBy { transaction ->
                                    try {
                                        LocalDate.parse(transaction.date.take(10))
                                    } catch (e: DateTimeParseException) {
                                        LocalDate.now()
                                    }
                                }.toSortedMap(compareByDescending { it })

                                grouped.forEach { (date, transactions) ->
                                    item {
                                        DateHeader(date = date)
                                    }
                                    
                                    items(
                                        items = transactions,
                                        key = { it.id }
                                    ) { transaction ->
                                        TransactionItem(
                                            transaction = transaction,
                                            onClick = { onNavigateToEditTransaction(transaction.id) },
                                            onDelete = { viewModel.deleteTransaction(transaction.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Filter bottom sheet
    if (uiState.showFilterSheet) {
        FilterBottomSheet(
            filters = uiState.filters,
            onApplyFilters = { viewModel.updateFilters(it) },
            onDismiss = { viewModel.hideFilterSheet() }
        )
    }
}

@Composable
private fun hasActiveFilters(filters: TransactionFilters): Boolean {
    return filters.type != null || 
           filters.categoryId != null || 
           filters.startDate != null || 
           filters.endDate != null ||
           !filters.searchQuery.isNullOrBlank()
}

@Composable
private fun ActiveFiltersRow(
    filters: TransactionFilters,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.type?.let {
            FilterChip(
                selected = true,
                onClick = { },
                label = { Text(it.capitalize()) },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        TextButton(onClick = onClearFilters) {
            Text("Clear all")
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val dateText = when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    }
    
    Text(
        text = dateText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")
    
    val isExpense = transaction.type == "expense"
    val amountColor = if (isExpense) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val amountPrefix = if (isExpense) "-" else "+"
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    transaction.category?.let { category ->
                        AssistChip(
                            onClick = { },
                            label = { Text(category.name) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    if (transaction.isRecurring) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$amountPrefix${currencyFormat.format(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTransactionsState(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        
        Text(
            text = "No transactions yet",
            style = MaterialTheme.typography.titleMedium
        )
        
        Text(
            text = "Start tracking your expenses by adding your first transaction",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Button(onClick = onAddTransaction) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Transaction")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    filters: TransactionFilters,
    onApplyFilters: (TransactionFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var localFilters by remember { mutableStateOf(filters) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Filter Transactions",
                style = MaterialTheme.typography.titleLarge
            )
            
            // Transaction type filter
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = localFilters.type == null,
                    onClick = { localFilters = localFilters.copy(type = null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = localFilters.type == "expense",
                    onClick = { localFilters = localFilters.copy(type = "expense") },
                    label = { Text("Expenses") }
                )
                FilterChip(
                    selected = localFilters.type == "income",
                    onClick = { localFilters = localFilters.copy(type = "income") },
                    label = { Text("Income") }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date range
            Text(
                text = "Date Range",
                style = MaterialTheme.typography.labelMedium
            )
            
            // Quick date filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val today = LocalDate.now()
                
                FilterChip(
                    selected = localFilters.startDate == today.toString() && localFilters.endDate == today.toString(),
                    onClick = { 
                        localFilters = localFilters.copy(
                            startDate = today.toString(),
                            endDate = today.toString()
                        )
                    },
                    label = { Text("Today") }
                )
                
                val thisWeekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                FilterChip(
                    selected = localFilters.startDate == thisWeekStart.toString() && localFilters.endDate == today.toString(),
                    onClick = { 
                        localFilters = localFilters.copy(
                            startDate = thisWeekStart.toString(),
                            endDate = today.toString()
                        )
                    },
                    label = { Text("This Week") }
                )
                
                val thisMonthStart = today.withDayOfMonth(1)
                FilterChip(
                    selected = localFilters.startDate == thisMonthStart.toString() && localFilters.endDate == today.toString(),
                    onClick = { 
                        localFilters = localFilters.copy(
                            startDate = thisMonthStart.toString(),
                            endDate = today.toString()
                        )
                    },
                    label = { Text("This Month") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        onApplyFilters(localFilters)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
