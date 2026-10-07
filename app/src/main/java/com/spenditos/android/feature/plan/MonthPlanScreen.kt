package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.w

@Composable
fun MonthPlanScreen(
    addOneTimeRequest: Int = 0,
    onAddOneTimeRequestHandled: (Int) -> Unit = {},
    onOpenIncomeAndBills: () -> Unit = {},
) {
    val viewModel: MonthPlanViewModel = hiltViewModel()
    MonthPlanContent(viewModel, addOneTimeRequest, onAddOneTimeRequestHandled, onOpenIncomeAndBills)
}

private enum class ConfirmAction { APPLY, DISCARD }

@Composable
private fun PlanTintCard(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        modifier.fillMaxWidth().clip(shape).background(color, shape),
        content = content,
    )
}

@Composable
private fun MonthPlanContent(
    viewModel: MonthPlanViewModel,
    addOneTimeRequest: Int,
    onAddOneTimeRequestHandled: (Int) -> Unit,
    onOpenIncomeAndBills: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingOneTime by remember { mutableStateOf(false) }
    var changingRow by remember { mutableStateOf<MonthPlanRecurringRow?>(null) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    val rows = viewModel.rows(state)
    val summary = viewModel.summary(state)

    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(addOneTimeRequest) {
        if (addOneTimeRequest > 0) {
            addingOneTime = true
            onAddOneTimeRequestHandled(addOneTimeRequest)
        }
    }

    Column(Modifier.fillMaxSize().testTag("monthPlan.screen")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MonthPicker(state, viewModel)

            if (!state.isOnline) {
                GlassCard(Modifier.testTag("monthPlan.offline")) {
                    Text("You're offline. Apply and discard need a connection.", modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.isPast) {
                Text(
                    "This month has passed. The draft can be discarded, not applied.",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                        .background(IosColors.textSecondary.copy(alpha = 0.06f)).padding(Spacing.m),
                    style = IosText.subheadline,
                    color = IosColors.textSecondary,
                )
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
                    IncomeSection(
                        rows.filter { it.isIncome },
                        viewModel.transactionRows(isIncome = true, current = state),
                        state,
                        viewModel,
                        onOpenIncomeAndBills = onOpenIncomeAndBills,
                    )
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
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(IosColors.textSecondary.copy(alpha = 0.08f))
                    .clickable(enabled = state.canGoToPreviousMonth && !state.isLoading && !state.isApplying) { viewModel.moveMonth(-1) }
                    .testTag("monthPlan.previousMonth"),
                contentAlignment = Alignment.Center,
            ) { Icon(SfSymbols.icon("chevron.left"), contentDescription = "Previous month", tint = IosColors.textPrimary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0])),
                    style = IosText.headline,
                    color = IosColors.textPrimary,
                )
                if (state.draftChangeCount > 0) {
                    Text(
                        "Draft · ${state.draftChangeCount} ${if (state.draftChangeCount == 1) "change" else "changes"}",
                        modifier = Modifier.background(IosColors.spendOrange.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp).testTag("monthPlan.draftCount"),
                        style = IosText.caption2.w(FontWeight.Bold),
                        color = IosColors.spendOrange,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(IosColors.textSecondary.copy(alpha = 0.08f))
                    .clickable(enabled = state.canGoToNextMonth && !state.isLoading && !state.isApplying) { viewModel.moveMonth(1) }
                    .testTag("monthPlan.nextMonth"),
                contentAlignment = Alignment.Center,
            ) { Icon(SfSymbols.icon("chevron.right"), contentDescription = "Next month", tint = IosColors.textPrimary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.loading")) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = IosColors.spendOrange, strokeWidth = 2.dp)
            Text("Loading your plan…", style = IosText.subheadline, color = IosColors.textPrimary)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.error")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(SfSymbols.icon("calendar.badge.exclamationmark"), contentDescription = null, tint = IosColors.textSecondary)
                Text("Plan unavailable", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            }
            Text(message, style = IosText.subheadline, color = IosColors.textSecondary)
            TextButton(onClick = onRetry, modifier = Modifier.testTag("monthPlan.retryLoad"), contentPadding = PaddingValues(0.dp)) {
                Text("Try again", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.spendOrange)
            }
        }
    }
}

@Composable
private fun ErrorInline(message: String, onRetry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.inlineError")) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(message, Modifier.weight(1f), style = IosText.subheadline, color = IosColors.expense)
            TextButton(onClick = onRetry, modifier = Modifier.testTag("monthPlan.retryRefresh"), contentPadding = PaddingValues(0.dp)) {
                Text("Retry", style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.spendOrange)
            }
        }
    }
}

@Composable
private fun EmptyPlanCard() {
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.empty")) {
        Text("Nothing scheduled this month yet.", modifier = Modifier.padding(Spacing.m), style = IosText.subheadline, color = IosColors.textSecondary)
    }
}

@Composable
private fun SummaryCard(summary: MonthPlanSummary, currency: String, hasDraft: Boolean) {
    val finance = LocalFinanceColors.current
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.summary")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                if (hasDraft) "Left at month end · with this draft" else "Left at month end",
                style = IosText.caption.w(FontWeight.Bold),
                color = IosColors.textSecondary,
            )
            Text(
                Money.format(summary.draftLeft.toDouble(), currency),
                style = IosText.title2.w(FontWeight.Bold),
                color = if (summary.draftLeft.signum() < 0) finance.expense else IosColors.textPrimary,
                modifier = Modifier.testTag("monthPlan.summary.left"),
            )
            if (summary.draftLeft.signum() < 0) {
                Text(
                    "That's more than you'll have. Skip something, or move a one-time item to a later month.",
                    style = IosText.caption,
                    color = IosColors.expense,
                )
            }
            if (hasDraft) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(IosColors.textSecondary.copy(alpha = 0.06f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SummaryLine(
                        "In your records now",
                        "≈ ${Money.format(summary.recordsLeft.toDouble(), currency)}",
                        IosColors.textPrimary,
                    )
                    val delta = summary.draftLeft.subtract(summary.recordsLeft)
                    SummaryLine(
                        "Draft",
                        (if (delta.signum() >= 0) "+" else "−") + Money.format(delta.abs().toDouble(), currency),
                        if (delta.signum() >= 0) finance.income else finance.expense,
                    )
                }
            }
            HorizontalDivider(color = IosColors.separator)
            SummaryLine("Income", "+${Money.format(summary.draftIncome.toDouble(), currency)}", finance.income)
            SummaryLine("Bills", "−${Money.format(summary.draftBills.toDouble(), currency)}", finance.expense)
            SummaryLine("One-time", "−${Money.format(summary.draftOneTime.toDouble(), currency)}", finance.expense)
            Text("Your records change only when you apply.", style = IosText.caption2, color = IosColors.textSecondary)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = IosColors.textPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = IosText.caption, color = IosColors.textSecondary)
        Text(value, style = IosText.caption.w(FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun DraftActions(state: MonthPlanUiState, viewModel: MonthPlanViewModel, onConfirm: (ConfirmAction) -> Unit) {
    val finance = LocalFinanceColors.current
    val applyFailed = state.applyMessage?.contains("Try again") == true
    PlanTintCard(Modifier.fillMaxWidth().testTag("monthPlan.draftActions"), IosColors.spendOrange.copy(alpha = 0.10f)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (state.draftChangeCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val changeLabel = if (state.draftChangeCount == 1) "1 change" else "${state.draftChangeCount} changes"
                        Text(
                            if (applyFailed) "$changeLabel didn't apply" else "$changeLabel in your draft",
                            style = IosText.subheadline.w(FontWeight.Bold),
                            color = IosColors.textPrimary,
                        )
                        Text(
                            if (applyFailed) "The rest is saved. Try again." else "Nothing is final until you apply.",
                            style = IosText.caption,
                            color = IosColors.textSecondary,
                        )
                    }
                    Text(
                        "Discard",
                        modifier = Modifier.testTag("monthPlan.discard")
                            .clickable(enabled = !state.isApplying && state.isOnline, role = Role.Button) { onConfirm(ConfirmAction.DISCARD) },
                        style = IosText.subheadline.w(FontWeight.SemiBold),
                        color = IosColors.textSecondary,
                    )
                    if (!state.isPast) {
                        Box(
                            Modifier.heightIn(min = 36.dp).clip(CircleShape)
                                .background(if (state.isApplying || !state.isOnline) IosColors.spendOrange.copy(alpha = 0.45f) else IosColors.spendOrange)
                                .clickable(enabled = !state.isApplying && state.isOnline, role = Role.Button) { onConfirm(ConfirmAction.APPLY) }
                                .testTag("monthPlan.apply")
                                .padding(horizontal = Spacing.m),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (state.isApplying) "Applying…" else if (applyFailed) "Retry" else "Apply",
                                style = IosText.subheadline.w(FontWeight.Bold),
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
            }
            state.applyMessage?.let { Text(it, color = if (it.startsWith("Couldn't")) finance.expense else IosColors.textSecondary, style = IosText.caption) }
            state.draftMessage?.let { Text(it, color = IosColors.textSecondary, style = IosText.caption) }
            if (state.isSavingDraft) Text("Saving draft…", style = IosText.caption, color = IosColors.textSecondary)
        }
    }
}

@Composable
private fun IncomeSection(
    rows: List<MonthPlanRecurringRow>,
    transactions: List<MonthPlanTransactionRow>,
    state: MonthPlanUiState,
    viewModel: MonthPlanViewModel,
    onOpenIncomeAndBills: () -> Unit,
) {
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.income")) {
        PlanSectionTitle("Income")
        GlassCard {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (rows.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("No income scheduled yet.", Modifier.weight(1f), style = IosText.subheadline, color = IosColors.textSecondary)
                        IncomeBillsLink(onOpenIncomeAndBills)
                    }
                }
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.description, Modifier.weight(1f), style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1)
                        Text(
                            "+${Money.format(row.total.toDouble(), state.currency)}",
                            style = IosText.subheadline.w(FontWeight.SemiBold),
                            color = finance.income,
                        )
                    }
                }
                if (rows.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IncomeBillsLink(onOpenIncomeAndBills)
                    }
                }
                if (transactions.isNotEmpty()) {
                    Text("In your records", Modifier.padding(top = Spacing.s, bottom = Spacing.xs), style = IosText.caption, color = IosColors.textSecondary)
                    transactions.forEach { RecordedPlanEntry(it, state, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun IncomeBillsLink(onClick: () -> Unit) {
    Text(
        "Change in Income & bills",
        Modifier.clickable(role = Role.Button, onClick = onClick).testTag("monthPlan.openIncomeAndBills"),
        style = IosText.caption.w(FontWeight.Bold),
        color = IosColors.spendOrange,
    )
}

@Composable
private fun BillsSection(rows: List<MonthPlanRecurringRow>, transactions: List<MonthPlanTransactionRow>, state: MonthPlanUiState, viewModel: MonthPlanViewModel, onChange: (MonthPlanRecurringRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.bills")) {
        PlanSectionTitle("Bills")
        if (rows.isEmpty()) {
            Text(
                "No bills scheduled yet.",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                    .background(IosColors.textSecondary.copy(alpha = 0.04f)).padding(Spacing.m),
                style = IosText.subheadline,
                color = IosColors.textSecondary,
            )
        }
        rows.forEach { row -> RecurringPlanRow(row, state, viewModel, onChange, isIncome = false, groupedIncome = false) }
        if (transactions.isNotEmpty()) {
            PlanSectionTitle("In your records")
            transactions.forEach { RecordedPlanEntry(it, state, viewModel) }
        }
    }
}

@Composable
private fun PlanSectionTitle(title: String) {
    Text(title, style = IosText.caption.w(FontWeight.Bold), color = IosColors.textSecondary)
}

@Composable
private fun RecordedPlanEntry(row: MonthPlanTransactionRow, state: MonthPlanUiState, viewModel: MonthPlanViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val finance = LocalFinanceColors.current
    GlassCard(Modifier.fillMaxWidth().testTag("monthPlan.transaction.${row.transaction.id}")) {
        IosRow(
            title = row.transaction.description,
            subtitle = runCatching { LocalDate.parse(row.transaction.date.take(10)).format(DateTimeFormatter.ofPattern("MMM d", locale)) }.getOrDefault(row.transaction.date.take(10)),
            icon = SfSymbols.icon("receipt"),
            iconTint = if (row.isIncome) finance.income else finance.expense,
            divider = false,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (row.isDeleted) DraftBadge()
                    Text(Money.signed(row.transaction.amount, row.isIncome, state.currency), style = IosText.subheadline.w(FontWeight.SemiBold), color = if (row.isIncome) finance.income else finance.expense)
                    if (!state.isPast) {
                        TextButton(
                            onClick = {
                                if (row.isDeleted) viewModel.undoTransactionDelete(row.transaction.id)
                                else viewModel.stageTransactionDelete(row.transaction.id)
                            },
                            enabled = !state.isApplying,
                            modifier = Modifier.testTag("monthPlan.transactionAction.${row.transaction.id}"),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(if (row.isDeleted) "Undo" else "Remove", style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary) }
                    }
                }
            },
        )
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
    groupedIncome: Boolean,
) {
    val finance = LocalFinanceColors.current
    val rowColor = when {
        groupedIncome -> androidx.compose.ui.graphics.Color.Transparent
        row.isSkipped -> IosColors.textSecondary.copy(alpha = 0.06f)
        row.hasAmountChange -> IosColors.spendOrange.copy(alpha = 0.06f)
        else -> IosColors.textSecondary.copy(alpha = 0.04f)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(rowColor)
            .testTag("monthPlan.row.${row.templateId}")
            .padding(if (groupedIncome) PaddingValues(vertical = 7.dp) else PaddingValues(12.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.description,
                Modifier.weight(1f),
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = if (row.isSkipped) IosColors.textSecondary else IosColors.textPrimary,
                textDecoration = if (row.isSkipped) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
            )
            if (row.amountVaries) Text("≈", style = IosText.subheadline, color = IosColors.textSecondary)
            if (row.hasDraftChange) {
                Spacer(Modifier.width(6.dp))
                DraftBadge()
            }
            Spacer(Modifier.width(Spacing.s))
            Text(
                Money.signed(row.total.toDouble(), isIncome, state.currency),
                style = IosText.subheadline.w(if (isIncome) FontWeight.SemiBold else FontWeight.Bold),
                color = if (row.isSkipped) IosColors.textSecondary else if (isIncome) finance.income else finance.expense,
                textDecoration = if (row.isSkipped) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
        val dates = if (row.occurrenceCount > 1) "${row.occurrenceCount} × ${Money.format(row.usualAmount.toDouble(), state.currency)}"
        else row.dates.firstOrNull()?.format(DateTimeFormatter.ofPattern("MMM d", LocalConfiguration.current.locales[0])).orEmpty()
        Text(dates, style = IosText.caption, color = IosColors.textSecondary)
        if (!state.isPast) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PlanTextAction(
                    if (row.isSkipped) "Undo skip" else "Skip",
                    "monthPlan.skip.${row.templateId}",
                    enabled = !state.isApplying,
                    onClick = { viewModel.stageSkip(row.templateId, row.dates, skipped = !row.isSkipped) },
                )
                if (!row.isSkipped) {
                    PlanTextAction(
                        "Change",
                        "monthPlan.change.${row.templateId}",
                        enabled = !state.isApplying,
                        onClick = { onChange(row) },
                    )
                    if (row.hasAmountChange) {
                        PlanTextAction(
                            "Reset",
                            "monthPlan.reset.${row.templateId}",
                            enabled = !state.isApplying,
                            onClick = { viewModel.resetAmountChange(row.templateId) },
                        )
                    }
                }
                PlanTextAction(
                    when {
                        row.isLogged && row.hasAmountChange -> "Logged in draft"
                        row.isLogged -> "Undo log"
                        isIncome -> "Log this month"
                        else -> "Log as paid"
                    },
                    "monthPlan.log.${row.templateId}",
                    enabled = !state.isApplying && !(row.isLogged && row.hasAmountChange),
                    onClick = { viewModel.stageLog(row.templateId, row.dates, logged = !row.isLogged) },
                )
            }
        }
    }
}

@Composable
private fun DraftBadge() {
    Text(
        "Draft",
        Modifier.background(IosColors.spendOrange.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = IosText.caption2.w(FontWeight.Bold),
        color = IosColors.spendOrange,
    )
}

@Composable
private fun PlanTextAction(
    text: String,
    tag: String,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color = IosColors.textSecondary,
    onClick: () -> Unit,
) {
    Text(
        text,
        Modifier.testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        style = IosText.caption.w(FontWeight.SemiBold),
        color = if (enabled) color else color.copy(alpha = 0.35f),
    )
}

@Composable
private fun OneTimeSection(state: MonthPlanUiState, viewModel: MonthPlanViewModel, onAdd: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().testTag("monthPlan.oneTime")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanSectionTitle("One-time")
            Spacer(Modifier.weight(1f))
            if (!state.isPast) {
                Row(
                    Modifier.testTag("monthPlan.addOneTime")
                        .clickable(enabled = !state.isApplying, role = Role.Button, onClick = onAdd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(SfSymbols.icon("plus"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(14.dp))
                    Text("Add one-time item", style = IosText.caption.w(FontWeight.Bold), color = IosColors.spendOrange)
                }
            }
        }
        GlassCard {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (state.draft.entries.isEmpty()) {
                    Text("No one-time items in this draft.", Modifier.padding(vertical = 10.dp), style = IosText.subheadline, color = IosColors.textSecondary)
                } else {
                    state.draft.entries.forEach { entry ->
                        val isIncome = state.snapshot?.categories?.firstOrNull { it.id == entry.categoryId }?.isIncome ?: false
                        Row(
                            Modifier.fillMaxWidth().testTag("monthPlan.oneTimeRow.${entry.id}").padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.description, style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1)
                                    DraftBadge()
                                }
                                Text(
                                    runCatching { LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("MMM d", locale)) }.getOrDefault(entry.date),
                                    style = IosText.caption,
                                    color = IosColors.textSecondary,
                                )
                            }
                            Text(
                                Money.signed(entry.amount, isIncome, state.currency),
                                style = IosText.subheadline.w(FontWeight.SemiBold),
                                color = if (isIncome) finance.income else finance.expense,
                            )
                            if (!state.isPast) {
                                Box(
                                    Modifier.size(30.dp).testTag("monthPlan.remove.${entry.id}")
                                        .clickable(enabled = !state.isApplying, role = Role.Button) { viewModel.removeOneTime(entry.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        SfSymbols.icon("xmark.circle.fill"),
                                        contentDescription = "Remove ${entry.description}",
                                        tint = IosColors.textSecondary,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
