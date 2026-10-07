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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NextBillTile(pin: HomeInsights.Pin?, today: LocalDate, currency: String, locale: Locale) {
    val finance = LocalFinanceColors.current
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.nextBill")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Next bill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (pin == null) {
                Text("No more bills this month", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(pin.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    Money.format(pin.amount.toDouble(), currency, locale),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = finance.expense,
                )
                val days = (pin.date.toEpochDay() - today.toEpochDay()).toInt()
                val whenText = when (days) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> "In $days days · ${pin.date.format(monthDayFormatter(locale))}"
                }
                Text(whenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WeekTile(week: List<HomeInsights.WeekDay>, currency: String, locale: Locale) {
    val finance = LocalFinanceColors.current
    val neutralBar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val total = week.fold(java.math.BigDecimal.ZERO) { sum, day -> sum + day.amount }
    val description = "Everyday spending in the last 7 days: ${Money.format(total.toDouble(), currency, locale)}"
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.week")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(Money.format(total.toDouble(), currency, locale), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Canvas(
                Modifier.fillMaxWidth().height(68.dp).semantics { contentDescription = description },
            ) {
                if (week.isEmpty()) return@Canvas
                val maximum = week.maxOfOrNull { it.amount.toDouble() }?.coerceAtLeast(1.0) ?: 1.0
                val slot = size.width / week.size
                val barWidth = (slot * 0.56f).coerceAtLeast(3.dp.toPx())
                val baseline = size.height - 2.dp.toPx()
                val financeColor = finance.expense
                week.forEachIndexed { index, day ->
                    val centerX = slot * index + slot / 2f
                    val barHeight = (size.height - 8.dp.toPx()) * (day.amount.toDouble() / maximum).toFloat()
                    drawRoundRect(
                        color = if (day.isToday) financeColor else neutralBar,
                        topLeft = Offset(centerX - barWidth / 2f, baseline - barHeight.coerceAtLeast(3.dp.toPx())),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(3.dp.toPx())),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().testTag("home.week.days"), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    Text(
                        day.date.format(weekdayFormatter(locale)),
                        modifier = Modifier.testTag("home.week.day.${day.date}"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) finance.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TopCategoryTile(top: HomeInsights.TopCategory, currency: String, locale: Locale) {
    val color = categoryColor(top.colorHex)
    val sharePercent = top.share.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home.topCategory")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Most spent on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().testTag("home.topCategory.summary"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(top.name.take(1).uppercase(locale), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(top.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(Money.format(top.amount.toDouble(), currency, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(top.share.toFloat().coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(color))
            }
            Text("$sharePercent% of everyday spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun monthDayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
private fun weekdayFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", locale)
