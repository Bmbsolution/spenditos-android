package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.CategorySummary
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reports", style = MaterialTheme.typography.titleLarge) },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("reports.hub.list"),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { ReportsHeroCard() }
                if (state.dashboard == null || state.isEmpty) item {
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
                    item {
                        MonthlyReviewCard(
                            dashboard,
                            state.currency,
                            state.month,
                            locale,
                            viewModel::previousMonth,
                            viewModel::nextMonth,
                        )
                    }
                    item { WhereMoneyWentCard(dashboard.categoryBreakdown, state.currency) }
                }
                item {
                    ReportNavigationCard(
                        title = "Daily Spending Trends",
                        detail = "Track daily spending patterns and identify high-spend days.",
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        iconTint = LocalFinanceColors.current.expense,
                        tag = "reports.hub.daily",
                        onClick = onOpenDaily,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Month Comparison",
                        detail = "Compare this month's spending to last month.",
                        icon = Icons.Filled.CalendarMonth,
                        iconTint = Color(0xFF8E67B2),
                        tag = "reports.hub.comparison",
                        onClick = onOpenComparison,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Paycheck Periods",
                        detail = "See how your income covers expenses across pay periods.",
                        icon = Icons.Filled.AccountBalance,
                        iconTint = LocalFinanceColors.current.income,
                        tag = "reports.hub.paycheck",
                        onClick = onOpenPaycheckPeriods,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsHeroCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                "REPORTING",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Insights and planning tools for your month",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Track day-to-day trends and income coverage from one place.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthlyReviewCard(
    data: com.spenditos.android.data.model.DashboardData,
    currency: String,
    month: LocalDate,
    locale: Locale,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val finance = LocalFinanceColors.current
    val summary = data.summary ?: return
    val topCategory = categorySlices(data.categoryBreakdown).firstOrNull()
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.hub.review")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Monthly review", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        data.dateRange?.startDate?.toString() ?: month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPreviousMonth, modifier = Modifier.testTag("reports.hub.month.previous")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                IconButton(onClick = onNextMonth, modifier = Modifier.testTag("reports.hub.month.next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Column(
                Modifier.fillMaxWidth().testTag("reports.hub.review.metrics"),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ReviewMetric("Income", Money.format(summary.totalIncome, currency), finance.income, Modifier.weight(1f))
                    ReviewMetric("Spending", Money.format(summary.totalExpenses, currency), finance.expense, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ReviewMetric(
                        "Net",
                        Money.format(summary.netAmount, currency),
                        if (summary.netAmount >= 0) finance.income else finance.expense,
                        Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Takeaways", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (topCategory == null) {
                    ReportTakeawayRow(
                        icon = Icons.Filled.BarChart,
                        tint = finance.neutral,
                        title = "No spending pattern yet",
                        detail = "Log activity to make this takeaway useful.",
                    )
                } else {
                    ReportTakeawayRow(
                        icon = Icons.Filled.BarChart,
                        tint = finance.expense,
                        title = "${topCategory.name} leads spending",
                        detail = "Largest category · ${Money.format(topCategory.amount.toDouble(), currency)}",
                    )
                }
                ReportTakeawayRow(
                    icon = Icons.Filled.Repeat,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Review recurring obligations",
                    detail = "Add regular bills or income to improve forecasts.",
                )
            }
        }
    }
}

@Composable
private fun ReportTakeawayRow(icon: ImageVector, tint: Color, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        IconChip(icon, tint, size = 30.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewMetric(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun WhereMoneyWentCard(categories: List<CategorySummary>, currency: String) {
    val slices = categorySlices(categories)
    val total = slices.fold(BigDecimal.ZERO) { sum, slice -> sum.add(slice.amount) }
    val palette = listOf(Color(0xFFB85F42), Color(0xFFD6A15C), Color(0xFF6F8FA6), Color(0xFF8A8F98))
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.hub.whereMoneyWent")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Where your money went",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text("This month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (total.signum() <= 0) {
                Text("No spending logged this month yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    slices.forEachIndexed { index, slice ->
                        val fraction = slice.amount.divide(total, 6, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0.01f)
                        Box(Modifier.weight(fraction).fillMaxHeight().background(palette[index % palette.size]))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    slices.forEachIndexed { index, slice ->
                        Row(
                            Modifier.fillMaxWidth().testTag("reports.category.${slice.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(palette[index % palette.size]))
                            Text(slice.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(Money.format(slice.amount.toDouble(), currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val percent = slice.amount.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                            Text("$percent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportNavigationCard(
    title: String,
    detail: String,
    icon: ImageVector,
    iconTint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag(tag), onClick = onClick) {
        Row(
            Modifier.padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            IconChip(icon, iconTint, size = 44.dp, shape = RoundedCornerShape(Spacing.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
