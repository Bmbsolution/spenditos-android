package com.spenditos.android.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
internal fun ReportsBackBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reports.back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.testTag("$tag.previous")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, modifier = Modifier.testTag("$tag.next")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
    }
}

@Composable
internal fun ReportsStatusCard(
    status: ReportLoadStatus,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val title = when (status) {
        ReportLoadStatus.OFFLINE -> "You're offline"
        ReportLoadStatus.ERROR -> "Reports unavailable"
        ReportLoadStatus.PRO_REQUIRED -> "Pro feature"
    }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(reportStatusMessage(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == ReportLoadStatus.OFFLINE) finance.warning else MaterialTheme.colorScheme.onSurface,
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
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ReportEmptyCard(title: String, detail: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth().testTag("reports.empty")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DailyExpenseChart(
    rows: List<DailyAggregate>,
    movingAverages: List<BigDecimal>,
    monthlyAverage: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val finance = LocalFinanceColors.current
    val maxExpense = rows.maxOfOrNull { it.expenses }?.takeIf { it > 0.0 } ?: 1.0
    val highest = rows.maxByOrNull { it.expenses }
    val lowest = rows.minByOrNull { it.expenses }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Daily expense bars with a seven day average" }
            .testTag("reports.daily.chart"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    row.date.format(DateTimeFormatter.ofPattern("MMM d")),
                    modifier = Modifier.width(48.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                    val fraction = (row.expenses / maxExpense).toFloat().coerceIn(0f, 1f)
                    val averageFraction = ((movingAverages.getOrNull(index)?.toDouble() ?: 0.0) / maxExpense).toFloat().coerceIn(0f, 1f)
                    val barColor = if (BigDecimal.valueOf(row.expenses) > monthlyAverage) finance.expense else finance.income
                    Box(
                        Modifier.fillMaxWidth(fraction.coerceAtLeast(0.025f)).height(20.dp)
                            .clip(RoundedCornerShape(3.dp)).background(barColor),
                    )
                    Box(
                        Modifier.offset(x = maxWidth * averageFraction - 3.dp).size(6.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant)
                            .align(Alignment.CenterStart),
                    )
                }
                Text(
                    Money.format(row.expenses, currency),
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val badge = when (row) {
                    highest -> "HIGH"
                    lowest -> "LOW"
                    else -> null
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badge == "HIGH") finance.expense else finance.income,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartLegendDot(finance.income, "Below or at avg")
            ChartLegendDot(finance.expense, "Above avg")
            ChartLegendDot(MaterialTheme.colorScheme.onSurfaceVariant, "7-day avg")
        }
    }
}

@Composable
internal fun WeeklyComparisonChart(
    values: List<WeeklyComparison>,
    maxValue: BigDecimal,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
) {
    val finance = LocalFinanceColors.current
    val currentColor = if (isIncome) finance.income else finance.expense
    val previousColor = currentColor.copy(alpha = 0.35f)
    val scale = maxValue.takeIf { it.signum() > 0 }?.toDouble() ?: 1.0
    Canvas(
        modifier.fillMaxWidth().height(148.dp)
            .semantics { contentDescription = "Weekly comparison bars" }
            .testTag("reports.comparison.chart"),
    ) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barWidth = (slot * 0.26f).coerceAtLeast(3.dp.toPx())
        val gap = 2.dp.toPx()
        val chartBottom = size.height - 4.dp.toPx()
        values.forEachIndexed { index, week ->
            val centerX = slot * index + slot / 2f
            val currentHeight = chartBottom * (week.current.toDouble() / scale).toFloat()
            val previousHeight = chartBottom * (week.previous.toDouble() / scale).toFloat()
            drawRoundRect(
                color = previousColor,
                topLeft = Offset(centerX - gap / 2f - barWidth, chartBottom - previousHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, previousHeight.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = currentColor,
                topLeft = Offset(centerX + gap / 2f, chartBottom - currentHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, currentHeight.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceAround) {
        values.forEach { week -> Text("W${week.week}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun ChartLegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
