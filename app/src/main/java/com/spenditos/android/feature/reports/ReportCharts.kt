package com.spenditos.android.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ReportsBackBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        title = { Text(title, style = IosText.headline, color = IosColors.textPrimary) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reports.back")) {
                Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
            }
        },
    )
}

@Composable
internal fun ReportMonthSelector(
    title: String,
    subtitle: String,
    tag: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ReportScopeButton("chevron.left", "Previous month", "$tag.previous", onPrevious)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = IosText.system(16, FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1)
                }
            }
            ReportScopeButton("chevron.right", "Next month", "$tag.next", onNext)
        }
    }
}

@Composable
internal fun ReportScopeButton(symbol: String, description: String, tag: String, onClick: () -> Unit) {
    Box(
        // 32dp visual like iOS; the 48dp touch target overflows it without changing the layout.
        Modifier.size(32.dp).wrapContentSize(Alignment.Center, unbounded = true).requiredSize(48.dp)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SfSymbols.icon(symbol), contentDescription = description, tint = IosColors.textSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun ReportsStatusCard(
    status: ReportLoadStatus,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    errorTitle: String = "Reports unavailable",
    errorMessage: String? = null,
) {
    val title = when (status) {
        ReportLoadStatus.OFFLINE -> "You're offline"
        ReportLoadStatus.ERROR -> errorTitle
        ReportLoadStatus.PRO_REQUIRED -> "Pro feature"
    }
    val message = if (status == ReportLoadStatus.ERROR && errorMessage != null) {
        errorMessage
    } else {
        reportStatusMessage(status)
    }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                if (status == ReportLoadStatus.PRO_REQUIRED) {
                    Icon(SfSymbols.icon("lock.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(20.dp))
                }
                Text(title, style = IosText.headline, color = IosColors.textPrimary)
            }
            Text(message, style = IosText.callout, color = IosColors.textSecondary)
            if (status == ReportLoadStatus.PRO_REQUIRED) {
                com.spenditos.android.feature.pro.LocalOpenPro.current?.let { openPro ->
                    PillButton("See Spenditos Pro", openPro, modifier = Modifier.fillMaxWidth().testTag("reports.seePro"), filled = false)
                }
            }
            if (onRetry != null && status != ReportLoadStatus.PRO_REQUIRED) {
                PillButton("Try again", onRetry, modifier = Modifier.fillMaxWidth().testTag("reports.retry"))
            }
        }
    }
}

@Composable
internal fun ReportInlineStatus(status: ReportLoadStatus, onRetry: () -> Unit, tag: String) {
    val finance = LocalFinanceColors.current
    GlassCard(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier.padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                reportStatusMessage(status),
                modifier = Modifier.weight(1f),
                style = IosText.body,
                color = if (status == ReportLoadStatus.OFFLINE) finance.warning else IosColors.textPrimary,
            )
            if (status != ReportLoadStatus.PRO_REQUIRED) {
                PillButton("Retry", onRetry, modifier = Modifier.testTag("reports.retry"), filled = false)
            }
        }
    }
}

@Composable
internal fun ReportLoadingCard(text: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(20.dp),
                color = IosColors.spendOrange,
                strokeWidth = 2.dp,
            )
            Text(text, style = IosText.body, color = IosColors.textSecondary)
        }
    }
}

@Composable
internal fun ReportLoadingState(text: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            Modifier.size(20.dp).scale(1.2f),
            color = tint,
            strokeWidth = 2.dp,
        )
        Text(text, modifier = Modifier.padding(top = Spacing.m), style = IosText.callout, color = IosColors.textTertiary)
    }
}

@Composable
internal fun ReportEmptyState(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            SfSymbols.icon(icon),
            contentDescription = null,
            tint = IosColors.spendOrange.copy(alpha = 0.6f),
            modifier = Modifier.size(58.dp),
        )
        Column(
            Modifier.padding(top = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text(subtitle, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

@Composable
internal fun ReportEmptyCard(title: String, detail: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth().testTag("reports.empty")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary)
            Text(detail, style = IosText.callout, color = IosColors.textSecondary)
        }
    }
}

@Composable
internal fun DailyExpenseChart(
    rows: List<DailyAggregate>,
    movingAverages: List<BigDecimal>,
    monthlyAverage: BigDecimal,
    currency: String,
    locale: Locale = Locale.getDefault(),
    modifier: Modifier = Modifier,
) {
    val finance = LocalFinanceColors.current
    val maxExpense = rows.maxOfOrNull { it.expenses }?.takeIf { it > 0.0 } ?: 1.0
    val highest = rows.maxByOrNull { it.expenses }
    val lowest = rows.minByOrNull { it.expenses }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Daily expense bars with a seven day average" }
            .testTag("reports.daily.chart"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    row.date.format(DateTimeFormatter.ofPattern("MMM d", locale)),
                    modifier = Modifier.width(48.dp),
                    style = IosText.subheadline,
                    color = IosColors.neutral,
                    maxLines = 1,
                )
                BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                    val fraction = if (maxExpense > 0) (row.expenses / maxExpense).toFloat() else 0f
                    val averageFraction = if (maxExpense > 0) {
                        ((movingAverages.getOrNull(index)?.toDouble() ?: 0.0) / maxExpense).toFloat()
                    } else 0f
                    val barColor = if (monthlyAverage.signum() > 0 && BigDecimal.valueOf(row.expenses) > monthlyAverage) {
                        finance.expense
                    } else {
                        finance.income
                    }
                    Box(
                        Modifier.width((maxWidth * fraction).coerceAtLeast(2.dp)).height(20.dp)
                            .clip(RoundedCornerShape(3.dp)).background(barColor),
                    )
                    Box(
                        Modifier.offset(x = maxWidth * averageFraction - 3.dp).size(6.dp)
                            .clip(CircleShape).background(IosColors.textSecondary)
                            .alpha(if (averageFraction > 0f) 1f else 0f)
                            .align(Alignment.CenterStart),
                    )
                }
                Text(
                    Money.format(row.expenses, currency),
                    modifier = Modifier.widthIn(min = 70.dp),
                    style = IosText.subheadline,
                    color = IosColors.textSecondary,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    maxLines = 1,
                )
                val badge = when (row) {
                    highest -> "HIGH"
                    lowest -> "LOW"
                    else -> null
                }
                if (badge != null) {
                    ChartBadge(badge, if (badge == "HIGH") finance.expense else finance.income)
                }
            }
        }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartLegendDot(finance.income, "Below or at avg")
            ChartLegendDot(finance.expense, "Above avg")
            ChartLegendDot(IosColors.textSecondary, "7-day avg", dotSize = 6.dp)
        }
    }
}

@Composable
private fun ChartBadge(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = IosText.system(9, FontWeight.Bold),
        color = color,
    )
}

@Composable
internal fun WeeklyComparisonChart(
    values: List<WeeklyComparison>,
    maxValue: BigDecimal,
    isIncome: Boolean,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val finance = LocalFinanceColors.current
    val currentColor = if (isIncome) finance.income else finance.expense
    val previousColor = currentColor.copy(alpha = 0.35f)
    val scale = maxValue.takeIf { it.signum() > 0 }?.toDouble() ?: 1.0
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Weekly comparison bars" }
            .testTag("reports.comparison.chart"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        values.forEach { week ->
            Row(
                Modifier.fillMaxWidth().height(32.dp).testTag("reports.comparison.week.${week.week}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text("W${week.week}", modifier = Modifier.width(36.dp), style = IosText.subheadline, color = IosColors.neutral)
                BoxWithConstraints(Modifier.weight(1f).height(16.dp)) {
                    val previousWidth = (maxWidth * (week.previous.toDouble() / scale).toFloat()).coerceAtLeast(2.dp)
                    val currentWidth = (maxWidth * (week.current.toDouble() / scale).toFloat()).coerceAtLeast(2.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(previousWidth).height(16.dp).clip(RoundedCornerShape(3.dp)).background(previousColor))
                        Box(Modifier.width(currentWidth).height(16.dp).clip(RoundedCornerShape(3.dp)).background(currentColor))
                    }
                }
                Column(
                    Modifier.widthIn(min = 70.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(Money.format(week.current.toDouble(), currency), style = IosText.subheadline, color = IosColors.textPrimary, maxLines = 1)
                    Text(Money.format(week.previous.toDouble(), currency), style = IosText.caption, color = IosColors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun ChartLegendDot(color: Color, label: String, dotSize: Dp = 8.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(dotSize).clip(CircleShape).background(color))
        Text(label, style = IosText.subheadline, color = IosColors.neutral)
    }
}
