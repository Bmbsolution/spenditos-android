package com.spenditos.android.feature.home

import com.spenditos.android.core.i18n.LocalizedDate
import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.feature.debts.DebtAddSheetHost
import com.spenditos.android.feature.debts.add.DebtPrefill
import com.spenditos.android.feature.home.insights.MonthRiverCard
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCard
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCopy
import com.spenditos.android.feature.home.insights.NextBillTile
import com.spenditos.android.feature.home.insights.TopCategoryTile
import com.spenditos.android.feature.home.insights.WeekTile
import com.spenditos.android.feature.home.pulse.FinancialWeather
import com.spenditos.android.feature.home.pulse.HealthScore
import com.spenditos.android.feature.home.pulse.HealthScoreSheet
import com.spenditos.android.feature.home.pulse.PulseHero
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HomeScreen(
    onReviewPlan: () -> Unit = {},
    onAddIncome: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onLogFirst: () -> Unit = {},
    /** A ready scan with several rows was tapped: open its review. */
    onOpenScanReview: (String) -> Unit = {},
    /** A ready single-receipt scan was tapped: open the add form with these values. */
    onOpenScanReceipt: (com.spenditos.android.feature.transactions.AddTransactionPrefill) -> Unit = {},
    onScanMessage: (String) -> Unit = {},
    /** Bills left was tapped: the expenses from tomorrow through month end. */
    onOpenBillsLeft: (start: LocalDate, end: LocalDate) -> Unit = { _, _ -> },
    /** Next bill was tapped: open the logged transaction in the edit form. */
    onOpenTransaction: (com.spenditos.android.feature.transactions.EditTarget) -> Unit = {},
    /** A plain-language message for the snackbar (a failed Remove / re-add of the month-start carry-over). */
    onMessage: (String) -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var showMath by rememberSaveable { mutableStateOf(false) }
    var showHealth by rememberSaveable { mutableStateOf(false) }
    // The Add debt sheet opened by the month-start card (not restored after process death: it needs the card).
    var addDebtPrefill by remember { mutableStateOf<DebtPrefill?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }

    val hero = state.status?.let { FinancialWeather.from(it.state) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wash = (hero?.glow ?: IosColors.spendOrange).copy(alpha = 0.26f)
        Box(
            Modifier.fillMaxWidth().height(480.dp).drawWithContent {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(wash, Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = 420f,
                    ),
                )
            },
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().testTag("home.refresh"),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                if (state.isLoading) {
                    GreetingHeader(state.today, state.displayName, locale)
                    Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("home.loading"))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GreetingHeader(state.today, state.displayName, locale)
                        // The month-start card lives on Home only, right above the status card.
                        state.monthBalance?.let { card ->
                            MonthBalanceCard(
                                card = card,
                                currency = state.currency,
                                locale = locale,
                                onAddDebt = { addDebtPrefill = MonthBalanceCopy.shortfallPrefill(card.data) },
                                onAnswer = viewModel::answerMonthBalance,
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
                                onOpenBillsLeft = {
                                    val end = status.monthEnd
                                    onOpenBillsLeft(minOf(state.today.plusDays(1), end), end)
                                },
                                onRemoveCarriedIn = (viewModel::removeCarriedIn).takeIf { state.canRemoveCarriedIn },
                                removeEnabled = !state.monthBalanceSaving,
                            )
                        }
                    }
                    if (state.showingSavedData) {
                        Text(
                            stringResource(R.string.home_showing_saved_data),
                            style = IosText.footnote,
                            color = IosColors.textSecondary,
                            modifier = Modifier.fillMaxWidth().testTag("home.offline"),
                        )
                    }
                    state.refreshError?.let { message ->
                        Text(
                            message,
                            style = IosText.footnote,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().testTag("home.error"),
                        )
                    }

                    attentionBill(state)?.let { bill ->
                        AttentionRow(bill, state.today, state.currency, locale, onReviewPlan)
                    }

                    // Scans left running or finished and waiting for review (iOS: after the plan reminder).
                    com.spenditos.android.feature.scan.ScanHomeCards(onOpenScanReview, onOpenScanReceipt, onScanMessage)

                    if (state.isEmpty) EmptyHomeState(onLogFirst)

                    state.insights?.let { insights ->
                        MonthRiverCard(insights, hero?.gradient?.getOrNull(1) ?: IosColors.spendOrange, state.currency, locale)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NextBillTile(
                                    insights.nextBill, state.today, state.currency, locale, Modifier.weight(1f).fillMaxHeight(),
                                    onOpen = { pin ->
                                        scope.launch {
                                            val target = viewModel.editTargetFor(pin.id, pin.date)
                                            if (target != null) onOpenTransaction(target) else onSeeAll()
                                        }
                                    },
                                )
                                WeekTile(insights.week, hero?.gradient?.getOrNull(1) ?: IosColors.spendOrange, state.currency, locale, Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TopCategoryTile(insights.topCategory, state.currency, locale, Modifier.weight(1f).fillMaxHeight())
                                com.spenditos.android.feature.progress.StreakTile(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }

                    if (state.recent.isNotEmpty()) RecentActivitySection(state.recent, state.currency, locale, onSeeAll)
                }
            }
        }
    }

    addDebtPrefill?.let { prefill ->
        DebtAddSheetHost(
            onDismiss = { addDebtPrefill = null },
            onCreated = { viewModel.onDebtCreated() },
            prefill = prefill,
        )
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
                onRemoveCarriedIn = (viewModel::removeCarriedIn).takeIf { state.canRemoveCarriedIn },
                followUp = state.monthBalanceFollowUp,
                onCarryOver = viewModel::carryOverAgain,
                onAddDebt = {
                    state.monthBalanceFollowUp?.data?.let(MonthBalanceCopy::shortfallPrefill)?.let { addDebtPrefill = it }
                    showMath = false
                },
                saving = state.monthBalanceSaving,
            )
        }
    }
}

private fun attentionBill(state: HomeUiState): CashFlowBreakdown.Line? {
    val today = state.today
    val soon = today.plusDays(3)
    return state.upcomingBills
        .filter { !it.date.isBefore(today) && !it.date.isAfter(soon) }
        .minWithOrNull(compareBy<CashFlowBreakdown.Line> { it.date }.thenByDescending { it.amount }.thenBy { it.name })
}

@Composable
private fun AttentionRow(
    line: CashFlowBreakdown.Line,
    today: LocalDate,
    currency: String,
    locale: Locale,
    onOpen: () -> Unit,
) {
    val days = ChronoUnit.DAYS.between(today, line.date).toInt()
    val whenText = when (days) {
        0 -> stringResource(R.string.home_due_today)
        1 -> stringResource(R.string.home_due_tomorrow)
        else -> Strings.plural(R.plurals.home_due_in_days, days)
    }
    GlassCard(onClick = onOpen, modifier = Modifier.testTag("home.billRow.${line.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            IconChip(SfSymbols.icon("calendar.badge.clock"), IosColors.spendOrange, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_bill_due, line.name, whenText), style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                Text(Money.format(line.amount.toDouble(), currency, locale), style = IosText.subheadline, color = IosColors.textSecondary)
            }
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.4.dp))
        }
    }
}

@Composable
private fun EmptyHomeState(onLogFirst: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s).testTag("home.empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            SfSymbols.icon("chart.line.uptrend"),
            contentDescription = null,
            tint = IosColors.spendOrange.copy(alpha = 0.75f),
            modifier = Modifier.size(67.dp),
        )
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(R.string.home_build_your_financial_picture),
                style = IosText.title2.w(FontWeight.Bold),
                color = IosColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.home_add_your_first_transaction_to),
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(IosColors.spendOrange)
                .clickable(role = Role.Button, onClick = onLogFirst)
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .testTag("home.empty.log"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
            Text(stringResource(R.string.common_log_first_transaction), style = IosText.headline, color = Color.White)
        }
    }
}

@Composable
private fun GreetingHeader(today: LocalDate, displayName: String, locale: Locale) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> stringResource(R.string.home_good_morning)
        hour < 18 -> stringResource(R.string.home_good_afternoon)
        else -> stringResource(R.string.home_good_evening)
    }
    val name = displayName.trim()
    val (symbol, colors) = when (hour) {
        in 5..11 -> "sun.horizon.fill" to listOf(Color(0xFFFFB347), Color(0xFFFF7A45))
        in 12..16 -> "sun.max.fill" to listOf(Color(0xFFFFD24D), Color(0xFFFF9F1C))
        else -> "moon.stars.fill" to listOf(Color(0xFF8E9BFF), Color(0xFF5B5FD6))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                SfSymbols.icon(symbol),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size((22 * 1.2 * 0.75).dp)
                    // SrcIn needs its own layer, or the gradient fills the whole box.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                    drawContent()
                    drawRect(Brush.linearGradient(colors), blendMode = BlendMode.SrcIn)
                },
            )
            Text(
                today.format(LocalizedDate.formatter("EEEE, MMMM d", locale)).uppercase(locale),
                style = IosText.caption.w(FontWeight.SemiBold),
                color = IosColors.textSecondary,
                letterSpacing = 0.8.sp,
            )
        }
        // One line when "Good morning, Sam Lee" fits; otherwise the greeting becomes a label and the
        // full name takes the line below (ellipsis only if the name alone is still too wide).
        val greetingStyle = IosText.rounded(30, FontWeight.Bold)
        if (name.isEmpty()) {
            Text(greeting, style = greetingStyle, color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("home.greeting"))
        } else {
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fitsOneLine = remember(greeting, name, constraints.maxWidth, greetingStyle) {
                    !measurer.measure(
                        "$greeting, $name",
                        style = greetingStyle,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = constraints.maxWidth),
                    ).didOverflowWidth
                }
                if (fitsOneLine) {
                    Text("$greeting, $name", style = greetingStyle, color = IosColors.textPrimary, maxLines = 1, modifier = Modifier.testTag("home.greeting"))
                } else {
                    Column(Modifier.testTag("home.greeting")) {
                        Text("$greeting,", style = IosText.rounded(17, FontWeight.SemiBold), color = IosColors.textSecondary, maxLines = 1)
                        Text(name, style = greetingStyle, color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivitySection(rows: List<RecentActivity>, currency: String, locale: Locale, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.testTag("home.recent")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(SfSymbols.icon("clock.arrow.circlepath"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(20.4.dp))
                Text(stringResource(R.string.home_recent_activity), style = IosText.headline, color = IosColors.textPrimary)
            }
            Text(
                stringResource(R.string.home_see_all),
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = IosColors.spendOrange,
                modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(vertical = 8.dp).testTag("home.recent.seeAll"),
            )
        }
        GlassCard {
            Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                rows.forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("home.recent.row.${row.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        com.spenditos.android.ui.components.CategoryIconChip(
                            row.icon,
                            row.category,
                            com.spenditos.android.ui.components.categoryColor(row.colorHex),
                            size = 42.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.title, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${row.category} · ${relativeDay(row.date)}",
                                style = IosText.subheadline,
                                color = IosColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            (if (row.isIncome) "+" else "−") + Money.format(row.amount, currency, locale),
                            style = IosText.rounded(16, FontWeight.SemiBold),
                            color = if (row.isIncome) IosColors.income else IosColors.textPrimary,
                        )
                    }
                    if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp), color = IosColors.separator)
                }
            }
        }
    }
}

private fun relativeDay(date: LocalDate): String = when {
    date == LocalDate.now() -> Strings.get(R.string.common_today)
    date == LocalDate.now().minusDays(1) -> Strings.get(R.string.common_yesterday)
    else -> date.format(LocalizedDate.formatter("MMM d"))
}
