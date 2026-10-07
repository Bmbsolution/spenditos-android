package com.spenditos.android.feature.home.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Home hero (iOS `FinancialPulseView` hero card): the month's mood, what is left after bills, the
 * spending orb, a daily pace chip, a stats strip and, once expanded, the health score and a 7-day sparkline.
 * Every existing Home test tag is kept (`home.statusCard`, `home.status.header`, `home.seeMath`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PulseHero(
    status: CashFlowStatus,
    history: List<DayFlow>,
    currency: String,
    locale: Locale,
    onSeeMath: () -> Unit,
    onOpenHealth: () -> Unit,
    onReviewPlan: () -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather = FinancialWeather.from(status.state)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val money = { value: BigDecimal -> Money.format(value.toDouble(), currency, locale) }
    val shape = RoundedCornerShape(28.dp)
    val score = HealthScore.score(status)
    val short = status.leftAfterBills.signum() < 0

    Column(
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(weather.gradient))
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(900f, 250f), radius = 520f))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded }
            .padding(Spacing.m + 2.dp)
            .testTag("home.statusCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().testTag("home.status.header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(weather.glyph, color = Color.White)
                Text(weather.shortTitle, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                PulseCopy.days(status.daysRemaining) + " left",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (status.state != CashFlowStatus.State.SETUP) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (short) "SHORT AFTER BILLS" else "LEFT AFTER BILLS",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                    val headline = money(status.leftAfterBills.abs())
                    Text(
                        if (short) "−$headline" else headline,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    )
                    val chip = if (short) {
                        "Trim ≈ ${money(status.leftAfterBills.negate().divide(BigDecimal(maxOf(status.daysRemaining, 1)), 2, RoundingMode.HALF_UP))} a day"
                    } else {
                        "About ${money(status.affordablePace.setScale(2, RoundingMode.HALF_UP))} a day available"
                    }
                    Text(
                        chip,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                LiquidOrb(
                    level = status.incomeLeftShare.toFloat(),
                    tint = weather.glow,
                    label = PulseCopy.percentLabel(status.incomeLeftShare),
                    caption = "of income left",
                )
            }
        }

        Text(
            PulseCopy.reason(status, locale, money),
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Medium,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )

        StatsStrip(
            listOf(
                "Income" to whole(status.income, currency, locale),
                "Spent" to whole(status.spent, currency, locale),
                "Bills left" to whole(status.billsLeft, currency, locale),
            ),
        )

        TextButton(onClick = onSeeMath, modifier = Modifier.fillMaxWidth().testTag("home.seeMath")) {
            Text("See the math", color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
        }

        when (status.state) {
            CashFlowStatus.State.OVER -> HeroButton("Review your plan", onReviewPlan, "home.reviewPlan")
            CashFlowStatus.State.SETUP -> HeroButton("Add income", onAddIncome, "home.addIncome")
            else -> Unit
        }

        if (expanded) {
            if (score != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClick = onOpenHealth).padding(12.dp).testTag("home.healthBadge"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Health score", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("$score  ›", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (status.state != CashFlowStatus.State.SETUP) {
                Text(
                    "The orb is the share of this month’s income still left after spending and bills.",
                    color = Color.White.copy(alpha = 0.9f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            if (history.any { it.income.signum() != 0 || it.expenses.signum() != 0 }) {
                CashFlowSparkline(history, currency, locale)
            }
        }
    }
}

@Composable
private fun StatsStrip(items: List<Pair<String, String>>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (title, value) ->
            if (index > 0) Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.2f)))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.85f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, onClick: () -> Unit, tag: String) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.92f), contentColor = Color(0xFF2A2420)),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

/** Last seven days of net cash flow (iOS `CashFlowSparkline`). */
@Composable
private fun CashFlowSparkline(history: List<DayFlow>, currency: String, locale: Locale) {
    val points = CashFlowHistory.sparkline(history.map { it.net })
    val avg = history.fold(BigDecimal.ZERO) { sum, day -> sum + day.expenses }
        .divide(BigDecimal(maxOf(history.size, 1)), 2, RoundingMode.HALF_UP)
    Column(Modifier.fillMaxWidth().testTag("home.sparkline"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("7-day cash flow", color = Color.White.copy(alpha = 0.8f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text("${Money.format(avg.toDouble(), currency, locale)} avg/day", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(40.dp).clearAndSetSemantics { contentDescription = "Net cash flow for the last 7 days" }) {
            if (points.isEmpty()) return@Canvas
            val xy = points.map { Offset(it.first * size.width, it.second * size.height) }
            val fill = Path().apply {
                moveTo(xy.first().x, xy.first().y)
                xy.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.02f))))
            val line = Path().apply { moveTo(xy.first().x, xy.first().y); xy.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(line, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            xy.forEachIndexed { index, p -> drawCircle(Color.White, radius = if (index == xy.lastIndex) 2.5.dp.toPx() else 1.5.dp.toPx(), center = p) }
        }
    }
}

private fun whole(value: BigDecimal, currency: String, locale: Locale): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { format.currency = Currency.getInstance(currency) }
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    return format.format(value)
}
