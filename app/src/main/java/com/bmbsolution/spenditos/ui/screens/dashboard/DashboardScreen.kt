package com.bmbsolution.spenditos.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGamification: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spenditos") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Transactions") },
                    label = { Text("Transactions") },
                    selected = false,
                    onClick = onNavigateToTransactions
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Budgets") },
                    label = { Text("Budgets") },
                    selected = false,
                    onClick = onNavigateToBudgets
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Game") },
                    label = { Text("Game") },
                    selected = false,
                    onClick = onNavigateToGamification
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gamification Summary Card
            item {
                GamificationCard(
                    level = uiState.level,
                    levelTitle = uiState.levelTitle,
                    currentPoints = uiState.currentPoints,
                    pointsToNextLevel = uiState.pointsToNextLevel,
                    currentStreak = uiState.currentStreak
                )
            }

            // Summary Card
            item {
                SummaryCard(
                    totalIncome = uiState.totalIncome,
                    totalExpenses = uiState.totalExpenses,
                    netAmount = uiState.netAmount
                )
            }

            // Spending by Category
            if (uiState.spendingByCategory.isNotEmpty()) {
                item {
                    CategorySpendingCard(categories = uiState.spendingByCategory)
                }
            }

            // Quick Actions
            item {
                QuickActionsRow(
                    onAddTransaction = onNavigateToAddTransaction,
                    onScanReceipt = onNavigateToPaywall,
                    onImportData = onNavigateToPaywall
                )
            }
        }
    }
}

@Composable
private fun GamificationCard(
    level: Int,
    levelTitle: String,
    currentPoints: Int,
    pointsToNextLevel: Int,
    currentStreak: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Level $level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = levelTitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$currentStreak",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = if (pointsToNextLevel > 0) {
                    (currentPoints.toFloat() / (currentPoints + pointsToNextLevel))
                } else 1f,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "$currentPoints / ${currentPoints + pointsToNextLevel} points to next level",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    totalIncome: Double,
    totalExpenses: Double,
    netAmount: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "This Period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    label = "Income",
                    amount = "+$${String.format("%.2f", totalIncome)}",
                    isPositive = true
                )
                SummaryItem(
                    label = "Expenses",
                    amount = "-$${String.format("%.2f", totalExpenses)}",
                    isPositive = false
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${if (netAmount >= 0) "+" else ""}$${String.format("%.2f", netAmount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (netAmount >= 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: String, isPositive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isPositive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun CategorySpendingCard(categories: List<CategorySpendingUi>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Top Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            categories.take(5).forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$${String.format("%.2f", category.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onAddTransaction: () -> Unit,
    onScanReceipt: () -> Unit,
    onImportData: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.Default.Add,
            label = "Add",
            onClick = onAddTransaction
        )
        QuickActionButton(
            icon = Icons.Default.CameraAlt,
            label = "Scan",
            onClick = onScanReceipt
        )
        QuickActionButton(
            icon = Icons.Default.Upload,
            label = "Import",
            onClick = onImportData
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

data class CategorySpendingUi(
    val id: String,
    val name: String,
    val amount: Double,
    val percentage: Double
)
