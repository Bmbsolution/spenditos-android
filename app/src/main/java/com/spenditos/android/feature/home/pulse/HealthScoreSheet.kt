package com.spenditos.android.feature.home.pulse

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreSheet(breakdown: HealthScoreBreakdown, onDismiss: () -> Unit) {
    val scoreColor = when {
        breakdown.score < 30 -> IosColors.expense
        breakdown.score <= 70 -> IosColors.warning
        else -> IosColors.income
    }
    val gradient = when {
        breakdown.score < 30 -> listOf(Color(0xFFDC2626), Color(0xFFEF4444))
        breakdown.score <= 70 -> listOf(Color(0xFFF59E0B), Color(0xFFF97316))
        else -> listOf(Color(0xFF22C55E), Color(0xFF16A34A))
    }
    val status = when (breakdown.score) {
        in 80..100 -> stringResource(R.string.home_excellent)
        in 60..79 -> stringResource(R.string.common_good)
        in 40..59 -> stringResource(R.string.home_needs_attention)
        in 30..39 -> stringResource(R.string.home_at_risk)
        else -> stringResource(R.string.home_critical)
    }
    val (trendIcon, trendColor, trendLabel) = when (breakdown.trend) {
        SpendingTrend.INCREASING -> Triple<ImageVector, Color, String>(SfSymbols.icon("arrow.up.forward"), IosColors.expense, "Increasing")
        SpendingTrend.STABLE -> Triple(SfSymbols.icon("minus"), IosColors.warning, "Stable")
        SpendingTrend.DECREASING -> Triple(SfSymbols.icon("arrow.down.forward"), IosColors.income, "Decreasing")
    }
    val trendScore = when (breakdown.trend) {
        SpendingTrend.DECREASING -> 1.0
        SpendingTrend.STABLE -> 0.5
        SpendingTrend.INCREASING -> 0.0
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m).testTag("home.healthSheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.home_health_score), style = IosText.headline, color = IosColors.textPrimary)

            Column(
                Modifier.fillMaxWidth().then(glassCardModifier(shadowAlpha = 0.12f)).padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(150.dp).clearAndSetSemantics { contentDescription = Strings.get(R.string.home_financial_health_score_out_of, breakdown.score) }) {
                        val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                        val inset = stroke.width / 2
                        val arc = Size(size.width - stroke.width, size.height - stroke.width)
                        drawArc(Color.Black.copy(alpha = 0.06f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = stroke)
                        drawArc(
                            Brush.linearGradient(gradient),
                            -90f,
                            360f * breakdown.score / 100f,
                            false,
                            topLeft = Offset(inset, inset),
                            size = arc,
                            style = stroke,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${breakdown.score}", style = IosText.rounded(34, FontWeight.Bold), color = scoreColor)
                        Text("/ 100", style = IosText.subheadline, color = IosColors.textSecondary)
                    }
                }
                Text(status, style = IosText.title2.w(FontWeight.Bold), color = scoreColor)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.home_score_breakdown), style = IosText.headline, color = IosColors.textPrimary)
                FactorCard(stringResource(R.string.home_savings_rate), stringResource(R.string.home_income_left_after_expenses), "80%", breakdown.savingsRate, null, SfSymbols.icon("banknote"), IosColors.income)
                FactorCard(stringResource(R.string.common_spending_trend), stringResource(R.string.home_recent_spending_direction), "20%", trendScore, trendLabel, trendIcon, trendColor)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.home_recommendations), style = IosText.headline, color = IosColors.textPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    breakdown.recommendations.forEach { tip ->
                        Row(
                            Modifier.fillMaxWidth().then(glassCardModifier(RoundedCornerShape(Radius.m))).padding(Spacing.m).testTag("home.healthTip"),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(Radius.s)).background(IosColors.spendOrange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(SfSymbols.icon("lightbulb"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
                            }
                            Text(tip, style = IosText.callout, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorCard(
    title: String,
    subtitle: String,
    weight: String,
    value: Double,
    displayValue: String?,
    icon: ImageVector,
    iconColor: Color,
) {
    Column(
        Modifier.fillMaxWidth().then(glassCardModifier(RoundedCornerShape(Radius.m))).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(Radius.s)).background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(19.2.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                    Text(
                        weight,
                        style = IosText.caption.w(FontWeight.Medium),
                        color = IosColors.textTertiary,
                        modifier = Modifier.clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(subtitle, style = IosText.subheadline, color = IosColors.textSecondary)
            }
            Text(displayValue ?: "${(value * 100).roundToInt()}%", style = IosText.callout.w(FontWeight.Bold), color = iconColor)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(iconColor))
        }
    }
}
