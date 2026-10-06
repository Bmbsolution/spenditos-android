package com.spenditos.android.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun MonthComparisonScreen(
    onBack: () -> Unit,
) {
    val viewModel: MonthComparisonViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var showIncome by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.load() }
    val previousMonth = state.month.minusMonths(1).withDayOfMonth(1)
    val weekly = if (showIncome) viewModel.weeklyIncome() else viewModel.weeklyExpenses()
    val maxValue = viewModel.maxWeeklyValue(weekly)

    Scaffold(topBar = { ReportsBackBar("Month comparison", onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("reports.comparison.list"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    ReportMonthSelector(
                        title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        subtitle = "${previousMonth.format(DateTimeFormatter.ofPattern("MMM d", locale))} – ${state.month.withDayOfMonth(state.month.lengthOfMonth()).format(DateTimeFormatter.ofPattern("MMM d", locale))}",
                        tag = "reports.comparison.month",
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
                if (state.isLoading) {
                    item { ReportLoadingCard("Loading comparison…") }
                } else if (state.loadStatus != null && !state.hasLoaded) {
                    item { ReportsStatusCard(requireNotNull(state.loadStatus), viewModel::refresh) }
                } else if (state.isEmpty && state.loadStatus == null) {
                    item { ReportEmptyCard("No comparison data", "No activity was found for these months.") }
                } else if (state.hasLoaded) {
                    state.loadStatus?.let { status -> item { ReportInlineStatus(status, viewModel::refresh, "reports.comparison.status") } }
                    item { ComparisonSummaryCards(state, viewModel) }
                    item {
                        Row(
                            Modifier.fillMaxWidth().testTag("reports.comparison.metricSelector"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = !showIncome,
                                onClick = { showIncome = false },
                                label = { Text("Expenses") },
                                modifier = Modifier.testTag("reports.comparison.metric.expenses"),
                            )
                            FilterChip(
                                selected = showIncome,
                                onClick = { showIncome = true },
                                label = { Text("Income") },
                                modifier = Modifier.padding(start = Spacing.s).testTag("reports.comparison.metric.income"),
                            )
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text("Weekly ${if (showIncome) "income" else "expenses"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                WeeklyComparisonChart(weekly, maxValue, showIncome)
                                Row(Modifier.testTag("reports.comparison.legend"), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                    val metricColor = if (showIncome) LocalFinanceColors.current.income else LocalFinanceColors.current.expense
                                    ChartLegendDot(metricColor.copy(alpha = 0.35f), previousMonth.format(DateTimeFormatter.ofPattern("MMM", locale)))
                                    ChartLegendDot(metricColor, state.month.format(DateTimeFormatter.ofPattern("MMM", locale)))
                                }
                                weekly.forEach { item ->
                                    WeeklyValueRow(item, state.currency, item.week)
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
private fun ComparisonSummaryCards(state: MonthComparisonUiState, viewModel: MonthComparisonViewModel) {
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.testTag("reports.comparison.metrics"), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ComparisonMetricCard(
                "Total expenses", state.currentTotalExpenses, state.previousTotalExpenses, true,
                state.currency, viewModel, Modifier.weight(1f),
            )
            ComparisonMetricCard(
                "Total income", state.currentTotalIncome, state.previousTotalIncome, false,
                state.currency, viewModel, Modifier.weight(1f),
            )
        }
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("reports.comparison.net")) {
            Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Net", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(state.currentNet.toDouble(), state.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    viewModel.percentageChange(state.currentNet, state.previousNet),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (viewModel.changeIsPositive(state.currentNet, state.previousNet, false)) finance.income else finance.expense,
                )
            }
        }
    }
}

@Composable
private fun ComparisonMetricCard(
    title: String,
    current: BigDecimal,
    previous: BigDecimal,
    isExpense: Boolean,
    currency: String,
    viewModel: MonthComparisonViewModel,
    modifier: Modifier,
) {
    val finance = LocalFinanceColors.current
    Card(shape = RoundedCornerShape(16.dp), modifier = modifier.testTag("reports.comparison.metric.${if (isExpense) "expenses" else "income"}")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(current.toDouble(), currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                viewModel.percentageChange(current, previous),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (viewModel.changeIsPositive(current, previous, isExpense)) finance.income else finance.expense,
            )
        }
    }
}

@Composable
private fun WeeklyValueRow(item: WeeklyComparison, currency: String, week: Int) {
    Row(
        Modifier.fillMaxWidth().testTag("reports.comparison.week.$week"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Week $week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(item.current.toDouble(), currency), style = MaterialTheme.typography.bodyMedium)
            Text(Money.format(item.previous.toDouble(), currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
