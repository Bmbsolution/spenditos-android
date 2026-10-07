package com.spenditos.android.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class RecurringDraft(
    val description: String = "",
    val amountText: String = "",
    val amountVaries: Boolean = false,
    val frequency: String = "monthly",
    val isIncome: Boolean = false,
    val categoryId: String? = null,
    val firstOccurrenceDate: LocalDate = LocalDate.now(),
    val isActive: Boolean = true,
    val repeatIntervalText: String = "1",
    val occurrencesRemainingText: String = "",
    val secondOccurrenceDayText: String = "15",
) {
    val amount: java.math.BigDecimal? get() = amountText.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 && it.toDouble().isFinite() }
    val repeatInterval: Int? get() = repeatIntervalText.toIntOrNull()?.takeIf { it > 0 }
    val occurrencesRemaining: Int? get() = occurrencesRemainingText.trim().toIntOrNull()?.takeIf { it > 0 }
    val secondOccurrenceDay: Int? get() = secondOccurrenceDayText.toIntOrNull()?.takeIf { it in 1..31 }
    val canSave: Boolean
        get() = description.trim().isNotEmpty() && amount != null && categoryId != null && repeatInterval != null &&
            (frequency != "semi-monthly" || secondOccurrenceDay != null)

    companion object {
        fun from(template: RecurringTemplate, isIncome: Boolean): RecurringDraft = RecurringDraft(
            description = template.description,
            amountText = java.math.BigDecimal.valueOf(template.amount).stripTrailingZeros().toPlainString(),
            amountVaries = template.amountVaries,
            frequency = template.frequency,
            isIncome = isIncome,
            categoryId = template.categoryId,
            firstOccurrenceDate = runCatching { LocalDate.parse(template.firstOccurrenceDate) }.getOrDefault(LocalDate.now()),
            isActive = template.isActive,
            repeatIntervalText = (template.repeatInterval ?: 1).toString(),
            occurrencesRemainingText = template.occurrencesRemaining?.toString().orEmpty(),
            secondOccurrenceDayText = (template.secondOccurrenceDay ?: 15).toString(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditRecurringSheet(
    template: RecurringTemplate?,
    categories: List<Category>,
    currency: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (RecurringDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editIsIncome = template?.let { item -> categories.firstOrNull { it.id == item.categoryId }?.isIncome == true }
    var draft by remember(template?.id, editIsIncome) {
        mutableStateOf(template?.let { RecurringDraft.from(it, categories.firstOrNull { category -> category.id == it.categoryId }?.isIncome == true) } ?: RecurringDraft())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val visibleCategories = categories.filter { it.isIncome == draft.isIncome }
    val frequencyOptions = listOf("daily", "weekly", "bi-weekly", "semi-monthly", "monthly", "quarterly", "semi-annually", "yearly")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(if (template == null) "Add income or bill" else "Edit income or bill", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it.take(160)) },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("recurring.form.description"),
            )
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { draft = draft.copy(amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                label = { Text("Amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("recurring.form.amount"),
            )
            GlassCard {
                Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("The amount changes each time", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = draft.amountVaries,
                        onCheckedChange = { draft = draft.copy(amountVaries = it) },
                        modifier = Modifier.testTag("recurring.form.amountVaries"),
                    )
                }
            }
            Text("Type", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, isIncome ->
                    SegmentedButton(
                        selected = draft.isIncome == isIncome,
                        onClick = { draft = draft.copy(isIncome = isIncome, categoryId = null) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.testTag("recurring.form.type.${if (isIncome) "income" else "bill"}"),
                    ) { Text(if (isIncome) "Income" else "Bill") }
                }
            }
            Text(if (draft.isIncome) "Income category" else "Bill category", style = MaterialTheme.typography.labelLarge)
            if (visibleCategories.isEmpty()) {
                Text("No matching categories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("recurring.form.categories.empty"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                visibleCategories.forEach { category ->
                    FilterChip(
                        selected = draft.categoryId == category.id,
                        onClick = { draft = draft.copy(categoryId = category.id) },
                        label = { Text(category.name) },
                        modifier = Modifier.testTag("recurring.form.category.${category.id}"),
                    )
                }
            }
            Text("Frequency", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                frequencyOptions.forEach { frequency ->
                    FilterChip(
                        selected = draft.frequency == frequency,
                        onClick = { draft = draft.copy(frequency = frequency) },
                        label = { Text(frequencyLabel(frequency)) },
                        modifier = Modifier.testTag("recurring.form.frequency.$frequency"),
                    )
                }
            }
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().testTag("recurring.form.date")) {
                Text("First date · ${draft.firstOccurrenceDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}")
            }
            GlassCard {
                Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = draft.isActive, onCheckedChange = { draft = draft.copy(isActive = it) }, modifier = Modifier.testTag("recurring.form.active"))
                }
            }
            Text("Advanced", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = draft.repeatIntervalText,
                onValueChange = { draft = draft.copy(repeatIntervalText = it.filter(Char::isDigit)) },
                label = { Text("Repeat every (1 or more)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("recurring.form.repeatInterval"),
            )
            OutlinedTextField(
                value = draft.occurrencesRemainingText,
                onValueChange = { draft = draft.copy(occurrencesRemainingText = it.filter(Char::isDigit)) },
                label = { Text("Occurrences remaining") },
                placeholder = { Text("Never") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("recurring.form.occurrencesRemaining"),
            )
            if (draft.frequency == "semi-monthly") {
                OutlinedTextField(
                    value = draft.secondOccurrenceDayText,
                    onValueChange = { draft = draft.copy(secondOccurrenceDayText = it.filter(Char::isDigit).take(2)) },
                    label = { Text("Second day (1–31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("recurring.form.secondDay"),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurring.form.error")) }
            RecurringSavePill(
                onClick = { onSave(draft) },
                enabled = draft.canSave && !isSaving,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth().testTag("recurring.form.save"),
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = draft.firstOccurrenceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { draft = draft.copy(firstOccurrenceDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDatePicker = false
                }, modifier = Modifier.testTag("recurring.form.date.confirm")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }, modifier = Modifier.testTag("recurring.form.date.cancel")) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun RecurringSavePill(onClick: () -> Unit, enabled: Boolean, isLoading: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Text("Save", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

private fun frequencyLabel(value: String): String = when (value) {
    "bi-weekly" -> "Bi-weekly"
    "semi-monthly" -> "Semi-monthly"
    "semi-annually" -> "Semi-annually"
    else -> value.replaceFirstChar(Char::uppercase)
}
