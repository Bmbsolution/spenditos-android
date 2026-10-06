package com.spenditos.android.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun CaptureReviewStep(
    items: List<CaptureReviewItem>,
    categories: List<Category>,
    currency: String,
    onIncludeChange: (String) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onDescriptionChange: (String, String) -> Unit,
    onDateChange: (String, LocalDate) -> Unit,
    onCategoryChange: (String, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth().testTag("capture.review.list")) {
        items.forEach { item ->
            val key = item.idempotencyKeyId
            var categoryMenu by remember(key) { mutableStateOf(false) }
            var showDatePicker by remember(key) { mutableStateOf(false) }
            val date = runCatching { LocalDate.parse(item.date) }.getOrDefault(LocalDate.now())
            val income = item.transactionType.equals("income", ignoreCase = true)
            val choices = categories.filter { it.isIncome == income }
            val selectedCategory = choices.firstOrNull { it.id == item.categoryId }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("capture.item.$key"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Transaction", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("Include", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = item.include, onCheckedChange = { onIncludeChange(key) }, enabled = !item.isFrozen, modifier = Modifier.testTag("capture.include.$key"))
                    }
                    OutlinedTextField(
                        value = item.amountText,
                        onValueChange = { onAmountChange(key, it) },
                        label = { Text("Amount ($currency)") },
                        singleLine = true,
                        enabled = !item.isFrozen,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("capture.amount.$key"),
                    )
                    OutlinedTextField(
                        value = item.description,
                        onValueChange = { onDescriptionChange(key, it) },
                        label = { Text("Description") },
                        singleLine = true,
                        enabled = !item.isFrozen,
                        modifier = Modifier.fillMaxWidth().testTag("capture.description.$key"),
                    )
                    OutlinedButton(onClick = { showDatePicker = true }, enabled = !item.isFrozen, modifier = Modifier.fillMaxWidth().testTag("capture.date.$key")) {
                        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }
                    Column {
                        OutlinedButton(onClick = { categoryMenu = true }, enabled = !item.isFrozen, modifier = Modifier.fillMaxWidth().testTag("capture.category.$key")) {
                            Text(selectedCategory?.name ?: "Uncategorized")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Uncategorized") },
                                onClick = { onCategoryChange(key, null); categoryMenu = false },
                                modifier = Modifier.testTag("capture.category.none.$key"),
                            )
                            choices.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { onCategoryChange(key, category.id); categoryMenu = false },
                                    modifier = Modifier.testTag("capture.category.${category.id}"),
                                )
                            }
                        }
                    }
                    if (!item.currency.isNullOrBlank() && !item.currency.equals(currency, ignoreCase = true)) {
                        Text("Parsed as ${item.currency.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture.currency.$key"))
                    }
                    if (item.isFrozen) Text("Retry to confirm this save before editing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture.frozen.$key"))
                    item.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("capture.failure.$key")) }
                }
            }

            if (showDatePicker) {
                val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { onDateChange(key, LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                            showDatePicker = false
                        }, modifier = Modifier.testTag("capture.date.confirm.$key")) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }, modifier = Modifier.testTag("capture.date.cancel.$key")) { Text("Cancel") } },
                ) { DatePicker(state = pickerState, modifier = Modifier.testTag("capture.date.picker.$key")) }
            }
        }
    }
}
