package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
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
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("reports.paycheck.list"),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
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
                if (state.isLoading) {
                    item { ReportLoadingCard("Loading paycheck periods…") }
                } else if (state.loadStatus != null && state.periods.isEmpty()) {
                    item {
                        val status = requireNotNull(state.loadStatus)
                        ReportsStatusCard(status, if (status == ReportLoadStatus.PRO_REQUIRED) null else viewModel::refresh)
                    }
                } else {
                    state.loadStatus?.let { status -> item { ReportInlineStatus(status, viewModel::refresh, "reports.paycheck.status") } }
                    if (state.isEmpty && state.loadStatus == null) {
                        item { ReportEmptyCard("No periods this month", "No paycheck periods overlap ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}.") }
                    } else {
                        state.summary?.let { summary -> item { PaycheckSummaryCard(summary, state.currency) } }
                        state.periods.forEach { period ->
                            item(key = period.id) { PaycheckPeriodCard(period, viewModel, state.currency, locale) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaycheckSummaryCard(summary: PaycheckPeriodsSummary, currency: String) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.paycheck.summary")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Period summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.weight(1f)) {
                    Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(summary.totalIncome.toDouble(), currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(summary.totalExpenses.toDouble(), currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Avg coverage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${summary.averageCoverage?.setScale(0, RoundingMode.HALF_UP) ?: BigDecimal.ZERO}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        isExact -> MaterialTheme.colorScheme.primary
        isCovered -> finance.income
        else -> finance.expense
    }
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("reports.paycheck.period.${period.id}")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(Modifier.testTag("reports.paycheck.period.heading.${period.id}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(displayPeriodLabel(period, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatPeriodDate(period.startDate, locale)} – ${formatPeriodDate(period.endDate, locale)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = statusText,
                    modifier = Modifier.clip(CircleShape).background(statusColor.copy(alpha = 0.14f)).padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(finance.income.copy(alpha = 0.22f))) {
                Box(
                    Modifier.fillMaxWidth(viewModel.expenseRatio(period)).height(8.dp)
                        .clip(CircleShape).background(if (isCovered) finance.income else finance.expense),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("reports.paycheck.period.amounts.${period.id}")) {
                PeriodAmount("Income", viewModel.incomeAmount(period), currency, finance.income, Alignment.Start)
                PeriodAmount("Expenses", viewModel.expenseAmount(period), currency, finance.expense, Alignment.CenterHorizontally)
                PeriodAmount("Remaining", remaining, currency, if (remaining.signum() >= 0) finance.income else finance.expense, Alignment.End)
            }
        }
    }
}

@Composable
private fun PeriodAmount(label: String, amount: BigDecimal, currency: String, color: androidx.compose.ui.graphics.Color, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(signedPeriodMoney(amount, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun signedPeriodMoney(value: BigDecimal, currency: String): String {
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
    return "${month.format(formatter)} – ${month.withDayOfMonth(month.lengthOfMonth()).format(formatter)}"
}
