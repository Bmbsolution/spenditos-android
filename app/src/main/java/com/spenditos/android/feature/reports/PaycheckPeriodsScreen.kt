package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.spenditos.android.data.model.PaycheckPeriod
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PaycheckPeriodsScreen(
    onBack: () -> Unit,
) {
    val viewModel: PaycheckPeriodsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ReportsBackBar("Paycheck Periods", onBack) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> ReportLoadingState(
                    "Loading paycheck periods...",
                    IosColors.spendOrange,
                    Modifier.fillMaxSize().testTag("reports.paycheck.list"),
                )
                // Empty month: keep the month selector so the user can step to another month.
                state.isEmpty && state.loadStatus == null -> Column(
                    Modifier.fillMaxSize().testTag("reports.paycheck.list")
                        .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m),
                ) {
                    ReportMonthSelector(
                        title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        subtitle = monthRangeLabel(state.month, locale),
                        tag = "reports.paycheck.month",
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                    ReportEmptyState(
                        icon = "banknote.fill",
                        title = "No Paycheck Periods",
                        subtitle = "No paycheck periods overlap ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}.",
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("reports.empty"),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("reports.paycheck.list"),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    item {
                        ReportMonthSelector(
                            title = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                            subtitle = monthRangeLabel(state.month, locale),
                            tag = "reports.paycheck.month",
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                        )
                    }
                    if (state.loadStatus != null && state.periods.isEmpty()) {
                        item {
                            val status = requireNotNull(state.loadStatus)
                            ReportsStatusCard(status, if (status == ReportLoadStatus.PRO_REQUIRED) null else viewModel::refresh)
                        }
                    } else {
                        state.loadStatus?.let { status ->
                            item { ReportInlineStatus(status, viewModel::refresh, "reports.paycheck.status") }
                        }
                        if (state.isEmpty && state.loadStatus == null) {
                            item {
                                ReportEmptyCard(
                                    "No Paycheck Periods",
                                    "No paycheck periods overlap ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}.",
                                )
                            }
                        } else {
                            state.summary?.let { summary -> item { PaycheckSummaryCard(summary, state.currency) } }
                            state.periods.forEach { period ->
                                item(key = period.id) { PaycheckPeriodCard(period, viewModel, state.currency, locale) }
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
private fun PaycheckSummaryCard(summary: PaycheckPeriodsSummary, currency: String) {
    val finance = LocalFinanceColors.current
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.paycheck.summary")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Period summary", style = IosText.headline, color = IosColors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.weight(1f)) {
                    Text("Income", style = IosText.subheadline, color = IosColors.neutral)
                    Text(Money.format(summary.totalIncome.toDouble(), currency), style = IosText.rounded(17, FontWeight.Medium), color = finance.income)
                }
                Column(Modifier.weight(1f)) {
                    Text("Expenses", style = IosText.subheadline, color = IosColors.neutral)
                    Text(Money.format(summary.totalExpenses.toDouble(), currency), style = IosText.rounded(17, FontWeight.Medium), color = finance.expense)
                }
                Column(Modifier.weight(1f)) {
                    Text("Avg coverage", style = IosText.subheadline, color = IosColors.neutral)
                    Text(
                        "${summary.averageCoverage?.setScale(0, RoundingMode.HALF_UP) ?: BigDecimal.ZERO}%",
                        style = IosText.rounded(17, FontWeight.Medium),
                        color = IosColors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaycheckPeriodCard(period: PaycheckPeriod, viewModel: PaycheckPeriodsViewModel, currency: String, locale: Locale) {
    val finance = LocalFinanceColors.current
    val remaining = viewModel.remainingAmount(period)
    val isExact = remaining.abs() < BigDecimal("0.01")
    val isCovered = viewModel.isCovered(period)
    val statusText = when {
        isExact -> "Exact"
        isCovered -> "Covered"
        else -> "Gap"
    }
    val statusColor = when {
        isExact -> IosColors.spendOrange
        isCovered -> finance.income
        else -> finance.expense
    }
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.paycheck.period.${period.id}")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                Modifier.testTag("reports.paycheck.period.heading.${period.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(displayPeriodLabel(period, locale), style = IosText.headline, color = IosColors.textPrimary)
                    Text(
                        "${formatPeriodDate(period.startDate, locale)} - ${formatPeriodDate(period.endDate, locale)}",
                        style = IosText.subheadline,
                        color = IosColors.neutral,
                    )
                }
                Text(
                    text = statusText,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.s)).background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = IosText.caption.w(FontWeight.SemiBold),
                    color = statusColor,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(finance.income.copy(alpha = 0.3f)),
                )
                Box(
                    Modifier.width((maxWidth * viewModel.expenseRatio(period)).coerceAtLeast(4.dp)).height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isCovered) IosColors.spendOrange else finance.expense),
                )
            }
            Row(
                Modifier.fillMaxWidth().testTag("reports.paycheck.period.amounts.${period.id}"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.Top,
            ) {
                PeriodAmount("Income", viewModel.incomeAmount(period), currency, finance.income, Alignment.Start, Modifier.weight(1f))
                PeriodAmount("Expenses", viewModel.expenseAmount(period), currency, finance.expense, Alignment.CenterHorizontally, Modifier.weight(1f))
                PeriodAmount("Remaining", viewModel.remainingAmount(period), currency, if (remaining.signum() >= 0) finance.income else finance.expense, Alignment.End, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PeriodAmount(
    label: String,
    amount: BigDecimal,
    currency: String,
    color: androidx.compose.ui.graphics.Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = IosText.subheadline, color = IosColors.neutral)
        Text(formatPeriodMoney(amount, currency), style = IosText.rounded(17, FontWeight.Medium), color = color)
    }
}

private fun formatPeriodMoney(value: BigDecimal, currency: String): String {
    val formatted = Money.format(value.abs().toDouble(), currency)
    return if (value.signum() < 0) "−$formatted" else formatted
}

private fun displayPeriodLabel(period: PaycheckPeriod, locale: Locale): String {
    val prefix = period.label.take(10)
    val parsed = runCatching { LocalDate.parse(prefix) }.getOrNull() ?: return period.label
    return "Paycheck ${formatPeriodDate(parsed, locale)}"
}

private fun formatPeriodDate(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("MMM d", locale))

private fun monthRangeLabel(month: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${month.format(formatter)} - ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
