package com.spenditos.android.feature.home.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import kotlin.math.roundToInt

/** Score detail (iOS `HealthScoreDetailView`): the ring, savings rate, spending trend and tips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreSheet(breakdown: HealthScoreBreakdown, onDismiss: () -> Unit) {
    val finance = LocalFinanceColors.current
    val scoreColor = when {
        breakdown.score < 30 -> finance.expense
        breakdown.score <= 70 -> Color(0xFFD98A1F)
        else -> finance.income
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m).testTag("home.healthSheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Health score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(140.dp).clearAndSetSemantics { contentDescription = "Health score ${breakdown.score} out of 100" }) {
                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arc = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc, style = stroke)
                    drawArc(scoreColor, -90f, 360f * breakdown.score / 100f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arc, style = stroke)
                }
                Text("${breakdown.score}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = scoreColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Savings rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(breakdown.savingsRate * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Spending trend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val (label, color) = when (breakdown.trend) {
                        SpendingTrend.INCREASING -> "Going up" to finance.expense
                        SpendingTrend.STABLE -> "Steady" to Color(0xFFD98A1F)
                        SpendingTrend.DECREASING -> "Going down" to finance.income
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
            Text("What to try", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            breakdown.recommendations.forEach { tip ->
                Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().testTag("home.healthTip"))
            }
            Text(
                "Based on this month's income, spending and upcoming bills, and your spending over the last 7 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
