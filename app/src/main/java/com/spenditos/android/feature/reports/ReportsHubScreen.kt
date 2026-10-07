package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.CategorySummary
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ReportsHubScreen(
    onOpenDaily: () -> Unit,
    onOpenComparison: () -> Unit,
    onOpenPaycheckPeriods: () -> Unit,
    onReviewTransactions: () -> Unit = {},
    onLogTransaction: () -> Unit = {},
) {
    val viewModel: ReportsHubViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reports", style = IosText.headline, color = IosColors.textPrimary) },
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
                if (state.isLoading) {
                    item { ReportLoadingCard("Preparing your monthly review…") }
                } else if (state.loadStatus != null && state.dashboard == null) {
                    val status = requireNotNull(state.loadStatus)
                    item {
                        ReportsStatusCard(
                            status,
                            viewModel::refresh,
                            errorTitle = "Monthly review unavailable",
                            errorMessage = "We could not refresh this review. Check your connection and try again.",
                        )
                    }
                } else if (state.dashboard == null) {
                    item { ReportsEmptyReviewCard(onLogTransaction) }
                } else {
                    val dashboard = requireNotNull(state.dashboard)
                    state.loadStatus?.let { status ->
                        item { ReportInlineStatus(status, viewModel::refresh, "reports.hub.status") }
                    }
                    item {
                        if (state.isEmpty) {
                            Box(Modifier.testTag("reports.empty")) {
                                MonthlyReviewCard(
                                    dashboard,
                                    state.currency,
                                    viewModel::previousMonth,
                                    viewModel::nextMonth,
                                    state.activeRecurringCount,
                                    onReviewTransactions,
                                )
                            }
                        } else {
                            MonthlyReviewCard(
                                dashboard,
                                state.currency,
                                viewModel::previousMonth,
                                viewModel::nextMonth,
                                state.activeRecurringCount,
                                onReviewTransactions,
                            )
                        }
                    }
                    if (dashboard.categoryBreakdown.isNotEmpty()) {
                        item { WhereMoneyWentCard(dashboard.categoryBreakdown, state.currency) }
                    }
                }
                item {
                    ReportNavigationCard(
                        title = "Daily Spending Trends",
                        detail = "Track your daily spending patterns and identify high-spend days.",
                        symbol = "chart.bar.fill",
                        iconTint = IosColors.spendOrange,
                        tag = "reports.hub.daily",
                        onClick = onOpenDaily,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Paycheck Periods",
                        detail = "See how your income covers expenses across pay periods.",
                        symbol = "banknote.fill",
                        iconTint = IosColors.income,
                        tag = "reports.hub.paycheck",
                        onClick = onOpenPaycheckPeriods,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = "Month Comparison",
                        detail = "Compare this month's spending to last month.",
                        symbol = "calendar.badge.clock",
                        iconTint = Color(0xFFAF52DE),
                        tag = "reports.hub.comparison",
                        onClick = onOpenComparison,
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
            Text("REPORTING", style = IosText.subheadline, color = IosColors.textSecondary)
            Text(
                "Insights and planning tools for your month",
                style = IosText.title3.w(FontWeight.SemiBold),
                color = IosColors.textPrimary,
            )
            Text(
                "Track day-to-day trends and income coverage from one place.",
                style = IosText.callout,
                color = IosColors.textSecondary,
            )
        }
    }
}

@Composable
private fun ReportsEmptyReviewCard(onLogTransaction: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(SfSymbols.icon("chart.line.uptrend.xyaxis"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(20.dp))
                Text("Your review starts with one entry", style = IosText.headline, color = IosColors.textPrimary)
            }
            Text(
                "Log a transaction to see spending and recurring obligations together.",
                style = IosText.callout,
                color = IosColors.textSecondary,
            )
            Text(
                "Log a transaction",
                modifier = Modifier.clickable(onClick = onLogTransaction),
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = IosColors.spendOrange,
            )
        }
    }
}

@Composable
private fun MonthlyReviewCard(
    data: com.spenditos.android.data.model.DashboardData,
    currency: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    activeRecurringCount: Int,
    onReviewTransactions: () -> Unit,
) {
    val finance = LocalFinanceColors.current
    val summary = data.summary ?: return
    val topCategory = categorySlices(data.categoryBreakdown).firstOrNull()
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.hub.review")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Monthly review", style = IosText.headline, color = IosColors.textPrimary)
                    Text(
                        data.dateRange?.startDate?.toString() ?: "This month",
                        style = IosText.subheadline,
                        color = IosColors.textSecondary,
                    )
                }
                ReportScopeButton("chevron.left", "Previous month", "reports.hub.month.previous", onPreviousMonth)
                ReportScopeButton("chevron.right", "Next month", "reports.hub.month.next", onNextMonth)
            }
            Column(
                Modifier.fillMaxWidth().testTag("reports.hub.review.metrics"),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ReviewMetric("Income", Money.format(summary.totalIncome, currency), finance.income, Modifier.weight(1f))
                    ReviewMetric("Spending", Money.format(summary.totalExpenses, currency), finance.expense, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
                Text("Takeaways", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                if (topCategory == null) {
                    ReportTakeawayRow(
                        symbol = "chart.bar.fill",
                        tint = finance.expense,
                        title = "No spending pattern yet",
                        detail = "Log activity to make this takeaway useful.",
                    )
                } else {
                    ReportTakeawayRow(
                        symbol = "chart.bar.fill",
                        tint = finance.expense,
                        title = "${topCategory.name} leads spending",
                        detail = "Review the transaction log to confirm your largest category (${Money.format(topCategory.amount.toDouble(), currency)}).",
                    )
                }
                ReportTakeawayRow(
                    symbol = "arrow.triangle.2.circlepath",
                    tint = IosColors.spendOrange,
                    title = if (activeRecurringCount == 0) {
                        "Recurring obligations are not mapped"
                    } else {
                        "$activeRecurringCount recurring obligation${if (activeRecurringCount == 1) "" else "s"} mapped"
                    },
                    detail = if (activeRecurringCount == 0) {
                        "Add regular bills or income to improve forecasts."
                    } else {
                        "Review your bills and income to keep projected cash flow trustworthy."
                    },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.m))
                    .background(IosColors.spendOrange)
                    .clickable(onClick = onReviewTransactions)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Review recent transactions",
                    modifier = Modifier.weight(1f),
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = Color.White,
                )
                Icon(
                    SfSymbols.icon("arrow.right"),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReportTakeawayRow(symbol: String, tint: Color, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = tint, modifier = Modifier.width(24.dp).size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(detail, style = IosText.caption, color = IosColors.textSecondary)
        }
        Icon(
            SfSymbols.icon("chevron.right"),
            contentDescription = null,
            tint = IosColors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ReviewMetric(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .padding(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = IosText.subheadline, color = IosColors.textSecondary)
        Text(amount, style = IosText.rounded(17, FontWeight.Medium), color = color, maxLines = 1)
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
                    style = IosText.headline,
                    color = IosColors.textPrimary,
                )
                Text("This month", style = IosText.subheadline, color = IosColors.textSecondary)
            }
            if (total.signum() <= 0) {
                Text("No spending logged this month yet.", style = IosText.subheadline, color = IosColors.textSecondary)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                    val barWidth = maxWidth - 2.dp * (slices.size - 1)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        slices.forEachIndexed { index, slice ->
                            val fraction = slice.amount.divide(total, 6, RoundingMode.HALF_UP).toFloat()
                            Box(
                                Modifier.width((barWidth * fraction).coerceAtLeast(2.dp)).fillMaxHeight()
                                    .background(palette[index]),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    slices.forEachIndexed { index, slice ->
                        Row(
                            Modifier.fillMaxWidth().testTag("reports.category.${slice.id}").padding(vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(palette[index]))
                            Text(slice.name, modifier = Modifier.weight(1f), style = IosText.callout, color = IosColors.textPrimary)
                            Text(Money.format(slice.amount.toDouble(), currency), style = IosText.callout.w(FontWeight.Medium), color = IosColors.textPrimary)
                            val percent = slice.amount.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                            Text(
                                "$percent%",
                                modifier = Modifier.widthIn(min = 36.dp),
                                style = IosText.subheadline,
                                color = IosColors.textSecondary,
                                textAlign = TextAlign.End,
                            )
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
    symbol: String,
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
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Radius.m)).background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfSymbols.icon(symbol), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = IosText.headline, color = IosColors.textPrimary)
                Text(detail, style = IosText.subheadline, color = IosColors.neutral, maxLines = 2)
            }
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.neutral, modifier = Modifier.size(18.dp))
        }
    }
}

