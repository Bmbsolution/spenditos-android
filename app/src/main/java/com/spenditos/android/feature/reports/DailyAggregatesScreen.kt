package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyAggregatesScreen(
    onBack: () -> Unit,
) {
    val viewModel: DailyAggregatesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ReportsBackBar("Daily Spending", onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("reports.daily.list"),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    ReportMonthSelector(
                        title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        subtitle = monthRangeLabel(state.month, locale),
                        tag = "reports.daily.month",
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
                if (state.isLoading) {
                    item { ReportLoadingCard("Loading daily spending…") }
                } else if (state.loadStatus != null && state.aggregates.isEmpty()) {
                    item { ReportsStatusCard(requireNotNull(state.loadStatus), viewModel::refresh) }
                } else {
                    state.loadStatus?.let { status -> item { ReportInlineStatus(status, viewModel::refresh, "reports.daily.status") } }
                    if (state.loadStatus == null && state.isEmpty) {
                        item { ReportEmptyCard("No spending data", "No activity was found for ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}.") }
                    } else if (!state.isEmpty) {
                        item { DailySummaryCard(state, locale) }
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    Text("Spending Trend", style = MaterialTheme.typography.titleLarge)
                                    DailyExpenseChart(
                                        state.aggregates,
                                        state.aggregates.indices.map(viewModel::movingAverageAt),
                                        state.summary.averageDailyExpense ?: BigDecimal.ZERO,
                                        state.currency,
                                    )
                                }
                            }
                        }
                        item {
                            Column {
                                SectionHeader("Daily Breakdown", Modifier.offset(x = -Spacing.screen))
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    state.aggregates.forEachIndexed { index, aggregate ->
                                        DailyAggregateRow(aggregate, state, locale)
                                        if (index != state.aggregates.lastIndex) {
                                            HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(state: DailyAggregatesUiState, locale: Locale) {
    val finance = LocalFinanceColors.current
    val summary = state.summary
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.daily.summary")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(Modifier.testTag("reports.daily.summary.incomeExpenses"), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                SummaryValue("Income", Money.format(summary.totalIncome.toDouble(), state.currency), finance.income, Modifier.weight(1f))
                SummaryValue("Expenses", Money.format(summary.totalExpenses.toDouble(), state.currency), finance.expense, Modifier.weight(1f))
            }
            Row(Modifier.testTag("reports.daily.summary.highLow"), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                SummaryValue("Avg/Day", Money.format((summary.averageDailyExpense ?: BigDecimal.ZERO).toDouble(), state.currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryValue("Highest day", summary.highestSpendingDay?.format(DateTimeFormatter.ofPattern("MMM d", locale)) ?: "—", finance.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun DailyAggregateRow(aggregate: DailyAggregate, state: DailyAggregatesUiState, locale: Locale) {
    val finance = LocalFinanceColors.current
    val isHigh = state.summary.highestSpendingDay == aggregate.date
    val isLow = state.summary.lowestSpendingDay == aggregate.date
    val dayLabel = aggregate.date.format(DateTimeFormatter.ofPattern("EEE", locale))
    val dateLabel = aggregate.date.format(DateTimeFormatter.ofPattern("MMM d", locale))
    val net = BigDecimal.valueOf(aggregate.income).subtract(BigDecimal.valueOf(aggregate.expenses))
    Column(Modifier.fillMaxWidth().testTag("reports.daily.row.${aggregate.date}")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(dateLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isHigh) DayBadge("HIGH", finance.expense)
                    else if (isLow) DayBadge("LOW", finance.income)
                }
                Text("${aggregate.transactionCount} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(aggregate.expenses, state.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = finance.expense)
                if (aggregate.income > 0.0) Text("+${Money.format(aggregate.income, state.currency)}", style = MaterialTheme.typography.bodySmall, color = finance.income)
                Text(signedMoney(net, state.currency), style = MaterialTheme.typography.labelSmall, color = if (net.signum() >= 0) finance.income else finance.expense)
            }
        }
    }
}

@Composable
private fun DayBadge(label: String, color: androidx.compose.ui.graphics.Color) {
            Text(
                label,
                modifier = Modifier.testTag("reports.daily.badge.${label.lowercase()}")
                    .clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
}

private fun signedMoney(value: BigDecimal, currency: String): String {
    val formatted = Money.format(value.abs().toDouble(), currency)
    return if (value.signum() >= 0) "+$formatted" else "−$formatted"
}

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
