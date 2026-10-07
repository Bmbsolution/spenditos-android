package com.spenditos.android.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.WheelPicker
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * iOS MonthPickerSheet: a wheel of the current month ±6 months.
 */
@Composable
fun GenerateMonthSheet(
    templates: List<RecurringTemplate>,
    currency: String,
    month: YearMonth,
    isGenerating: Boolean,
    error: String?,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: (YearMonth) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val preview = remember(templates, month) { generatePreview(templates, month) }
    val locale = LocalConfiguration.current.locales[0]
    val monthOptions = remember {
        val now = YearMonth.now()
        (-6..6).map { now.plusMonths(it.toLong()) }
    }
    val monthLabels = remember(locale) {
        monthOptions.map { option ->
            val label = option.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            if (option == YearMonth.now()) "$label (Current)" else label
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", Modifier.clickable(role = Role.Button, onClick = onDismiss).testTag("recurring.generate.cancel"), style = IosText.body, color = IosColors.textPrimary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Generate Transactions", style = IosText.headline, color = IosColors.textPrimary)
                }
                Spacer(Modifier.size(52.dp))
            }
            Text(
                "Process all active income and bills and create projected transactions for the selected month.",
                Modifier.padding(horizontal = Spacing.l).padding(top = Spacing.l),
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            WheelPicker(
                labels = monthLabels,
                selectedIndex = monthOptions.indexOf(month).coerceAtLeast(0),
                onSelect = { onMonthChange(monthOptions[it]) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m).testTag("recurring.generate.month"),
            )
            Text("Range: ±6 months from current month", style = IosText.caption, color = IosColors.textTertiary)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.m)
                    .clip(RoundedCornerShape(Radius.m))
                    .background(IosColors.spendOrange.copy(alpha = 0.05f))
                    .border(1.dp, IosColors.spendOrange.copy(alpha = 0.2f), RoundedCornerShape(Radius.m))
                    .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    "PREVIEW",
                    style = IosText.caption.w(FontWeight.Bold).copy(letterSpacing = 1.sp),
                    color = IosColors.spendOrange,
                )
                PreviewLine("Transactions to create:", preview.count.toString(), Modifier.testTag("recurring.generate.count"))
                PreviewLine("From income and bills:", preview.recurringCount.toString())
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                PreviewLine(
                    "Estimated total:",
                    Money.format(preview.total.toDouble(), currency),
                    valueColor = if (preview.total.signum() >= 0) IosColors.income else IosColors.expense,
                    modifier = Modifier.testTag("recurring.generate.total"),
                )
            }
            error?.let {
                Text(it, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("recurring.generate.error"), style = IosText.caption, color = IosColors.expense)
            }
            Row(
                Modifier.padding(top = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SfSymbols.icon("info.circle"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.dp))
                Text("Existing transactions will be skipped", style = IosText.caption, color = IosColors.textTertiary)
            }
            val shape = RoundedCornerShape(Radius.m)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.l)
                    .clip(shape)
                    .background(if (isGenerating) IosColors.spendOrange.copy(alpha = 0.4f) else IosColors.spendOrange)
                    .clickable(enabled = !isGenerating, role = Role.Button) { onGenerate(month) }
                    .testTag("recurring.generate.confirm")
                    .padding(vertical = Spacing.m),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SfSymbols.icon("calendar.badge.plus"), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Text(
                    if (isGenerating) "Generating…" else "Log this month now",
                    Modifier.padding(start = Spacing.s),
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = IosColors.textPrimary) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = IosText.subheadline, color = IosColors.textSecondary)
        Text(value, style = IosText.callout.w(FontWeight.SemiBold), color = valueColor)
    }
}

data class GeneratePreview(val count: Int, val recurringCount: Int, val total: BigDecimal)

fun generatePreview(templates: List<RecurringTemplate>, month: YearMonth): GeneratePreview {
    var count = 0
    var recurringCount = 0
    var total = BigDecimal.ZERO
    templates.filter { it.isActive && it.type == "fixed" }.forEach { template ->
        val dates = RecurrenceMath.occurrencesInMonth(template, month)
        count += dates.size
        recurringCount += 1
        total = total.add(BigDecimal.valueOf(template.amount).multiply(BigDecimal.valueOf(dates.size.toLong())))
    }
    return GeneratePreview(count, recurringCount, total)
}
