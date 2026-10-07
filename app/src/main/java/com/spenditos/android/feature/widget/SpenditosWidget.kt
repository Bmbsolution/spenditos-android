package com.spenditos.android.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import com.spenditos.android.feature.home.CashFlowStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

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

    private companion object {
        // Breakpoints that fit the existing 110×70 dp minimum and grow into iOS
        // small / medium / large families. Glance reports LocalSize as one of these.
        val WidgetSmall = DpSize(110.dp, 70.dp)
        val WidgetMedium = DpSize(250.dp, 110.dp)
        val WidgetLarge = DpSize(250.dp, 280.dp)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpenditosWidgetEntryPoint {
    fun widgetStateProvider(): WidgetStateProvider
}

private val WidgetOrange = Color(0xFFFF6B35)
private val WidgetRed = Color(0xFFEF4444)
private val WidgetGreen = Color(0xFF22C55E)
private val WidgetBlue = Color(0xFF3B82F6)
private val WidgetGray = Color(0xFF8E8E93)

private val WidgetBackground = ColorProvider(
    day = Color(0xFFF2F2F7),
    night = Color(0xFF1C1C1E),
)
private val TextPrimary = ColorProvider(
    day = Color(0xFF000000),
    night = Color(0xFFFFFFFF),
)
private val TextSecondary = ColorProvider(
    day = Color(0x993C3C43),
    night = Color(0x99EBEBF5),
)

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val open = actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    val size = LocalSize.current
    val root = GlanceModifier.fillMaxSize().background(WidgetBackground).clickable(open)
    if (snapshot.isPlaceholder) {
        Box(
            modifier = root.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Open Spenditos",
                style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        return
    }
    when {
        size.height >= SpenditosWidgetSizes.large.height -> LargeWidget(snapshot, root)
        size.width >= SpenditosWidgetSizes.medium.width -> MediumWidget(snapshot, root)
        else -> SmallWidget(snapshot, root)
    }
}

private object SpenditosWidgetSizes {
    val medium = DpSize(250.dp, 110.dp)
    val large = DpSize(250.dp, 280.dp)
}

@Composable
private fun SmallWidget(snapshot: WidgetSnapshot, root: GlanceModifier) {
    // SmallWidgetView: padding 16, stack spacing 8, flexible spacers, caption / title2 / caption2.
    Column(modifier = root.padding(16.dp)) {
        Spacer(GlanceModifier.defaultWeight())
        AmountBlock(snapshot, amountSize = 22, gap = 4)
        Spacer(GlanceModifier.defaultWeight())
        DaysLine(snapshot)
    }
}

@Composable
private fun MediumWidget(snapshot: WidgetSnapshot, root: GlanceModifier) {
    // MediumWidgetView detail column: padding 16, spacing 6, subheadline header, title2 amount.
    Column(
        modifier = root.padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        StatusRow(snapshot, fontSize = 12, weight = FontWeight.Bold)
        Spacer(GlanceModifier.height(6.dp))
        AmountBlock(snapshot, labelSize = 11, amountSize = 22, gap = 2)
        snapshot.daysLeft?.let {
            Spacer(GlanceModifier.height(6.dp))
            DaysLine(snapshot)
        }
    }
}

@Composable
private fun LargeWidget(snapshot: WidgetSnapshot, root: GlanceModifier) {
    // LargeWidgetView: padding 16, section spacing 12, headline header, budget card (radius 12).
    Column(modifier = root.padding(16.dp)) {
        StatusRow(snapshot, fontSize = 12, weight = FontWeight.Bold)
        Spacer(GlanceModifier.height(12.dp))
        val tint = amountColor(snapshot)
        Column(
            modifier = GlanceModifier.fillMaxWidth()
                .background(androidx.glance.unit.ColorProvider(tint.copy(alpha = 0.1f)))
                .cornerRadius(12.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                "Left after bills",
                style = TextStyle(color = TextSecondary, fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                snapshot.formattedLeftAfterBills().orEmpty(),
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(tint),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        snapshot.daysLeft?.let {
            Spacer(GlanceModifier.height(12.dp))
            DaysLine(snapshot)
        }
    }
}

@Composable
private fun StatusRow(snapshot: WidgetSnapshot, fontSize: Int, weight: FontWeight) {
    val label = snapshot.stateLabel ?: return
    val color = androidx.glance.unit.ColorProvider(statusColor(snapshot.state))
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(
            modifier = GlanceModifier.size(6.dp)
                .background(color)
                .cornerRadius(3.dp),
            content = {},
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            label,
            style = TextStyle(color = color, fontSize = fontSize.sp, fontWeight = weight),
            maxLines = 1,
        )
    }
}

@Composable
private fun AmountBlock(snapshot: WidgetSnapshot, labelSize: Int = 12, amountSize: Int, gap: Int = 4) {
    Column {
        Text(
            "Left after bills",
            style = TextStyle(color = TextSecondary, fontSize = labelSize.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(gap.dp))
        Text(
            snapshot.formattedLeftAfterBills().orEmpty(),
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(amountColor(snapshot)),
                fontSize = amountSize.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun DaysLine(snapshot: WidgetSnapshot) {
    val days = snapshot.daysLeft ?: return
    Text(
        "$days ${if (days == 1) "day" else "days"} left",
        style = TextStyle(color = TextSecondary, fontSize = 11.sp),
        maxLines = 1,
    )
}

private fun statusColor(state: CashFlowStatus.State?): Color = when (state) {
    CashFlowStatus.State.ON_TRACK -> WidgetGreen
    CashFlowStatus.State.WATCH -> WidgetOrange
    CashFlowStatus.State.TIGHT -> WidgetOrange
    CashFlowStatus.State.OVER -> WidgetRed
    CashFlowStatus.State.SETUP -> WidgetBlue
    null -> WidgetGray
}

private fun amountColor(snapshot: WidgetSnapshot): Color {
    val left = snapshot.leftAfterBills ?: return WidgetGray
    return if (left.signum() > 0) WidgetGreen else WidgetRed
}
