package com.spenditos.android.feature.home.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val OutColor = Color(0xFFD9643A)

@Composable
fun MonthRiverCard(insights: HomeInsights, tint: Color, currency: String, locale: Locale) {
    val inColor = IosColors.income
    val signedGap = signedAmount(insights.monthEndGap, currency, locale)
    val shortfall = insights.shortfall
    val upcoming = insights.pins.filterNot { it.isPast }.take(4)
    GlassCard(modifier = Modifier.testTag("home.river")) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Your month", style = IosText.headline, color = IosColors.textPrimary)
                    Text(
                        "Money coming in and going out, with bills and income ahead",
                        style = IosText.caption,
                        color = IosColors.textSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        signedGap,
                        style = IosText.rounded(17, FontWeight.Bold),
                        color = if (insights.monthEndGap.signum() >= 0) inColor else IosColors.expense,
                    )
                    Text("left at month end", style = IosText.caption2, color = IosColors.textSecondary)
                }
            }

            val description = riverDescription(insights, currency, locale)
            Canvas(
                Modifier.fillMaxWidth().height(176.dp)
                    .semantics { contentDescription = description },
            ) {
                if (insights.points.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
                val horizontalInset = 8.dp.toPx()
                val topInset = 26.dp.toPx()
                val bottomInset = 8.dp.toPx()
                val dayCount = (insights.monthEnd.toEpochDay() - insights.monthStart.toEpochDay() + 1).toFloat().coerceAtLeast(1f)
                val plotWidth = (size.width - horizontalInset * 2).coerceAtLeast(1f)
                val plotHeight = (size.height - topInset - bottomInset).coerceAtLeast(1f)
                val maximum = (insights.points.maxOfOrNull { maxOf(it.moneyIn, it.moneyOut) } ?: BigDecimal.ZERO)
                    .takeIf { it.signum() > 0 }?.toDouble() ?: 1.0

                fun x(date: java.time.LocalDate): Float =
                    horizontalInset + (date.toEpochDay() - insights.monthStart.toEpochDay()).toFloat() /
                        (dayCount - 1f).coerceAtLeast(1f) * plotWidth

                fun y(amount: BigDecimal): Float = topInset + plotHeight - (amount.toDouble() / maximum).toFloat().coerceIn(0f, 1f) * plotHeight

                fun drawSeries(projected: Boolean, income: Boolean, color: Color) {
                    val series = insights.points.filter { it.isProjected == projected }
                    if (series.isEmpty()) return
                    val path = Path().apply {
                        moveTo(x(series.first().date), y(if (income) series.first().moneyIn else series.first().moneyOut))
                        var previous = series.first()
                        series.drop(1).forEach { point ->
                            val previousAmount = if (income) previous.moneyIn else previous.moneyOut
                            val amount = if (income) point.moneyIn else point.moneyOut
                            lineTo(x(point.date), y(previousAmount))
                            lineTo(x(point.date), y(amount))
                            previous = point
                        }
                    }
                    drawPath(
                        path = path,
                        color = color.copy(alpha = if (projected) 0.6f else 1f),
                        style = Stroke(
                            width = if (projected) 2.5.dp.toPx() else 3.dp.toPx(),
                            pathEffect = if (projected) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())) else null,
                        ),
                    )
                }

                drawSeries(projected = false, income = true, color = inColor)
                drawSeries(projected = false, income = false, color = OutColor)
                drawSeries(projected = true, income = true, color = inColor)
                drawSeries(projected = true, income = false, color = OutColor)

                val todayX = x(insights.today)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(todayX, topInset),
                    end = Offset(todayX, size.height - bottomInset),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
                insights.pins.forEach { pin ->
                    val levelY = y(pin.level)
                    val pinColor = if (pin.isIncome) inColor else categoryColor(pin.colorHex)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(x(pin.date), levelY))
                    drawCircle(pinColor.copy(alpha = if (pin.isPast) 1f else 0.75f), 4.dp.toPx(), Offset(x(pin.date), levelY))
                }
            }

            Row(Modifier.fillMaxWidth().testTag("home.river.axis"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(insights.monthStart.format(dayFormatter(locale)), style = IosText.caption2, color = IosColors.textSecondary)
                Text("Today", style = IosText.caption2.w(FontWeight.Bold), color = Color.White, modifier = Modifier.clip(CircleShape).background(tint).padding(horizontal = 7.dp, vertical = 2.dp))
                Text(insights.monthEnd.format(dayFormatter(locale)), style = IosText.caption2, color = IosColors.textSecondary)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                RiverLegend("In", insights.monthIn, inColor, currency, locale)
                RiverLegend("Out", insights.monthOut, OutColor, currency, locale)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp).height(2.dp).background(IosColors.textTertiary))
                    Text("scheduled", style = IosText.caption2, color = IosColors.textTertiary)
                }
            }

            if (shortfall != null) {
                val until = if (shortfall.end == insights.monthEnd) "through month end" else "until ${shortfall.end.plusDays(1).format(dayFormatter(locale))}"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OutColor.copy(alpha = 0.08f)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconChip(SfSymbols.icon("exclamationmark.triangle.fill"), OutColor, size = 32.dp)
                    Text(
                        "Out is ahead of in $until, by up to ${Money.format(shortfall.amount.toDouble(), currency, locale)}. That part comes from savings or what’s already in your account.",
                        style = IosText.caption,
                        color = IosColors.textPrimary,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (upcoming.isEmpty()) {
                    Icon(SfSymbols.icon("checkmark.seal.fill"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(14.4.dp))
                    Text("No more bills or income this month", style = IosText.caption, color = IosColors.textSecondary)
                } else {
                    Text("Coming up", style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                    upcoming.forEach { pin ->
                        Row(
                            Modifier.testTag("home.river.pin.${pin.id}").clip(CircleShape).background(IosColors.textTertiary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PinBadge(pin, 16.dp)
                            Text(pin.date.dayOfMonth.toString(), style = IosText.caption2.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RiverLegend(label: String, amount: BigDecimal, color: Color, currency: String, locale: Locale) {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { format.currency = Currency.getInstance(currency) }
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    Row(
        Modifier.testTag("home.river.legend.${label.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp).height(4.dp).clip(CircleShape).background(color))
        Text(
            "$label ${format.format(amount)}",
            style = IosText.caption.w(FontWeight.SemiBold),
            color = IosColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun riverDescription(insights: HomeInsights, currency: String, locale: Locale): String {
    val parts = mutableListOf(
        "Your month. In ${Money.format(insights.monthIn.toDouble(), currency, locale)}, out ${Money.format(insights.monthOut.toDouble(), currency, locale)}, ${signedAmount(insights.monthEndGap, currency, locale)} left at month end.",
    )
    insights.shortfall?.let { shortfall ->
        parts += "Out is ahead of in from ${shortfall.start.format(dayFormatter(locale))} through ${shortfall.end.format(dayFormatter(locale))}, by up to ${Money.format(shortfall.amount.toDouble(), currency, locale)}."
    }
    val upcoming = insights.pins.filterNot { it.isPast }
    if (upcoming.isNotEmpty()) {
        parts += "Coming up: ${upcoming.joinToString { "${it.name}, ${Money.format(it.amount.toDouble(), currency, locale)} on ${it.date.format(dayFormatter(locale))}" }}."
    }
    return parts.joinToString(" ")
}

private fun signedAmount(amount: BigDecimal, currency: String, locale: Locale): String =
    if (amount.signum() < 0) "−${Money.format(amount.abs().toDouble(), currency, locale)}" else Money.format(amount.toDouble(), currency, locale)

private fun dayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
