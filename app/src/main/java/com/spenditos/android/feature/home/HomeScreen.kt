package com.spenditos.android.feature.home

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import com.spenditos.android.feature.home.pulse.FinancialWeather
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
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
import com.spenditos.android.feature.home.pulse.HealthScore
import com.spenditos.android.feature.home.pulse.HealthScoreSheet
import com.spenditos.android.feature.home.pulse.PulseHero
import com.spenditos.android.ui.components.Money
import com.spenditos.android.feature.home.insights.MonthRiverCard
import com.spenditos.android.feature.home.insights.NextBillTile
import com.spenditos.android.feature.home.insights.TopCategoryTile
import com.spenditos.android.feature.home.insights.WeekTile
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HomeScreen(onReviewPlan: () -> Unit = {}, onAddIncome: () -> Unit = {}, onSeeAll: () -> Unit = {}) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var showMath by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }

    val hero = state.status?.let { FinancialWeather.from(it.state) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Ambient wash: the weather glow fades in from the top, like the iOS Home.
        Box(
            Modifier.fillMaxWidth().height(420.dp).background(
                Brush.verticalGradient(listOf((hero?.glow ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.22f), Color.Transparent)),
            ),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().testTag("home.refresh"),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                GreetingHeader(state.today, locale)
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
                        PulseHero(
                            status = status,
                            history = state.history,
                            currency = state.currency,
                            locale = locale,
                            onSeeMath = { showMath = true },
                            onOpenHealth = { showHealth = true },
                            onReviewPlan = onReviewPlan,
                            onAddIncome = onAddIncome,
                        )
                    }

                    if (state.isEmpty) EmptyHomeState()

                    state.insights?.let { insights ->
                        MonthRiverCard(insights, state.currency, locale)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NextBillTile(insights.nextBill, state.today, state.currency, locale, Modifier.weight(1f).fillMaxHeight())
                            WeekTile(insights.week, state.currency, locale, Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            insights.topCategory?.let { TopCategoryTile(it, state.currency, locale, Modifier.weight(1f).fillMaxHeight()) }
                            com.spenditos.android.feature.progress.StreakTile(Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    if (state.recent.isNotEmpty()) RecentActivitySection(state.recent, state.currency, locale, onSeeAll)
                }
            }
        }
    }

    if (showHealth) {
        HealthScoreSheet(
            breakdown = HealthScore.breakdown(state.status, state.history.map { it.expenses }),
            onDismiss = { showHealth = false },
        )
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

/** "THURSDAY, OCTOBER 1" over a large "Good morning" (iOS Home header). */
@Composable
private fun GreetingHeader(today: LocalDate, locale: Locale) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Text(greeting, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.testTag("home.greeting"))
    }
}

/** iOS "Recent activity": a header with "See all", then the last few transactions in one grouped card. */
@Composable
private fun RecentActivitySection(rows: List<RecentActivity>, currency: String, locale: Locale, onSeeAll: () -> Unit) {
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.testTag("home.recent")) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "See all",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSeeAll).padding(vertical = 8.dp).testTag("home.recent.seeAll"),
            )
        }
        com.spenditos.android.ui.components.GlassCard {
            rows.forEachIndexed { index, row ->
                val color = com.spenditos.android.ui.components.categoryColor(row.colorHex)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp).testTag("home.recent.row.${row.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Text(row.category.take(1).uppercase(locale), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${row.category} · ${row.date.format(DateTimeFormatter.ofPattern("MMM d", locale))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        Money.signed(row.amount, row.isIncome, currency, locale),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (row.isIncome) finance.income else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (index < rows.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 78.dp, end = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
