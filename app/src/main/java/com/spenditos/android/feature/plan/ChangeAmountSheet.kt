package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal

@Composable
fun ChangeAmountSheet(
    row: MonthPlanRecurringRow,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
    onReset: () -> Unit,
) {
    var amountText by remember { mutableStateOf((row.amounts.firstOrNull() ?: row.usualAmount).setScale(2).toPlainString()) }
    val amount = parsePlanAmount(amountText)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Change amount", style = MaterialTheme.typography.titleLarge)
            Text(row.description, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.changeAmount.value"),
            )
            Text("Only this month. Your usual amount in Income & bills stays the same.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { amount?.let { onSave(it); onDismiss() } },
                enabled = amount != null && row.dates.size <= 10,
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.changeAmount.save"),
            ) { Text("Save to draft") }
            if (row.dates.size > 10) {
                Text("Amount changes are unavailable for this schedule.", color = MaterialTheme.colorScheme.error)
            }
            if (row.hasAmountChange) {
                TextButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.testTag("monthPlan.changeAmount.reset")) {
                    Text("Reset to usual amount")
                }
            }
        }
    }
}
