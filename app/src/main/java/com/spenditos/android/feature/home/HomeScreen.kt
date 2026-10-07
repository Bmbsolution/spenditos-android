package com.spenditos.android.feature.home

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HomeScreen() {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var showMath by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().testTag("home.refresh"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("home.loading"))
                }
            } else {
                if (state.showingSavedData) {
                    Text(
                        "Showing saved data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("home.offline"),
                    )
                }
                state.refreshError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().testTag("home.error"),
                    )
                }

                state.status?.let { status ->
                    CashFlowCard(
                        status = status,
                        currency = state.currency,
                        locale = locale,
                        onSeeMath = { showMath = true },
                    )
                }

                com.spenditos.android.feature.progress.StreakChip()

                if (state.isEmpty) EmptyHomeState()

                Text("Coming up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (state.upcomingBills.isEmpty()) {
                    Text(
                        "No upcoming bills",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("home.comingUp.empty"),
                    )
                } else {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.comingUp")) {
                        Column {
                            state.upcomingBills.forEachIndexed { index, line ->
                                if (index > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = Spacing.m))
                                UpcomingBillRow(line, state.today, state.currency, locale)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMath) {
        val status = state.status
        val breakdown = state.breakdown
        if (status != null && breakdown != null) {
            CashFlowMathSheet(
                status = status,
                breakdown = breakdown,
                currency = state.currency,
                locale = locale,
                onDismiss = { showMath = false },
            )
        }
    }
}

@Composable
private fun CashFlowCard(status: CashFlowStatus, currency: String, locale: Locale, onSeeMath: () -> Unit) {
    val finance = LocalFinanceColors.current
    val percent = status.incomeLeftShare.multiply(java.math.BigDecimal(100))
        .setScale(0, RoundingMode.HALF_UP).toInt()
    val amount = Money.format(status.leftAfterBills.toDouble(), currency, locale)
    val daily = Money.format(status.affordablePace.setScale(2, RoundingMode.HALF_UP).toDouble(), currency, locale)
    val income = Money.format(status.income.toDouble(), currency, locale)
    val stateLabel = when (status.state) {
        CashFlowStatus.State.ON_TRACK -> "On track"
        CashFlowStatus.State.WATCH -> "Watch your pace"
        CashFlowStatus.State.TIGHT -> "Getting tight"
        CashFlowStatus.State.OVER -> "Over"
        CashFlowStatus.State.SETUP -> "Income needed"
    }
    val stateCopy = when (status.state) {
        CashFlowStatus.State.ON_TRACK -> "Your daily pace fits what remains this month."
        CashFlowStatus.State.WATCH -> "Your daily pace is close to what remains each day."
        CashFlowStatus.State.TIGHT -> "Your daily pace is above what remains each day."
        CashFlowStatus.State.OVER -> "Spending and bills are above this month’s income."
        CashFlowStatus.State.SETUP -> "Add income to see how this month is going."
    }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.statusCard")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.status.header")) {
                Text(stateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${status.daysRemaining} days left",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stateCopy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (status.leftAfterBills < java.math.BigDecimal.ZERO) "Short after bills" else "Left after bills",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(amount, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("About $daily a day available", style = MaterialTheme.typography.bodyMedium)
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(status.incomeLeftShare.toFloat().coerceIn(0f, 1f))
                        .background(finance.income),
                )
            }
            Text(
                "$percent% of $income income remains",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSeeMath, modifier = Modifier.align(Alignment.End).testTag("home.seeMath")) {
                Text("See the math")
            }
        }
    }
}

@Composable
private fun UpcomingBillRow(
    line: CashFlowBreakdown.Line,
    today: LocalDate,
    currency: String,
    locale: Locale,
) {
    val relativeDate = when (val days = ChronoUnit.DAYS.between(today, line.date).toInt()) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> "In $days days"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 14.dp)
            .testTag("home.billRow.${line.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$relativeDate · ${line.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            Money.signed(line.amount.toDouble(), isIncome = false, currencyCode = currency, locale = locale),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalFinanceColors.current.expense,
        )
    }
}

@Composable
private fun EmptyHomeState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s).testTag("home.empty"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("No transactions this month", style = MaterialTheme.typography.titleMedium)
        Text("Your cash flow will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
