package com.spenditos.android.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
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
            val finance = LocalFinanceColors.current
            val typeTint = if (income) finance.income else finance.expense

            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("capture.item.$key"),
                shape = RoundedCornerShape(Radius.l),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(Radius.s)).background(typeTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(Icons.Filled.Category, contentDescription = null, tint = typeTint, modifier = Modifier.size(18.dp))
                        }
                        Text("Transaction", style = MaterialTheme.typography.titleMedium, color = typeTint, modifier = Modifier.weight(1f))
                        Text("Include", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = item.include, onCheckedChange = { onIncludeChange(key) }, enabled = !item.isFrozen, modifier = Modifier.testTag("capture.include.$key"))
                    }
                    ReviewInputField(
                        value = item.amountText,
                        onValueChange = { onAmountChange(key, it) },
                        label = "Amount ($currency)",
                        enabled = !item.isFrozen,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("capture.amount.$key"),
                    )
                    ReviewInputField(
                        value = item.description,
                        onValueChange = { onDescriptionChange(key, it) },
                        label = "Description",
                        enabled = !item.isFrozen,
                        modifier = Modifier.testTag("capture.description.$key"),
                    )
                    ReviewChoice(
                        text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        icon = { androidx.compose.material3.Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag("capture.date.$key"),
                        enabled = !item.isFrozen,
                    )
                    Box {
                        ReviewChoice(
                            text = selectedCategory?.name ?: "Uncategorized",
                            icon = { androidx.compose.material3.Icon(Icons.Filled.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { categoryMenu = true },
                            modifier = Modifier.testTag("capture.category.$key"),
                            enabled = !item.isFrozen,
                            trailingIcon = { androidx.compose.material3.Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
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
                    item.failureReason?.let { Text(it, color = finance.expense, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("capture.failure.$key")) }
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

@Composable
private fun ReviewInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(shape = RoundedCornerShape(Radius.m)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 15.dp).then(modifier),
            )
        }
    }
}

@Composable
private fun ReviewChoice(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: @Composable () -> Unit = {},
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            icon()
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            trailingIcon()
        }
    }
}
