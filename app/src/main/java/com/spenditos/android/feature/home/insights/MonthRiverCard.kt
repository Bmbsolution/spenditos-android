package com.spenditos.android.feature.home.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthRiverCard(insights: HomeInsights, currency: String, locale: Locale) {
    val finance = LocalFinanceColors.current
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val signedGap = signedAmount(insights.monthEndGap, currency, locale)
    val shortfall = insights.shortfall
    val upcoming = insights.pins.filterNot { it.isPast }.take(4)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.river")) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Your month", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Money coming in and going out, with bills and income ahead",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$signedGap left at month end",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (insights.monthEndGap.signum() >= 0) finance.income else finance.expense,
            )

            val description = riverDescription(insights, currency, locale)
            Canvas(
                Modifier.fillMaxWidth().height(176.dp)
                    .semantics { contentDescription = description },
            ) {
                if (insights.points.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
                val horizontalInset = 8.dp.toPx()
                val topInset = 8.dp.toPx()
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

                shortfall?.let { span ->
                    val areaPoints = insights.points
                        .filter { it.date in span.start..span.end && (it.isProjected || it.date == insights.today) }
                        .distinctBy { it.date }
                    if (areaPoints.size > 1) {
                        val area = Path().apply {
                            moveTo(x(areaPoints.first().date), y(areaPoints.first().moneyOut))
                            areaPoints.drop(1).forEach { lineTo(x(it.date), y(it.moneyOut)) }
                            areaPoints.asReversed().forEach { lineTo(x(it.date), y(it.moneyIn)) }
                            close()
                        }
                        drawPath(area, finance.expense.copy(alpha = 0.12f))
                    }
                }

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
                        color = color.copy(alpha = if (projected) 0.68f else 1f),
                        style = Stroke(
                            width = if (projected) 2.dp.toPx() else 3.dp.toPx(),
                            pathEffect = if (projected) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                        ),
                    )
                }

                drawSeries(projected = false, income = true, color = finance.income)
                drawSeries(projected = false, income = false, color = finance.expense)
                drawSeries(projected = true, income = true, color = finance.income)
                drawSeries(projected = true, income = false, color = finance.expense)

                val todayX = x(insights.today)
                drawLine(
                    color = axisColor.copy(alpha = 0.5f),
                    start = Offset(todayX, topInset),
                    end = Offset(todayX, size.height - bottomInset),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
                insights.pins.forEach { pin ->
                    val levelY = y(pin.level)
                    val pinColor = if (pin.isIncome) finance.income else categoryColor(pin.colorHex)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(x(pin.date), levelY))
                    drawCircle(pinColor, 4.dp.toPx(), Offset(x(pin.date), levelY))
                }
            }

            Row(Modifier.fillMaxWidth().testTag("home.river.axis"), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(insights.monthStart.format(dayFormatter(locale)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(insights.monthEnd.format(dayFormatter(locale)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            RiverLegend("In", insights.monthIn, finance.income, currency, locale)
            RiverLegend("Out", insights.monthOut, finance.expense, currency, locale)
            Text("Dashed lines show future amounts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (shortfall != null) {
                val until = if (shortfall.end == insights.monthEnd) "through month end" else "until ${shortfall.end.plusDays(1).format(dayFormatter(locale))}"
                Text(
                    "Out is ahead of in $until, by up to ${Money.format(shortfall.amount.toDouble(), currency, locale)}. That part comes from savings or what’s already in your account.",
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(finance.expense.copy(alpha = 0.08f)).padding(Spacing.s),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (upcoming.isEmpty()) {
                Text("No more bills or income this month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Coming up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                upcoming.forEach { pin ->
                    Row(
                        Modifier.fillMaxWidth().testTag("home.river.pin.${pin.id}"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (pin.isIncome) finance.income else categoryColor(pin.colorHex)))
                        Text(
                            "${pin.name} · ${pin.date.format(dayFormatter(locale))} · ${Money.format(pin.amount.toDouble(), currency, locale)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiverLegend(label: String, amount: BigDecimal, color: Color, currency: String, locale: Locale) {
    Row(
        Modifier.testTag("home.river.legend.${label.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(14.dp).height(4.dp).clip(CircleShape).background(color))
        Text("$label ${Money.format(amount.toDouble(), currency, locale)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
