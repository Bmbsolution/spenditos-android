package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import com.spenditos.android.data.model.CategorySummary
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportsHubScreen(
    onOpenDaily: () -> Unit,
    onOpenComparison: () -> Unit,
    onOpenPaycheckPeriods: () -> Unit,
) {
    val viewModel: ReportsHubViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(topBar = { androidx.compose.material3.CenterAlignedTopAppBar(title = { Text("Reports") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("reports.hub.list"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Reporting", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Review your month and spending patterns.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    ReportMonthSelector(
                        title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        subtitle = monthRangeLabel(state.month, locale),
                        tag = "reports.hub.month",
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
                if (state.isLoading) {
                    item { ReportLoadingCard("Preparing your monthly review…") }
                } else if (state.loadStatus != null && state.dashboard == null) {
                    val status = requireNotNull(state.loadStatus)
                    item { ReportsStatusCard(status, viewModel::refresh) }
                } else if (state.isEmpty && state.loadStatus == null) {
                    item {
                        ReportEmptyCard(
                            "No activity this month",
                            "Add an entry to see your income and spending here.",
                        )
                    }
                } else if (state.dashboard != null) {
                    val dashboard = requireNotNull(state.dashboard)
                    state.loadStatus?.let { status -> item { ReportInlineStatus(status, viewModel::refresh, "reports.hub.status") } }
                    item { MonthlyReviewCard(dashboard, state.currency) }
                    item { WhereMoneyWentCard(dashboard.categoryBreakdown, state.currency) }
                }
                item {
                    ReportNavigationCard(
                        title = "Daily spending",
                        detail = "See daily totals and spending highs and lows.",
                        tag = "reports.hub.daily",
                        onClick = onOpenDaily,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Month comparison",
                        detail = "Compare this month with last month.",
                        tag = "reports.hub.comparison",
                        onClick = onOpenComparison,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Paycheck periods",
                        detail = "See how income covers expenses across periods.",
                        tag = "reports.hub.paycheck",
                        onClick = onOpenPaycheckPeriods,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyReviewCard(data: com.spenditos.android.data.model.DashboardData, currency: String) {
    val finance = LocalFinanceColors.current
    val summary = data.summary ?: return
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("reports.hub.review")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Monthly review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.testTag("reports.hub.review.metrics"), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ReviewMetric("Income", Money.format(summary.totalIncome, currency), finance.income, Modifier.weight(1f))
                ReviewMetric("Expenses", Money.format(summary.totalExpenses, currency), finance.expense, Modifier.weight(1f))
                ReviewMetric("Net", Money.format(summary.netAmount, currency), if (summary.netAmount >= 0) finance.income else finance.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReviewMetric(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun WhereMoneyWentCard(categories: List<CategorySummary>, currency: String) {
    val slices = categorySlices(categories)
    val total = slices.fold(BigDecimal.ZERO) { sum, slice -> sum.add(slice.amount) }
    val palette = listOf(Color(0xFFB85F42), Color(0xFFD6A15C), Color(0xFF6F8FA6), Color(0xFF8A8F98))
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("reports.hub.whereMoneyWent")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Where money went", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (total.signum() <= 0) {
                Text("No spending logged this month yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                    slices.forEachIndexed { index, slice ->
                        val fraction = slice.amount.divide(total, 6, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0.01f)
                        Box(Modifier.weight(fraction).fillMaxHeight().background(palette[index % palette.size]))
                    }
                }
                slices.forEachIndexed { index, slice ->
                    Row(
                        Modifier.fillMaxWidth().testTag("reports.category.${slice.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(palette[index % palette.size]))
                        Text(slice.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(slice.amount.toDouble(), currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val percent = slice.amount.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                        Text("$percent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportNavigationCard(title: String, detail: String, tag: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
