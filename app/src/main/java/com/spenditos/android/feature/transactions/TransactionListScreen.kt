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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
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
    val monthLabel = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val totalCount = state.days.sumOf { (_, rows) -> rows.size }

    IosScreen {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(includeCategories = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.fillMaxSize().testTag("transactions.list"),
            ) {
                item {
                    MonthNavigator(
                        monthLabel = monthLabel,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        modifier = Modifier.padding(horizontal = Spacing.screen).padding(top = 8.dp),
                    )
                }
                item { LargeTitle("Transactions") }

                if (!state.isOnline || state.waitingCount > 0 || state.failedCount > 0 || state.loadError != null) {
                    item {
                        SyncBanner(
                            state = state,
                            onOpenNotSynced = onOpenNotSynced,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                        )
                    }
                }

                item {
                    HistoryHeader(totalCount, Modifier.testTag("transactions.summary"))
                }

                if (state.isEmpty && state.isRefreshing) {
                    item { LoadingState() }
                } else if (state.isEmpty) {
                    item { EmptyState() }
                }

                state.days.forEach { (day, rows) ->
                    item(key = "day-$day") {
                        DayCard(day = day, rows = rows, currency = state.currency)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    monthLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f).testTag("transactions.month"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryHeader(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Spacing.s))
        Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            "$count ${if (count == 1) "transaction" else "transactions"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SyncBanner(state: TransactionListUiState, onOpenNotSynced: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, text, tint) = when {
        state.failedCount > 0 -> Triple(Icons.Filled.ErrorOutline, "${state.failedCount} not synced. Tap to review.", MaterialTheme.colorScheme.error)
        !state.isOnline && state.waitingCount > 0 -> Triple(Icons.Filled.CloudOff, "Offline. ${state.waitingCount} waiting to sync.", MaterialTheme.colorScheme.primary)
        !state.isOnline -> Triple(Icons.Filled.CloudOff, "Offline. Showing what's saved on this device.", MaterialTheme.colorScheme.primary)
        state.waitingCount > 0 -> Triple(Icons.Filled.Schedule, "${state.waitingCount} waiting to sync.", MaterialTheme.colorScheme.primary)
        else -> Triple(Icons.Filled.CloudOff, state.loadError ?: "", MaterialTheme.colorScheme.error)
    }
    GlassCard(
        modifier = modifier
            .clickable(
                enabled = state.waitingCount + state.failedCount > 0,
                role = Role.Button,
                onClick = onOpenNotSynced,
            )
            .testTag("transactions.syncBanner"),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(Spacing.s))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DayCard(day: LocalDate, rows: List<TransactionRowUi>, currency: String) {
    val label = when (day) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val shape = RoundedCornerShape(Radius.l)
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = shape) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)).padding(horizontal = Spacing.m, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                rows.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TransactionRow(row = row, currency = currency)
        }
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUi, currency: String) {
    val finance = LocalFinanceColors.current
    val tint = categoryColor(row.categoryColor)
    val icon = categoryIcon(row.categoryName)
    val subtitle = when (row.syncStatus) {
        SyncStatus.PENDING -> "${row.categoryName} · Waiting to sync"
        SyncStatus.FAILED -> "${row.categoryName} · Not synced"
        SyncStatus.SYNCED -> row.categoryName
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m).testTag("transaction.row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(12.dp).background(tint, CircleShape))
        CategoryTile(icon = icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.syncStatus == SyncStatus.FAILED) finance.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Money.signed(row.amount, row.isIncome, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (row.isIncome) finance.income else finance.expense,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(Radius.m)).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
    }
}

private fun categoryIcon(categoryName: String): ImageVector {
    val value = categoryName.lowercase()
    return when {
        listOf("food", "drink", "coffee", "restaurant", "dining").any { value.contains(it) } -> Icons.Filled.Restaurant
        listOf("shop", "grocery", "market").any { value.contains(it) } -> Icons.Filled.ShoppingCart
        listOf("car", "transport", "transit", "travel").any { value.contains(it) } -> Icons.Filled.DirectionsCar
        listOf("home", "housing", "rent").any { value.contains(it) } -> Icons.Filled.Home
        listOf("health", "medical", "fitness").any { value.contains(it) } -> Icons.Filled.Favorite
        listOf("entertainment", "movie", "fun").any { value.contains(it) } -> Icons.Filled.Movie
        else -> Icons.Filled.Category
    }
}

@Composable
private fun EmptyState() {
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Filled.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("No transactions this month", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Tap + to log what you spent or earned.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
