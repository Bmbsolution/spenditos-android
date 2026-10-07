package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
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
            when {
                state.isLoading -> ReportLoadingState(
                    "Loading spending data...",
                    IosColors.spendOrange,
                    Modifier.fillMaxSize().testTag("reports.daily.list"),
                )
                state.isEmpty && state.loadStatus == null -> Box(
                    Modifier.fillMaxSize().testTag("reports.daily.list"),
                ) {
                    ReportEmptyState(
                        icon = "chart.bar.fill",
                        title = "No Spending Data",
                        subtitle = "No daily spending aggregates were found for ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}.",
                        modifier = Modifier.fillMaxSize().testTag("reports.empty"),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("reports.daily.list"),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
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
                    if (state.loadStatus != null && state.aggregates.isEmpty()) {
                        item { ReportsStatusCard(requireNotNull(state.loadStatus), viewModel::refresh) }
                    } else {
                        state.loadStatus?.let { status ->
                            item { ReportInlineStatus(status, viewModel::refresh, "reports.daily.status") }
                        }
                        if (!state.isEmpty) {
                            item { DailySummaryCard(state, locale) }
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                        Text("Spending Trend", style = IosText.headline, color = IosColors.textPrimary)
                                        DailyExpenseChart(
                                            state.aggregates,
                                            state.aggregates.indices.map(viewModel::movingAverageAt),
                                            state.summary.averageDailyExpense ?: BigDecimal.ZERO,
                                            state.currency,
                                            locale,
                                        )
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    Text("Daily Breakdown", style = IosText.headline, color = IosColors.textPrimary)
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        state.aggregates.forEachIndexed { index, aggregate ->
                                            DailyAggregateRow(aggregate, state, locale)
                                            if (index != state.aggregates.lastIndex) {
                                                HorizontalDivider(
                                                    Modifier.padding(horizontal = Spacing.m),
                                                    color = Color.Black.copy(alpha = 0.06f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
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
            Row(Modifier.testTag("reports.daily.summary.incomeExpenses")) {
                SummaryValue("Income", Money.format(summary.totalIncome.toDouble(), state.currency), finance.income, Modifier.weight(1f))
                SummaryValue("Expenses", Money.format(summary.totalExpenses.toDouble(), state.currency), finance.expense, Modifier.weight(1f))
            }
            Row(Modifier.testTag("reports.daily.summary.highLow")) {
                SummaryValue("Avg/Day", Money.format((summary.averageDailyExpense ?: BigDecimal.ZERO).toDouble(), state.currency), IosColors.spendOrange, Modifier.weight(1f))
                SummaryValue("Highest Day", summary.highestSpendingDay?.format(DateTimeFormatter.ofPattern("MMM d", locale)) ?: "—", finance.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = IosText.subheadline, color = IosColors.neutral)
        Text(value, style = IosText.rounded(17, FontWeight.Medium), color = color)
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
    Row(
        Modifier.fillMaxWidth().testTag("reports.daily.row.${aggregate.date}")
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(dateLabel, style = IosText.body, color = IosColors.textPrimary)
                Text(dayLabel, style = IosText.subheadline, color = IosColors.neutral)
                if (isHigh) DayBadge("HIGHEST", finance.expense, "high")
                else if (isLow) DayBadge("LOWEST", finance.income, "low")
            }
            Text(
                "${aggregate.transactionCount} transaction${if (aggregate.transactionCount == 1) "" else "s"}",
                style = IosText.subheadline,
                color = IosColors.neutral,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Money.format(aggregate.expenses, state.currency), style = IosText.rounded(17, FontWeight.Medium), color = finance.expense)
            if (aggregate.income > 0.0) {
                Text("+${Money.format(aggregate.income, state.currency)}", style = IosText.subheadline, color = finance.income)
            }
            Text(signedMoney(net, state.currency), style = IosText.caption, color = if (net.signum() >= 0) finance.income else finance.expense)
        }
    }
}

@Composable
private fun DayBadge(label: String, color: Color, tag: String) {
    Text(
        label,
        modifier = Modifier.testTag("reports.daily.badge.$tag")
            .clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = IosText.system(9, FontWeight.Bold),
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
