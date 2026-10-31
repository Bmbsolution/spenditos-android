package com.spenditos.android.feature.reports

import com.spenditos.android.core.i18n.LocalizedDate
import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.semantics.Role
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
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.common_reports), style = IosText.headline, color = IosColors.textPrimary) },
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
                    item { ReportLoadingCard(stringResource(R.string.reports_preparing_your_monthly_review)) }
                } else if (state.loadStatus != null && state.dashboard == null) {
                    val status = requireNotNull(state.loadStatus)
                    item {
                        ReportMonthSelector(
                            title = state.month.format(LocalizedDate.formatter("MMMM yyyy", locale)),
                            subtitle = monthRangeLabel(state.month, locale),
                            tag = "reports.hub.month",
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                        )
                    }
                    item {
                        ReportsStatusCard(
                            status,
                            viewModel::refresh,
                            errorTitle = stringResource(R.string.reports_monthly_review_unavailable),
                            errorMessage = stringResource(R.string.reports_we_could_not_refresh_this),
                        )
                    }
                } else if (state.isEmpty) {
                    // A month with no entries: iOS's "starts with one entry" card, with month navigation kept.
                    item {
                        ReportMonthSelector(
                            title = state.month.format(LocalizedDate.formatter("MMMM yyyy", locale)),
                            subtitle = monthRangeLabel(state.month, locale),
                            tag = "reports.hub.month",
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                        )
                    }
                    item { Box(Modifier.testTag("reports.empty")) { ReportsEmptyReviewCard(onLogTransaction) } }
                } else {
                    val dashboard = requireNotNull(state.dashboard)
                    state.loadStatus?.let { status ->
                        item { ReportInlineStatus(status, viewModel::refresh, "reports.hub.status") }
                    }
                    item {
                        MonthlyReviewCard(
                            dashboard,
                            state.currency,
                            viewModel::previousMonth,
                            viewModel::nextMonth,
                            state.activeRecurringCount,
                            onReviewTransactions,
                        )
                    }
                    if (dashboard.categoryBreakdown.isNotEmpty()) {
                        item { WhereMoneyWentCard(dashboard.categoryBreakdown, state.currency) }
                    }
                }
                item {
                    ReportNavigationCard(
                        title = stringResource(R.string.reports_daily_spending_trends),
                        detail = stringResource(R.string.reports_track_your_daily_spending_patterns),
                        symbol = "chart.bar.fill",
                        iconTint = IosColors.spendOrange,
                        tag = "reports.hub.daily",
                        onClick = onOpenDaily,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = stringResource(R.string.reports_pay_periods),
                        detail = stringResource(R.string.reports_see_how_your_income_covers),
                        symbol = "banknote.fill",
                        iconTint = IosColors.income,
                        tag = "reports.hub.paycheck",
                        onClick = onOpenPaycheckPeriods,
                    )
                }
                item {
                    ReportNavigationCard(
                        title = stringResource(R.string.reports_month_comparison),
                        detail = stringResource(R.string.reports_compare_this_month_s_spending),
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
                stringResource(R.string.reports_insights_and_planning_tools_for),
                style = IosText.title3.w(FontWeight.SemiBold),
                color = IosColors.textPrimary,
            )
            Text(
                stringResource(R.string.reports_track_day_to_day_trends),
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
                Text(stringResource(R.string.reports_your_review_starts_with_one), style = IosText.headline, color = IosColors.textPrimary)
            }
            Text(
                stringResource(R.string.reports_log_a_transaction_to_see),
                style = IosText.callout,
                color = IosColors.textSecondary,
            )
            Text(
                stringResource(R.string.reports_log_a_transaction),
                modifier = Modifier.clickable(role = Role.Button, onClick = onLogTransaction),
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
                    Text(stringResource(R.string.reports_monthly_review), style = IosText.headline, color = IosColors.textPrimary)
                    Text(
                        data.dateRange?.startDate?.toString() ?: stringResource(R.string.common_this_month),
                        style = IosText.subheadline,
                        color = IosColors.textSecondary,
                    )
                }
                ReportScopeButton("chevron.left", stringResource(R.string.reports_previous_month), "reports.hub.month.previous", onPreviousMonth)
                ReportScopeButton("chevron.right", stringResource(R.string.reports_next_month), "reports.hub.month.next", onNextMonth)
            }
            Column(
                Modifier.fillMaxWidth().testTag("reports.hub.review.metrics"),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ReviewMetric(stringResource(R.string.common_income), Money.format(summary.totalIncome, currency), finance.income, Modifier.weight(1f))
                    ReviewMetric(stringResource(R.string.reports_spending), Money.format(summary.totalExpenses, currency), finance.expense, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ReviewMetric(
                        stringResource(R.string.reports_net),
                        Money.format(summary.netAmount, currency),
                        if (summary.netAmount >= 0) finance.income else finance.expense,
                        Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.reports_takeaways), style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                if (topCategory == null) {
                    ReportTakeawayRow(
                        symbol = "chart.bar.fill",
                        tint = finance.expense,
                        title = stringResource(R.string.reports_no_spending_pattern_yet),
                        detail = stringResource(R.string.reports_log_activity_to_make_this),
                    )
                } else {
                    ReportTakeawayRow(
                        symbol = "chart.bar.fill",
                        tint = finance.expense,
                        title = stringResource(R.string.reports_top_category_leads, topCategory.name),
                        detail = stringResource(R.string.reports_review_the_transaction_log_to, Money.format(topCategory.amount.toDouble(), currency)),
                        onClick = onReviewTransactions,
                    )
                }
                ReportTakeawayRow(
                    symbol = "arrow.triangle.2.circlepath",
                    tint = IosColors.spendOrange,
                    title = if (activeRecurringCount == 0) {
                        stringResource(R.string.reports_recurring_obligations_are_not_mapped)
                    } else {
                        Strings.plural(R.plurals.reports_recurring_mapped, activeRecurringCount)
                    },
                    detail = if (activeRecurringCount == 0) {
                        stringResource(R.string.reports_add_regular_bills_or_income)
                    } else {
                        stringResource(R.string.reports_review_your_bills_and_income)
                    },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.m))
                    .background(IosColors.spendOrange)
                    .clickable(role = Role.Button, onClick = onReviewTransactions)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reports_review_recent_transactions),
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
private fun ReportTakeawayRow(symbol: String, tint: Color, title: String, detail: String, onClick: (() -> Unit)? = null) {
    Row(
        // The chevron only appears on rows that go somewhere.
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = tint, modifier = Modifier.width(24.dp).size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(detail, style = IosText.caption, color = IosColors.textSecondary)
        }
        if (onClick != null) {
            Icon(
                SfSymbols.icon("chevron.right"),
                contentDescription = null,
                tint = IosColors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
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
                    stringResource(R.string.reports_where_your_money_went),
                    modifier = Modifier.weight(1f),
                    style = IosText.headline,
                    color = IosColors.textPrimary,
                )
                Text(stringResource(R.string.common_this_month), style = IosText.subheadline, color = IosColors.textSecondary)
            }
            if (total.signum() <= 0) {
                Text(stringResource(R.string.reports_no_spending_logged_this_month), style = IosText.subheadline, color = IosColors.textSecondary)
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

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = LocalizedDate.formatter("MMM d", locale)
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
