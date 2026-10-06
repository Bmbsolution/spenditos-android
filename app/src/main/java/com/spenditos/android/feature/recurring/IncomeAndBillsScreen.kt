package com.spenditos.android.feature.recurring

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun IncomeAndBillsScreen(viewModel: IncomeAndBillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var templateToEdit by remember { mutableStateOf<RecurringTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<RecurringTemplate?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var generateMonth by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                IncomeAndBillsEvent.Saved -> { showEditor = false; templateToEdit = null }
                IncomeAndBillsEvent.Deleted -> templateToDelete = null
                is IncomeAndBillsEvent.Generated -> {
                    if (event.result.errors == 0) showGenerate = false
                    snackbar.showSnackbar("Generated ${event.result.generated} transactions. ${event.result.skipped} skipped, ${event.result.errors} errors.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Income & bills") },
                actions = {
                    TextButton(onClick = { templateToEdit = null; showEditor = true }, modifier = Modifier.testTag("recurring.add")) { Text("Add") }
                    TextButton(onClick = { showGenerate = true }, modifier = Modifier.testTag("recurring.generate.open")) { Text("Generate") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("recurring.list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.isLoading && state.templates.isEmpty()) {
                item("loading") {
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.testTag("recurring.loading"))
                    }
                }
            }
            if (state.isLoading && state.templates.isNotEmpty()) {
                item("refreshing") {
                    Row(Modifier.fillMaxWidth().testTag("recurring.refreshing"), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        LinearProgressIndicator(Modifier.weight(1f))
                        Text("Updating…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.error?.let { error ->
                item("error") {
                    Column(Modifier.fillMaxWidth().testTag("recurring.error"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        if (state.isOffline) Text(if (state.templates.isEmpty()) "No saved list yet." else "Showing saved data.", modifier = Modifier.testTag("recurring.offline"))
                        TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("recurring.retry")) { Text("Try again") }
                    }
                }
            }
            if (state.isEmpty) {
                item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l).testTag("recurring.empty"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("No income or bills yet.", style = MaterialTheme.typography.titleMedium)
                        Text("Add a recurring item to get started.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { templateToEdit = null; showEditor = true }, modifier = Modifier.testTag("recurring.empty.add")) { Text("Add") }
                    }
                }
            }
            item("income.header") { SectionHeader("Income", "recurring.section.income") }
            if (!state.isLoading && state.incomeTemplates.isEmpty() && state.templates.isNotEmpty()) {
                item("income.empty") { Text("No income yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("recurring.income.empty")) }
            }
            items(state.incomeTemplates, key = RecurringTemplate::id) { template ->
                IncomeBillRow(template, state.categories.firstOrNull { it.id == template.categoryId }?.name, true, state.mainIncomeTemplateId, state.currency,
                    onEdit = { templateToEdit = template; showEditor = true },
                    onDelete = { templateToDelete = template },
                    onMainIncome = { viewModel.setMainIncome(if (state.mainIncomeTemplateId == template.id) null else template.id) },
                )
            }
            item("bills.header") { SectionHeader("Bills", "recurring.section.bills") }
            if (!state.isLoading && state.billTemplates.isEmpty() && state.templates.isNotEmpty()) {
                item("bills.empty") { Text("No bills yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("recurring.bills.empty")) }
            }
            items(state.billTemplates, key = RecurringTemplate::id) { template ->
                IncomeBillRow(template, state.categories.firstOrNull { it.id == template.categoryId }?.name, false, state.mainIncomeTemplateId, state.currency,
                    onEdit = { templateToEdit = template; showEditor = true },
                    onDelete = { templateToDelete = template },
                    onMainIncome = {},
                )
            }
        }
    }

    if (showEditor) {
        AddEditRecurringSheet(
            template = templateToEdit,
            categories = state.categories,
            currency = state.currency,
            isSaving = state.isWriting,
            error = state.error,
            onDismiss = { showEditor = false; templateToEdit = null },
            onSave = { draft ->
                if (templateToEdit == null) viewModel.create(draft.toCreateRequest())
                else viewModel.update(requireNotNull(templateToEdit).id, draft.toUpdateRequest())
            },
        )
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete recurring item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Delete ${template.description}?")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurring.delete.error")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(template.id) }, enabled = !state.isWriting, modifier = Modifier.testTag("recurring.delete.confirm")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { templateToDelete = null }, modifier = Modifier.testTag("recurring.delete.cancel")) { Text("Cancel") } },
        )
    }

    if (showGenerate) {
        GenerateMonthSheet(
            templates = state.templates,
            currency = state.currency,
            month = generateMonth,
            isGenerating = state.isWriting,
            error = state.error,
            onMonthChange = { generateMonth = it },
            onDismiss = { showGenerate = false },
            onGenerate = viewModel::generate,
        )
    }
}

@Composable
private fun SectionHeader(title: String, tag: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs).testTag(tag))
}

@Composable
private fun IncomeBillRow(
    template: RecurringTemplate,
    categoryName: String?,
    isIncome: Boolean,
    mainIncomeTemplateId: String?,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMainIncome: () -> Unit,
) {
    val finance = LocalFinanceColors.current
    val amount = Money.format(template.amount, currency)
    val canEdit = template.type == "fixed"
    Column(Modifier.fillMaxWidth().testTag("recurring.row.${template.id}")) {
        ListItem(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(template.description, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (template.id == mainIncomeTemplateId) Text("Main income", color = finance.income, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("recurring.mainIncome.badge.${template.id}"))
                    if (!template.isActive) Text("Paused", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("recurring.paused.${template.id}"))
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listOfNotNull(categoryName, frequencyText(template)).joinToString(" · "))
                    Text(nextDateLabel(template), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!canEdit) Text("Read-only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("${if (template.amountVaries) "≈ " else ""}$amount", color = if (isIncome) finance.income else finance.expense, style = MaterialTheme.typography.titleSmall)
                    if (isIncome && (template.id == mainIncomeTemplateId || canEdit)) {
                        TextButton(onClick = onMainIncome, enabled = template.id == mainIncomeTemplateId || (canEdit && template.isActive), modifier = Modifier.testTag("recurring.mainIncome.${template.id}")) { Text(if (template.id == mainIncomeTemplateId) "★" else "☆") }
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(start = Spacing.s, bottom = Spacing.xs), horizontalArrangement = Arrangement.End) {
            if (canEdit) TextButton(onClick = onEdit, modifier = Modifier.testTag("recurring.edit.${template.id}")) { Text("Edit") }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("recurring.delete.${template.id}")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()
    }
}

private fun frequencyText(template: RecurringTemplate): String {
    return when (template.frequency) {
        "bi-weekly" -> "Bi-weekly"
        "semi-monthly" -> "Semi-monthly"
        "semi-annually" -> "Semi-annually"
        else -> template.frequency.replaceFirstChar(Char::uppercase)
    }
}

private fun nextDateLabel(template: RecurringTemplate): String {
    if (!template.isActive) return "No upcoming date"
    val today = LocalDate.now()
    val startMonth = YearMonth.from(today)
    val next = (0L..24L).asSequence()
        .flatMap { RecurrenceMath.occurrencesInMonth(template, startMonth.plusMonths(it)).asSequence() }
        .firstOrNull { !it.isBefore(today) }
    return next?.let { "Next ${it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}" } ?: "No upcoming date"
}

private fun RecurringDraft.toCreateRequest() = CreateRecurringTemplateRequest(
    categoryId = requireNotNull(categoryId),
    description = description.trim(),
    amount = requireNotNull(amount).toDouble(),
    frequency = frequency,
    firstOccurrenceDate = firstOccurrenceDate.toString(),
    occurrencesRemaining = occurrencesRemaining?.let { JsonPrimitive(it) } ?: JsonNull,
    secondOccurrenceDay = if (frequency == "semi-monthly") secondOccurrenceDay?.let { JsonPrimitive(it) } ?: JsonNull else JsonNull,
    repeatInterval = requireNotNull(repeatInterval),
    amountVaries = amountVaries,
    isActive = isActive,
)

private fun RecurringDraft.toUpdateRequest() = UpdateRecurringTemplateRequest(
    categoryId = requireNotNull(categoryId),
    description = description.trim(),
    amount = requireNotNull(amount).toDouble(),
    type = "fixed",
    frequency = frequency,
    firstOccurrenceDate = firstOccurrenceDate.toString(),
    // The Never value must be JSON null on update so the backend clears the old cap.
    occurrencesRemaining = occurrencesRemaining?.let { JsonPrimitive(it) } ?: JsonNull,
    isActive = isActive,
    secondOccurrenceDay = if (frequency == "semi-monthly") secondOccurrenceDay?.let { JsonPrimitive(it) } ?: JsonNull else JsonNull,
    repeatInterval = requireNotNull(repeatInterval),
    pausedAt = if (isActive) null else Instant.now().toString(),
    amountVaries = amountVaries,
)
