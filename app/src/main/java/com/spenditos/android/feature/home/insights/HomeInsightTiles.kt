package com.spenditos.android.feature.home.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.w
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun BentoTile(
    title: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().padding(14.dp).heightIn(min = 156.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SfSymbols.icon(icon), contentDescription = null, tint = tint, modifier = Modifier.size(14.4.dp))
                Text(title, style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary)
            }
            content()
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun NextBillTile(pin: HomeInsights.Pin?, today: LocalDate, currency: String, locale: Locale, modifier: Modifier = Modifier) {
    BentoTile("Next bill", "calendar.badge.clock", IosColors.spendOrange, modifier.testTag("home.nextBill")) {
        if (pin == null) {
            Icon(SfSymbols.icon("checkmark.seal.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(36.dp))
            Text("No more bills this month", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
        } else {
            val days = (pin.date.toEpochDay() - today.toEpochDay()).toInt()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PinBadge(pin, 34.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(pin.name, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(pin.amount.toDouble(), currency, locale), style = IosText.rounded(15, FontWeight.Bold), color = IosColors.textPrimary)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (days <= 1) (if (days == 1) "Tomorrow" else "Today") else "$days",
                    style = IosText.rounded(if (days <= 1) 22 else 30, FontWeight.ExtraBold),
                    color = IosColors.spendOrange,
                )
                if (days > 1) {
                    Text(
                        "days · ${pin.date.format(monthDayFormatter(locale))}",
                        style = IosText.caption.w(FontWeight.Medium),
                        color = IosColors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun WeekTile(week: List<HomeInsights.WeekDay>, tint: Color, currency: String, locale: Locale, modifier: Modifier = Modifier) {
    val total = week.fold(java.math.BigDecimal.ZERO) { sum, day -> sum + day.amount }
    val description = "Everyday spending in the last 7 days: ${Money.format(total.toDouble(), currency, locale)}"
    val maxAmount = week.maxOfOrNull { it.amount.toDouble() }?.coerceAtLeast(1.0) ?: 1.0
    BentoTile("Last 7 days", "chart.bar.fill", tint, modifier.testTag("home.week")) {
        Text(
            Money.format(total.toDouble(), currency, locale),
            style = IosText.rounded(20, FontWeight.Bold),
            color = IosColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().height(66.dp).semantics { contentDescription = description }.testTag("home.week.days"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { day ->
                Column(
                    Modifier.weight(1f).testTag("home.week.day.${day.date}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val fraction = (day.amount.toDouble() / maxAmount).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height((4f + 50f * fraction).dp).clip(RoundedCornerShape(4.dp)).background(
                            if (day.isToday) Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.65f)))
                            else Brush.verticalGradient(listOf(IosColors.textTertiary.copy(alpha = 0.28f), IosColors.textTertiary.copy(alpha = 0.28f))),
                        ),
                    )
                    Text(
                        day.date.format(weekdayFormatter(locale)),
                        style = IosText.system(10, if (day.isToday) FontWeight.Bold else FontWeight.Medium),
                        color = if (day.isToday) tint else IosColors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun TopCategoryTile(top: HomeInsights.TopCategory?, currency: String, locale: Locale, modifier: Modifier = Modifier) {
    BentoTile("Most spent on", "crown.fill", IosColors.spendGold, modifier.testTag("home.topCategory")) {
        if (top == null) {
            Text("Log a few expenses to see where your money goes.", style = IosText.subheadline, color = IosColors.textSecondary)
        } else {
            val color = categoryColor(top.colorHex)
            val sharePercent = top.share.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP)
            Row(
                Modifier.fillMaxWidth().testTag("home.topCategory.summary"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(com.spenditos.android.ui.components.CategoryIcons.symbol(top.symbol, top.name), color, size = 38.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(top.name, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(top.amount.toDouble(), currency, locale), style = IosText.rounded(15, FontWeight.Bold), color = IosColors.textPrimary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
                    Box(
                        Modifier.fillMaxWidth(top.share.toFloat().coerceIn(0f, 1f)).height(7.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
                    )
                }
                Text("$sharePercent% of everyday spending", style = IosText.caption2, color = IosColors.textSecondary)
            }
        }
    }
}

@Composable
internal fun PinBadge(pin: HomeInsights.Pin, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val color = if (pin.isIncome) IosColors.income else categoryColor(pin.colorHex)
    Box(
        Modifier
            .dropShadow(CircleShape, Shadow(radius = 6.dp, color = color, offset = DpOffset(0.dp, 1.dp), alpha = 0.4f))
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = if (pin.isPast) 1f else 0.75f))
            .border(if (size > 18.dp) 2.dp else 1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SfSymbols.icon(if (pin.isIncome) "arrow.down" else pin.symbol),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

private fun monthDayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
private fun weekdayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", locale)
