package com.spenditos.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionListScreen(
    onOpenNotSynced: () -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), modifier = Modifier.testTag("transactions.month"))
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(includeCategories = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.fillMaxSize().testTag("transactions.list"),
            ) {
                item { MonthSummary(state) }
                if (!state.isOnline || state.waitingCount > 0 || state.failedCount > 0 || state.loadError != null) {
                    item { SyncBanner(state, onOpenNotSynced) }
                }
                if (state.isEmpty && !state.isRefreshing) {
                    item { EmptyState() }
                }
                state.days.forEach { (day, rows) ->
                    item(key = "day-$day") { DayHeader(day) }
                    item(key = "card-$day") {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            rows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 52.dp))
                                TransactionRow(row, state.currency)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummary(state: TransactionListUiState) {
    val finance = LocalFinanceColors.current
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("transactions.summary")) {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCell("Money in", Money.format(state.moneyIn.toDouble(), state.currency), finance.income, Modifier.weight(1f))
            SummaryCell("Money out", Money.format(state.moneyOut.toDouble(), state.currency), finance.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SyncBanner(state: TransactionListUiState, onOpenNotSynced: () -> Unit) {
    val (icon, text) = when {
        state.failedCount > 0 -> Icons.Filled.ErrorOutline to "${state.failedCount} not synced. Tap to review."
        !state.isOnline && state.waitingCount > 0 -> Icons.Filled.CloudOff to "Offline. ${state.waitingCount} waiting to sync."
        !state.isOnline -> Icons.Filled.CloudOff to "Offline. Showing what's saved on this device."
        state.waitingCount > 0 -> Icons.Filled.Schedule to "${state.waitingCount} waiting to sync."
        else -> Icons.Filled.CloudOff to (state.loadError ?: "")
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = state.waitingCount + state.failedCount > 0, role = Role.Button, onClick = onOpenNotSynced).testTag("transactions.syncBanner"),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.s))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val label = when (day) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs))
}

@Composable
private fun TransactionRow(row: TransactionRowUi, currency: String) {
    val finance = LocalFinanceColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp).testTag("transaction.row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(categoryColor(row.categoryColor), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when (row.syncStatus) {
                SyncStatus.PENDING -> "${row.categoryName} · Waiting to sync"
                SyncStatus.FAILED -> "${row.categoryName} · Not synced"
                SyncStatus.SYNCED -> row.categoryName
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.syncStatus == SyncStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Money.signed(row.amount, row.isIncome, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (row.isIncome) finance.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No transactions this month", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap + to log what you spent or earned.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
