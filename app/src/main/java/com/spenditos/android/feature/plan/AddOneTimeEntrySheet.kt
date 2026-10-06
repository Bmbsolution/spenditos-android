package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOneTimeEntrySheet(
    month: YearMonth,
    categories: List<Category>,
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (String, BigDecimal, String, LocalDate) -> Boolean,
) {
    val expenseCategories = categories.filterNot { it.isIncome }
    val firstCategory = expenseCategories.firstOrNull()?.id ?: categories.firstOrNull()?.id.orEmpty()
    var isIncome by remember { mutableStateOf(categories.firstOrNull { it.id == firstCategory }?.isIncome ?: false) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(firstCategory) }
    var date by remember { mutableStateOf(month.atDay(1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val availableCategories = categories.filter { it.isIncome == isIncome }
    val amount = parsePlanAmount(amountText)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Add one-time item", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    selected = !isIncome,
                    onClick = {
                        isIncome = false
                        categoryId = categories.firstOrNull { !it.isIncome }?.id.orEmpty()
                    },
                    label = { Text("Expense") },
                    modifier = Modifier.testTag("monthPlan.oneTime.expense"),
                )
                FilterChip(
                    selected = isIncome,
                    onClick = {
                        isIncome = true
                        categoryId = categories.firstOrNull { it.isIncome }?.id.orEmpty()
                    },
                    label = { Text("Income") },
                    modifier = Modifier.testTag("monthPlan.oneTime.income"),
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(200); error = null },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime.description"),
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = null },
                label = { Text("Amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime.amount"),
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            if (availableCategories.isEmpty()) {
                Text("No categories available. Add one in Income & bills.", style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                availableCategories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                        modifier = Modifier.testTag("monthPlan.oneTime.category.${category.id}"),
                    )
                }
            }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime.date"),
            ) {
                Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("monthPlan.oneTime.error")) }
            Button(
                onClick = {
                    val parsed = amount
                    if (parsed == null || categoryId !in availableCategories.map { it.id } || YearMonth.from(date) != month || description.isBlank()) {
                        error = "Enter a description, amount, category, and date in this month."
                    } else if (onAdd(description, parsed, categoryId, date)) {
                        onDismiss()
                    } else {
                        error = "Enter a description, amount, category, and date in this month."
                    }
                },
                enabled = amount != null && description.isNotBlank() && categoryId.isNotBlank() && availableCategories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime.add"),
            ) { Text("Add to draft") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / MILLIS_PER_DAY) }
                    showDatePicker = false
                }, modifier = Modifier.testTag("monthPlan.oneTime.dateConfirm")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }, modifier = Modifier.testTag("monthPlan.oneTime.dateCancel")) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
