package com.spenditos.android.feature.reports

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ReportsBackBar("Month Comparison", onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> ReportLoadingState(
                    "Loading comparison data...",
                    Color(0xFFAF52DE),
                    Modifier.fillMaxSize().testTag("reports.comparison.list"),
                )
                state.isEmpty && state.loadStatus == null -> Box(Modifier.fillMaxSize().testTag("reports.comparison.list")) {
                    ReportEmptyState(
                        icon = "calendar.badge.clock",
                        title = "No Data",
                        subtitle = "No daily aggregates found for comparison.",
                        modifier = Modifier.fillMaxSize().testTag("reports.empty"),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("reports.comparison.list"),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    item {
                        ReportMonthSelector(
                            title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                            subtitle = monthRangeLabel(state.month, locale),
                            tag = "reports.comparison.month",
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                        )
                    }
                    if (state.loadStatus != null && !state.hasLoaded) {
                        item { ReportsStatusCard(requireNotNull(state.loadStatus), viewModel::refresh) }
                    } else if (state.hasLoaded) {
                        state.loadStatus?.let { status ->
                            item { ReportInlineStatus(status, viewModel::refresh, "reports.comparison.status") }
                        }
                        item { ComparisonSummaryCards(state, viewModel) }
                        item { ComparisonMetricSelector(showIncome, { showIncome = it }) }
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                    Text(
                                        "Weekly ${if (showIncome) "Income" else "Expenses"}",
                                        style = IosText.headline,
                                        color = IosColors.textPrimary,
                                    )
                                    WeeklyComparisonChart(weekly, maxValue, showIncome, state.currency)
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag("reports.comparison.legend"),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                                    ) {
                                        val metricColor = if (showIncome) LocalFinanceColors.current.income else LocalFinanceColors.current.expense
                                        ChartLegendDot(metricColor.copy(alpha = 0.35f), previousMonth.format(DateTimeFormatter.ofPattern("MMM", locale)))
                                        ChartLegendDot(metricColor, state.month.format(DateTimeFormatter.ofPattern("MMM", locale)))
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
private fun ComparisonSummaryCards(state: MonthComparisonUiState, viewModel: MonthComparisonViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            Modifier.testTag("reports.comparison.metrics"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ComparisonMetricCard(
                "Total Spending", state.currentTotalExpenses, state.previousTotalExpenses, true,
                state.currency, viewModel, Modifier.weight(1f).testTag("reports.comparison.metric.expenses"),
            )
            ComparisonMetricCard(
                "Total Income", state.currentTotalIncome, state.previousTotalIncome, false,
                state.currency, viewModel, Modifier.weight(1f).testTag("reports.comparison.metric.income"),
            )
        }
        ComparisonMetricCard(
            "Net Change", state.currentNet, state.previousNet, false,
            state.currency, viewModel, Modifier.fillMaxWidth().testTag("reports.comparison.net"),
        )
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
    val changeColor = if (viewModel.changeIsPositive(current, previous, isExpense)) finance.income else finance.expense
    GlassCard(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title, style = IosText.subheadline, color = IosColors.neutral)
            Text(Money.format(current.toDouble(), currency), style = IosText.rounded(17, FontWeight.Medium), color = IosColors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    SfSymbols.icon(if (current >= previous) "arrow.up" else "arrow.down"),
                    contentDescription = null,
                    tint = changeColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(viewModel.percentageChange(current, previous), style = IosText.subheadline.w(FontWeight.SemiBold), color = changeColor)
            }
        }
    }
}

@Composable
private fun ComparisonMetricSelector(showIncome: Boolean, onSelectIncome: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().testTag("reports.comparison.metricSelector"), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.06f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Expenses", "Income").forEachIndexed { index, label ->
                val selected = (index == 1) == showIncome
                val tag = if (index == 0) "reports.comparison.metric.expenses" else "reports.comparison.metric.income"
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                        .clickable(role = Role.Tab) {
                            onSelectIncome(index == 1)
                            HapticFeedback.light()
                        }
                        .testTag(tag)
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosText.subheadline.w(FontWeight.SemiBold),
                        color = if (selected) IosColors.textPrimary else IosColors.textSecondary,
                    )
                }
            }
        }
    }
}

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
