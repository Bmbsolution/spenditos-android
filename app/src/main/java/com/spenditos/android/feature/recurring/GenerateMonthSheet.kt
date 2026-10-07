package com.spenditos.android.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Generate for a month", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { onMonthChange(month.minusMonths(1)) }, modifier = Modifier.testTag("recurring.generate.previous")) { Text("Previous") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0])), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp).testTag("recurring.generate.month"))
                OutlinedButton(onClick = { onMonthChange(month.plusMonths(1)) }, modifier = Modifier.testTag("recurring.generate.next")) { Text("Next") }
            }
            GlassCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("${preview.count} transactions to create", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("recurring.generate.count"))
                    Text("Estimated total · ${Money.format(preview.total.toDouble(), currency)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("recurring.generate.total"))
                }
            }
            error?.let {
                GlassCard(Modifier.testTag("recurring.generate.error")) {
                    Text(it, modifier = Modifier.padding(Spacing.m), color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PillButton("Cancel", onDismiss, modifier = Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.cancel"), filled = false)
                PillButton(if (isGenerating) "Generating…" else "Generate", { onGenerate(month) }, enabled = !isGenerating, modifier = Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.confirm"))
            }
        }
    }
}

data class GeneratePreview(val count: Int, val total: BigDecimal)

fun generatePreview(templates: List<RecurringTemplate>, month: YearMonth): GeneratePreview {
    var count = 0
    var total = BigDecimal.ZERO
    templates.filter { it.isActive && it.type == "fixed" }.forEach { template ->
        val dates = RecurrenceMath.occurrencesInMonth(template, month)
        count += dates.size
        total = total.add(BigDecimal.valueOf(template.amount).multiply(BigDecimal.valueOf(dates.size.toLong())))
    }
    return GeneratePreview(count, total)
}
