package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun MonthPlanScreen() {
    val viewModel: MonthPlanViewModel = hiltViewModel()
    MonthPlanContent(viewModel)
}

private enum class ConfirmAction { APPLY, DISCARD }

@Composable
private fun MonthPlanContent(viewModel: MonthPlanViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingOneTime by remember { mutableStateOf(false) }
    var changingRow by remember { mutableStateOf<MonthPlanRecurringRow?>(null) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    val rows = viewModel.rows(state)
    val summary = viewModel.summary(state)

    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Month plan") },
            )
        },
        modifier = Modifier.testTag("monthPlan.screen"),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MonthPicker(state, viewModel)

            if (!state.isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("monthPlan.offline"),
                ) {
                    Text("You're offline. Apply and discard need a connection.", modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.isPast) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                    Text("This month has passed. You can discard its draft.", modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
                }
            }

            when {
                state.isLoading && state.snapshot == null -> LoadingCard()
                state.snapshot == null && state.error != null -> ErrorCard(state.error!!, onRetry = viewModel::refresh)
                state.snapshot != null -> {
                    state.error?.let { ErrorInline(it, onRetry = viewModel::refresh) }
                    if (summary != null) SummaryCard(summary, state.currency, state.draftChangeCount > 0)
                    if (state.draftChangeCount > 0 || state.applyMessage != null || state.draftMessage != null) {
                        DraftActions(state, viewModel, onConfirm = { confirmAction = it })
                    }
                    if (rows.isEmpty() && state.draft.entries.isEmpty()) EmptyPlanCard()
                    IncomeSection(rows.filter { it.isIncome }, viewModel.transactionRows(isIncome = true, current = state), state, viewModel, onChange = { changingRow = it })
                    BillsSection(rows.filterNot { it.isIncome }, viewModel.transactionRows(isIncome = false, current = state), state, viewModel, onChange = { changingRow = it })
                    OneTimeSection(
                        state = state,
                        viewModel = viewModel,
                        onAdd = { addingOneTime = true },
                    )
                }
                state.isLoading -> LoadingCard()
                else -> ErrorCard("Couldn't load this month. Try again.", onRetry = viewModel::refresh)
            }
        }
    }

    if (addingOneTime && !state.isPast) {
        AddOneTimeEntrySheet(
            month = state.month,
            categories = state.snapshot?.categories.orEmpty(),
            currency = state.currency,
            onDismiss = { addingOneTime = false },
            onAdd = viewModel::addOneTime,
        )
    }
    changingRow?.let { row ->
        ChangeAmountSheet(
            row = row,
            currency = state.currency,
            onDismiss = { changingRow = null },
            onSave = { viewModel.stageAmountChange(row.templateId, row.dates, it) },
            onReset = { viewModel.resetAmountChange(row.templateId) },
        )
    }

    when (confirmAction) {
        ConfirmAction.APPLY -> AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Apply this plan?") },
            text = { Text("This updates your records for ${state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0]))}.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmAction = null; viewModel.applyAllFromScreen() },
                    modifier = Modifier.testTag("monthPlan.confirmApply"),
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }, modifier = Modifier.testTag("monthPlan.cancelApply")) { Text("Cancel") } },
        )
        ConfirmAction.DISCARD -> AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Discard this draft?") },
            text = { Text("These changes will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmAction = null; viewModel.discardFromScreen() },
                    modifier = Modifier.testTag("monthPlan.confirmDiscard"),
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }, modifier = Modifier.testTag("monthPlan.cancelDiscard")) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun MonthPicker(state: MonthPlanUiState, viewModel: MonthPlanViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { viewModel.moveMonth(-1) },
                enabled = state.canGoToPreviousMonth && !state.isLoading && !state.isApplying,
                modifier = Modifier.testTag("monthPlan.previousMonth"),
            ) { Text("‹") }
            Spacer(Modifier.weight(1f))
            Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0])), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { viewModel.moveMonth(1) },
                enabled = state.canGoToNextMonth && !state.isLoading && !state.isApplying,
                modifier = Modifier.testTag("monthPlan.nextMonth"),
            ) { Text("›") }
        }
        if (state.draftChangeCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("monthPlan.draftCount"),
            ) {
                Text("Draft · ${state.draftChangeCount} ${if (state.draftChangeCount == 1) "change" else "changes"}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(Modifier.fillMaxWidth().testTag("monthPlan.loading")) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CircularProgressIndicator()
            Text("Loading your plan…")
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("monthPlan.error")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("monthPlan.retryLoad")) { Text("Try again") }
        }
    }
}

@Composable
private fun ErrorInline(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("monthPlan.inlineError")) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, modifier = Modifier.testTag("monthPlan.retryRefresh")) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyPlanCard() {
    Card(Modifier.fillMaxWidth().testTag("monthPlan.empty")) {
        Text("Nothing scheduled this month yet.", modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryCard(summary: MonthPlanSummary, currency: String, hasDraft: Boolean) {
    val finance = LocalFinanceColors.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag("monthPlan.summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Left at month end", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(summary.draftLeft.toDouble(), currency),
                style = MaterialTheme.typography.headlineSmall,
                color = if (summary.draftLeft.signum() < 0) finance.expense else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("monthPlan.summary.left"),
            )
            if (hasDraft) {
                SummaryLine("In your records now", Money.format(summary.recordsLeft.toDouble(), currency))
                val delta = summary.delta
                val formattedDelta = (if (delta.signum() >= 0) "+" else "−") + Money.format(delta.abs().toDouble(), currency)
                SummaryLine("With this draft · $formattedDelta", Money.format(summary.draftLeft.toDouble(), currency))
            }
            Spacer(Modifier.height(2.dp))
            SummaryLine("Income", Money.format(summary.draftIncome.toDouble(), currency), finance.income)
            SummaryLine("Bills", Money.format(summary.draftBills.toDouble(), currency), finance.expense)
            SummaryLine("One-time", Money.format(summary.draftOneTime.toDouble(), currency), finance.expense)
            Text("Your records change only when you apply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun DraftActions(state: MonthPlanUiState, viewModel: MonthPlanViewModel, onConfirm: (ConfirmAction) -> Unit) {
    val finance = LocalFinanceColors.current
    Card(
        modifier = Modifier.fillMaxWidth().testTag("monthPlan.draftActions"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (state.draftChangeCount > 0) {
                Text("${state.draftChangeCount} changes in your draft", fontWeight = FontWeight.SemiBold)
                Text("Nothing is final until you apply.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onConfirm(ConfirmAction.DISCARD) },
                        enabled = !state.isApplying && state.isOnline,
                        modifier = Modifier.weight(1f).testTag("monthPlan.discard"),
                    ) { Text("Discard") }
                    Button(
                        onClick = { onConfirm(ConfirmAction.APPLY) },
                        enabled = !state.isApplying && state.isOnline && !state.isPast,
                        modifier = Modifier.weight(1f).testTag("monthPlan.apply"),
                    ) { Text(if (state.isApplying) "Applying…" else if (state.applyMessage?.contains("Try again") == true) "Retry" else "Apply") }
                }
            }
            state.applyMessage?.let { Text(it, color = if (it.startsWith("Couldn't")) finance.expense else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            state.draftMessage?.let { Text(it, color = finance.warning, style = MaterialTheme.typography.bodySmall) }
            if (state.isSavingDraft) Text("Saving draft…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IncomeSection(rows: List<MonthPlanRecurringRow>, transactions: List<MonthPlanTransactionRow>, state: MonthPlanUiState, viewModel: MonthPlanViewModel, onChange: (MonthPlanRecurringRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.income")) {
        Text("Income", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) Text("No income scheduled yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { row -> RecurringPlanRow(row, state, viewModel, onChange, isIncome = true) }
        if (transactions.isNotEmpty()) {
            Text("In your records", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            transactions.forEach { RecordedPlanEntry(it, state, viewModel) }
        }
    }
}

@Composable
private fun BillsSection(rows: List<MonthPlanRecurringRow>, transactions: List<MonthPlanTransactionRow>, state: MonthPlanUiState, viewModel: MonthPlanViewModel, onChange: (MonthPlanRecurringRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.bills")) {
        Text("Bills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) Text("No bills scheduled yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { row -> RecurringPlanRow(row, state, viewModel, onChange, isIncome = false) }
        if (transactions.isNotEmpty()) {
            Text("In your records", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            transactions.forEach { RecordedPlanEntry(it, state, viewModel) }
        }
    }
}

@Composable
private fun RecordedPlanEntry(row: MonthPlanTransactionRow, state: MonthPlanUiState, viewModel: MonthPlanViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val finance = LocalFinanceColors.current
    Card(Modifier.fillMaxWidth().testTag("monthPlan.transaction.${row.transaction.id}")) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.transaction.description, fontWeight = FontWeight.Medium)
                    if (row.isDeleted) {
                        Spacer(Modifier.width(Spacing.s))
                        Text("Draft", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    runCatching { LocalDate.parse(row.transaction.date.take(10)).format(DateTimeFormatter.ofPattern("MMM d", locale)) }.getOrDefault(row.transaction.date.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.signed(row.transaction.amount, row.isIncome, state.currency), color = if (row.isIncome) finance.income else finance.expense)
            if (!state.isPast) {
                TextButton(
                    onClick = {
                        if (row.isDeleted) viewModel.undoTransactionDelete(row.transaction.id)
                        else viewModel.stageTransactionDelete(row.transaction.id)
                    },
                    enabled = !state.isApplying,
                    modifier = Modifier.testTag("monthPlan.transactionAction.${row.transaction.id}"),
                ) { Text(if (row.isDeleted) "Undo" else "Remove") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringPlanRow(
    row: MonthPlanRecurringRow,
    state: MonthPlanUiState,
    viewModel: MonthPlanViewModel,
    onChange: (MonthPlanRecurringRow) -> Unit,
    isIncome: Boolean,
) {
    val finance = LocalFinanceColors.current
    Card(Modifier.fillMaxWidth().testTag("monthPlan.row.${row.templateId}")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.description, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (row.amountVaries) Text("≈", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.hasDraftChange) {
                    Spacer(Modifier.width(Spacing.s))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                        Text("Draft", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    Money.signed(row.total.toDouble(), isIncome, state.currency),
                    color = if (isIncome) finance.income else finance.expense,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val dates = if (row.occurrenceCount == 1) row.dates.firstOrNull()?.format(DateTimeFormatter.ofPattern("MMM d", LocalConfiguration.current.locales[0])).orEmpty()
                else "${row.occurrenceCount} dates · ${Money.format(row.usualAmount.toDouble(), state.currency)} each"
            Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.isPast) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(
                        onClick = { viewModel.stageSkip(row.templateId, row.dates, skipped = !row.isSkipped) },
                        enabled = !state.isApplying,
                        modifier = Modifier.testTag("monthPlan.skip.${row.templateId}"),
                    ) { Text(if (row.isSkipped) "Undo skip" else "Skip this month") }
                    TextButton(
                        onClick = { onChange(row) },
                        enabled = !state.isApplying,
                        modifier = Modifier.testTag("monthPlan.change.${row.templateId}"),
                    ) { Text("Change amount") }
                    TextButton(
                        onClick = { viewModel.stageLog(row.templateId, row.dates, logged = !row.isLogged) },
                        enabled = !state.isApplying && !(row.isLogged && row.hasAmountChange),
                        modifier = Modifier.testTag("monthPlan.log.${row.templateId}"),
                    ) {
                        Text(
                            when {
                                row.isLogged && row.hasAmountChange -> "Logged in draft"
                                row.isLogged -> "Undo log"
                                isIncome -> "Log this month"
                                else -> "Log as paid"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneTimeSection(state: MonthPlanUiState, viewModel: MonthPlanViewModel, onAdd: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("One-time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!state.isPast) TextButton(onClick = onAdd, enabled = !state.isApplying, modifier = Modifier.testTag("monthPlan.addOneTime")) { Text("Add") }
        }
        if (state.draft.entries.isEmpty()) {
            Text("No one-time items in this draft.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.draft.entries.forEach { entry ->
                val isIncome = state.snapshot?.categories?.firstOrNull { it.id == entry.categoryId }?.isIncome ?: false
                Card(Modifier.fillMaxWidth().testTag("monthPlan.oneTimeRow.${entry.id}")) {
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.description, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(Spacing.s))
                                Text("Draft", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                runCatching { LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("MMM d", locale)) }.getOrDefault(entry.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.signed(entry.amount, isIncome, state.currency), color = if (isIncome) finance.income else finance.expense)
                        if (!state.isPast) {
                            TextButton(onClick = { viewModel.removeOneTime(entry.id) }, enabled = !state.isApplying, modifier = Modifier.testTag("monthPlan.remove.${entry.id}")) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
