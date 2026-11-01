package com.spenditos.android.feature.widget

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spenditos.android.app.MainActivity
import com.spenditos.android.ui.components.Money
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SpenditosWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSmall, WidgetMedium, WidgetLarge),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SpenditosWidgetEntryPoint::class.java,
        )
        val snapshot = try {
            entryPoint.widgetStateProvider().snapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WidgetSnapshot.placeholder()
        }
        provideContent { WidgetContent(snapshot) }
    }

}

// Android's closest practical sizes for iOS systemSmall / Medium / Large.
private val WidgetSmall = DpSize(170.dp, 170.dp)
private val WidgetMedium = DpSize(360.dp, 170.dp)
private val WidgetLarge = DpSize(360.dp, 380.dp)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpenditosWidgetEntryPoint {
    fun widgetStateProvider(): WidgetStateProvider
}

/** The type behind androidx.glance.color.ColorProvider(day, night). */
private typealias GlanceColorProvider = androidx.glance.unit.ColorProvider

private val WidgetOrange = Color(0xFFFF6B35)
private val WidgetRed = Color(0xFFEF4444)
private val WidgetGreen = Color(0xFF22C55E)
private val WidgetBlue = Color(0xFF3B82F6)
private val WidgetCyan = Color(0xFF06B6D4)
private val WidgetGold = Color(0xFFFFD700)
private val WidgetGray = Color(0xFF8E8E93)

private val NormalBackground = ColorProvider(
    day = Color(0xFFF2F2F7),
    night = Color(0xFF1C1C1E),
)
private val StormBackground = ColorProvider(
    day = Color(0xFF2D1B1B),
    night = Color(0xFF2D1B1B),
)
private val NormalPrimary = ColorProvider(
    day = Color(0xFF000000),
    night = Color(0xFFFFFFFF),
)
private val StormPrimary = ColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFFFFFFFF),
)
private val NormalSecondary = ColorProvider(
    day = Color(0x993C3C43),
    night = Color(0x99EBEBF5),
)
private val StormSecondary = ColorProvider(
    day = Color(0xFFDED0D0),
    night = Color(0xFFDED0D0),
)

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val open = actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    val size = LocalSize.current
    val root = GlanceModifier.fillMaxSize()
        .background(if (snapshot.isStormMode) StormBackground else NormalBackground)
        .clickable(open)
    if (snapshot.isPlaceholder) {
        Box(
            modifier = root.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Strings.get(R.string.widget_open_spenditos),
                style = TextStyle(
                    color = if (snapshot.isStormMode) StormPrimary else NormalPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        return
    }

    when {
        size.height >= WidgetLarge.height -> LargeWidgetView(snapshot, root)
        size.width >= WidgetMedium.width -> MediumWidgetView(snapshot, root)
        else -> SmallWidgetView(snapshot, root)
    }
}

private fun progress(snapshot: WidgetSnapshot): Double =
    if (snapshot.dailyPace > 0.0) minOf(snapshot.spentToday / snapshot.dailyPace, 1.0) else 0.0

private fun breakEvenProgress(snapshot: WidgetSnapshot): Double =
    if (snapshot.monthIncome > 0.0) minOf(snapshot.monthExpenses / snapshot.monthIncome, 1.0) else 0.0

private fun remainingBudget(snapshot: WidgetSnapshot): Double = snapshot.dailyPace - snapshot.spentToday

private fun statusColor(snapshot: WidgetSnapshot): Color = when {
    snapshot.isStormMode -> WidgetRed
    progress(snapshot) > 0.9 -> WidgetOrange
    else -> WidgetGreen
}

private fun statusText(snapshot: WidgetSnapshot): String = when {
    snapshot.isStormMode -> Strings.get(R.string.widget_over_pace)
    progress(snapshot) > 0.9 -> Strings.get(R.string.widget_almost_reached)
    else -> Strings.get(R.string.common_on_track)
}

private fun amount(value: Double, snapshot: WidgetSnapshot): String = Money.format(value, snapshot.currency)

private fun textSecondary(snapshot: WidgetSnapshot) =
    if (snapshot.isStormMode) StormSecondary else NormalSecondary

private fun textPrimary(snapshot: WidgetSnapshot) =
    if (snapshot.isStormMode) StormPrimary else NormalPrimary

@Composable
private fun SmallWidgetView(snapshot: WidgetSnapshot, root: GlanceModifier) {
    val secondary = textSecondary(snapshot)
    val primary = textPrimary(snapshot)
    val currentProgress = if (snapshot.isStormMode) breakEvenProgress(snapshot) else progress(snapshot)
    val progressColor = if (snapshot.isStormMode) WidgetRed else if (progress(snapshot) > 0.9) WidgetRed else WidgetBlue

    Column(modifier = root.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            if (snapshot.isStormMode) {
                WidgetIcon("storm", WidgetRed, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    Strings.get(R.string.widget_storm),
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(WidgetRed), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            } else {
                WidgetIcon("flame", WidgetOrange, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    snapshot.streakDays.toString(),
                    style = TextStyle(color = primary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.height(8.dp))
        Column {
            Text(
                if (snapshot.isStormMode) Strings.get(R.string.widget_deficit) else Strings.get(R.string.common_remaining),
                style = TextStyle(color = secondary, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            val remaining = remainingBudget(snapshot)
            val displayAmount = if (snapshot.isStormMode) -abs(snapshot.net) else remaining
            val amountTint = if (snapshot.isStormMode) WidgetRed else if (remaining > 0.0) WidgetGreen else WidgetRed
            Text(
                amount(displayAmount, snapshot),
                style = TextStyle(color = androidx.glance.unit.ColorProvider(amountTint), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.defaultWeight())
        if (snapshot.isStormMode) {
            Text(
                Strings.get(R.string.widget_d_to_break_even, snapshot.daysLeft ?: 0),
                style = TextStyle(color = secondary, fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(8.dp))
        } else {
            Spacer(GlanceModifier.height(8.dp))
        }
        Image(
            provider = ImageProvider(progressBarBitmap(LocalContext.current, (LocalSize.current.width - 32.dp).value, currentProgress, progressColor)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        )
    }
}

@Composable
private fun MediumWidgetView(snapshot: WidgetSnapshot, root: GlanceModifier) {
    val ratio = if (snapshot.isStormMode) breakEvenProgress(snapshot) else progress(snapshot)
    val tint = statusColor(snapshot)
    val primary = textPrimary(snapshot)
    val secondary = textSecondary(snapshot)
    val context = LocalContext.current

    Row(
        modifier = root.padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(progressRingBitmap(context, ratio, tint, ringTextColor(context, snapshot.isStormMode))),
            contentDescription = "${(ratio * 100).toInt()}%",
            modifier = GlanceModifier.size(80.dp),
        )
        Spacer(GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetIcon(if (snapshot.isStormMode) "storm" else "flame", if (snapshot.isStormMode) WidgetRed else WidgetOrange, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    if (snapshot.isStormMode) Strings.get(R.string.widget_storm_mode) else Strings.plural(R.plurals.widget_day_streak, snapshot.streakDays),
                    style = TextStyle(color = primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    modifier = GlanceModifier.size(6.dp).background(androidx.glance.unit.ColorProvider(tint)).cornerRadius(3.dp),
                    content = {},
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    statusText(snapshot),
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(tint), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Column {
                Text(Strings.get(R.string.widget_spent_today), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                Text(amount(snapshot.spentToday, snapshot), style = TextStyle(color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                MonthFigure(Strings.get(R.string.common_income), amount(snapshot.monthIncome, snapshot), WidgetGreen, secondary)
                Spacer(GlanceModifier.width(12.dp))
                MonthFigure(Strings.get(R.string.common_expenses), amount(snapshot.monthExpenses, snapshot), WidgetRed, secondary)
                if (snapshot.isStormMode) {
                    Spacer(GlanceModifier.width(12.dp))
                    MonthFigure(Strings.get(R.string.widget_deficit), amount(abs(snapshot.net), snapshot), WidgetRed, secondary)
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.MonthFigure(label: String, value: String, color: Color, secondary: GlanceColorProvider) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.Start) {
        Text(label, style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Text(value, style = TextStyle(color = androidx.glance.unit.ColorProvider(color), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun LargeWidgetView(snapshot: WidgetSnapshot, root: GlanceModifier) {
    val primary = textPrimary(snapshot)
    val secondary = textSecondary(snapshot)
    val status = statusColor(snapshot)
    val context = LocalContext.current

    Column(modifier = root.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetIcon(if (snapshot.isStormMode) "storm" else "flame", if (snapshot.isStormMode) WidgetRed else WidgetOrange, 20.dp)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (snapshot.isStormMode) Strings.get(R.string.widget_storm_mode) else Strings.plural(R.plurals.widget_day_streak, snapshot.streakDays),
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(if (snapshot.isStormMode) WidgetRed else WidgetOrange),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier.size(6.dp).background(androidx.glance.unit.ColorProvider(status)).cornerRadius(3.dp),
                content = {},
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(statusText(snapshot), style = TextStyle(color = androidx.glance.unit.ColorProvider(status), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }

        Spacer(GlanceModifier.height(12.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            BudgetCard(Strings.get(R.string.common_spent), amount(snapshot.spentToday, snapshot), WidgetBlue, "card", secondary)
            Spacer(GlanceModifier.width(12.dp))
            val remaining = remainingBudget(snapshot)
            BudgetCard(Strings.get(R.string.widget_left_today), amount(remaining, snapshot), if (remaining > 0.0) WidgetGreen else WidgetRed, "dollar", secondary)
            Spacer(GlanceModifier.width(12.dp))
            BudgetCard(Strings.get(R.string.widget_daily_pace), amount(snapshot.dailyPace, snapshot), WidgetGray, "target", secondary)
        }

        Spacer(GlanceModifier.height(12.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column {
                        Text(Strings.get(R.string.common_income), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
                        Spacer(GlanceModifier.height(2.dp))
                        Text(amount(snapshot.monthIncome, snapshot), style = TextStyle(color = androidx.glance.unit.ColorProvider(WidgetGreen), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.Horizontal.End) {
                        Text(Strings.get(R.string.common_expenses), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
                        Spacer(GlanceModifier.height(2.dp))
                        Text(amount(snapshot.monthExpenses, snapshot), style = TextStyle(color = androidx.glance.unit.ColorProvider(WidgetRed), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
                if (snapshot.isStormMode) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            Strings.get(R.string.widget_deficit_2, amount(abs(snapshot.net), snapshot)),
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(WidgetRed), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(Strings.get(R.string.widget_d_to_break_even, snapshot.daysLeft ?: 0), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
            if (snapshot.last7Days.isNotEmpty()) {
                Spacer(GlanceModifier.width(12.dp))
                Image(
                    provider = ImageProvider(sparklineBitmap(context, snapshot.last7Days, if (snapshot.isStormMode) WidgetRed else WidgetBlue)),
                    contentDescription = Strings.get(R.string.widget_last_7_days_spending),
                    modifier = GlanceModifier.size(120.dp, 50.dp),
                )
            }
        }

        if (snapshot.topCategories.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Column {
                Text(Strings.get(R.string.widget_top_categories), style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    snapshot.topCategories.take(3).forEachIndexed { index, category ->
                        if (index > 0) Spacer(GlanceModifier.width(8.dp))
                        // iOS CategoryPill: plain "%.0f" figure, no currency symbol.
                        CategoryPill(category.name, String.format(Locale.US, "%.0f", category.amount), categoryColor(category.colorHex), index + 1, primary, secondary)
                    }
                }
            }
        }

        Spacer(GlanceModifier.height(12.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            // iOS widget Links: spenditos://add-expense and spenditos://scan-receipt.
            ActionPill("plus", Strings.get(R.string.widget_add_expense), WidgetBlue, actionStartActivity(deepLinkIntent(context, "add-expense")))
            Spacer(GlanceModifier.width(12.dp))
            ActionPill("camera", Strings.get(R.string.common_scan), WidgetGreen, actionStartActivity(deepLinkIntent(context, "scan-receipt")))
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.BudgetCard(
    title: String,
    value: String,
    color: Color,
    icon: String,
    secondary: GlanceColorProvider,
) {
    Column(
        modifier = GlanceModifier.defaultWeight()
            .background(androidx.glance.unit.ColorProvider(color.copy(alpha = 0.1f)))
            .cornerRadius(12.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        WidgetIcon(icon, color, 24.dp)
        Spacer(GlanceModifier.height(6.dp))
        Text(title, style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        Text(value, style = TextStyle(color = androidx.glance.unit.ColorProvider(color), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun androidx.glance.layout.RowScope.CategoryPill(name: String, value: String, color: Color, rank: Int, primary: GlanceColorProvider, secondary: GlanceColorProvider) {
    Row(
        modifier = GlanceModifier.defaultWeight()
            .background(androidx.glance.unit.ColorProvider(color.copy(alpha = 0.12f)))
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(16.dp).background(androidx.glance.unit.ColorProvider(color)).cornerRadius(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.toString(), style = TextStyle(color = androidx.glance.unit.ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Spacer(GlanceModifier.width(4.dp))
        Column {
            Text(name, style = TextStyle(color = primary, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(value, style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
        }
    }
}

/** Opens MainActivity on a spenditos:// host (SpenditosWidget.swift Links); the pills each own one. */
private fun deepLinkIntent(context: Context, host: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://$host"))
        .setComponent(ComponentName(context, MainActivity::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun ActionPill(icon: String, title: String, color: Color, onClick: Action) {
    Row(
        modifier = GlanceModifier.background(androidx.glance.unit.ColorProvider(color))
            .cornerRadius(10.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetIcon(icon, Color.White, 14.dp)
        Spacer(GlanceModifier.width(4.dp))
        Text(title, style = TextStyle(color = androidx.glance.unit.ColorProvider(Color.White), fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun WidgetIcon(kind: String, color: Color, size: androidx.compose.ui.unit.Dp) {
    Image(
        provider = ImageProvider(iconBitmap(LocalContext.current, kind, color, size.value)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

private fun categoryColor(hex: String?): Color = runCatching {
    if (hex.isNullOrBlank()) WidgetBlue else Color(android.graphics.Color.parseColor(if (hex.startsWith('#')) hex else "#$hex"))
}.getOrDefault(WidgetBlue)

private fun bitmap(context: Context, widthDp: Float, heightDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    return Bitmap.createBitmap(
        max(1, (widthDp * density).toInt()),
        max(1, (heightDp * density).toInt()),
        Bitmap.Config.ARGB_8888,
    )
}

private fun progressBarBitmap(context: Context, widthDp: Float, value: Double, color: Color): Bitmap {
    val output = bitmap(context, widthDp, 6f)
    val density = context.resources.displayMetrics.density
    val canvas = Canvas(output)
    val radius = 3f * density
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x338E8E93 }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    canvas.drawRoundRect(RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()), radius, radius, track)
    val end = output.width * value.coerceIn(0.0, 1.0).toFloat()
    if (end > 0f) canvas.drawRoundRect(RectF(0f, 0f, end, output.height.toFloat()), radius, radius, fill)
    return output
}

private fun progressRingBitmap(context: Context, value: Double, color: Color, textColor: Color): Bitmap {
    val output = bitmap(context, 80f, 80f)
    val density = context.resources.displayMetrics.density
    val canvas = Canvas(output)
    val stroke = 8f * density
    val inset = stroke / 2f
    val bounds = RectF(inset, inset, output.width - inset, output.height - inset)
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0x338E8E93
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawArc(bounds, 0f, 360f, false, track)
    val sweep = 360f * value.coerceIn(0.0, 1.0).toFloat()
    if (sweep > 0f) canvas.drawArc(bounds, -90f, sweep, false, arc)
    val label = "${(value.coerceIn(0.0, 1.0) * 100).toInt()}${com.spenditos.android.core.i18n.AppLanguage.percentSuffix()}"
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor.toArgb()
        textSize = 12f * density
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val centerY = output.height / 2f - (text.ascent() + text.descent()) / 2f
    canvas.drawText(label, output.width / 2f, centerY, text)
    return output
}

private fun ringTextColor(context: Context, stormMode: Boolean): Color =
    if (stormMode || (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
        Color.White
    } else {
        Color.Black
    }

private fun sparklineBitmap(context: Context, values: List<Double>, color: Color): Bitmap {
    val output = bitmap(context, 120f, 50f)
    if (values.isEmpty()) return output
    val canvas = Canvas(output)
    val width = output.width.toFloat()
    val height = output.height.toFloat()
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 1.0
    val range = maxValue - minValue
    val stepX = width / max(values.size - 1, 1)
    val points = values.mapIndexed { index, value ->
        val x = index * stepX
        val y = if (range > 0.0) height - ((value - minValue) / range * height).toFloat() else height / 2f
        x to y
    }
    val area = Path().apply {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { lineTo(it.first, it.second) }
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.copy(alpha = 0.15f).toArgb() })
    val line = Path().apply {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { lineTo(it.first, it.second) }
    }
    canvas.drawPath(
        line,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 2f * context.resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        },
    )
    val dotRadius = 2f * context.resources.displayMetrics.density
    val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    points.forEach { (x, y) -> canvas.drawCircle(x, y, dotRadius, dots) }
    return output
}

private fun iconBitmap(context: Context, kind: String, color: Color, sizeDp: Float): Bitmap {
    val output = bitmap(context, sizeDp, sizeDp)
    val canvas = Canvas(output)
    val scale = output.width / 24f
    canvas.save()
    canvas.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.FILL
    }
    when (kind) {
        "flame" -> {
            val flame = Path().apply {
                moveTo(12f, 23f)
                cubicTo(3f, 20f, 4f, 12f, 10f, 6f)
                cubicTo(10f, 11f, 14f, 11f, 14f, 2f)
                cubicTo(23f, 11f, 22f, 20f, 12f, 23f)
                close()
            }
            canvas.drawPath(flame, paint)
        }
        "storm" -> {
            canvas.drawCircle(8f, 11f, 5f, paint)
            canvas.drawCircle(14f, 9f, 6f, paint)
            canvas.drawCircle(19f, 12f, 4f, paint)
            canvas.drawRoundRect(RectF(4f, 11f, 21f, 17f), 3f, 3f, paint)
            val bolt = Path().apply {
                moveTo(13f, 13f)
                lineTo(9f, 19f)
                lineTo(12f, 19f)
                lineTo(10f, 23f)
                lineTo(17f, 16f)
                lineTo(14f, 16f)
                lineTo(16f, 13f)
                close()
            }
            canvas.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
        }
        "card" -> {
            canvas.drawRoundRect(RectF(2f, 5f, 22f, 19f), 3f, 3f, paint)
            canvas.drawRect(RectF(3f, 9f, 21f, 12f), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
        }
        "dollar" -> {
            canvas.drawCircle(12f, 12f, 10f, paint)
            val dollar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                textSize = 15f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText("$", 12f, 17f, dollar)
        }
        "target" -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(12f, 12f, 9f, paint)
            canvas.drawCircle(12f, 12f, 5f, paint)
            canvas.drawCircle(12f, 12f, 1.5f, paint)
        }
        "plus" -> {
            canvas.drawCircle(12f, 12f, 10f, paint)
            val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                strokeWidth = 2f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(12f, 7f, 12f, 17f, plus)
            canvas.drawLine(7f, 12f, 17f, 12f, plus)
        }
        "camera" -> {
            canvas.drawRoundRect(RectF(2f, 7f, 22f, 20f), 3f, 3f, paint)
            canvas.drawRoundRect(RectF(7f, 4f, 13f, 8f), 1.5f, 1.5f, paint)
            canvas.drawCircle(12f, 13.5f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
            canvas.drawCircle(12f, 13.5f, 2f, paint)
        }
        else -> Unit
    }
    canvas.restore()
    return output
}
