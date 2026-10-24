package com.spenditos.android.feature.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.transactions.BillCandidate
import com.spenditos.android.data.transactions.RecurrencePreset
import com.spenditos.android.ui.components.CategoryIcon
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.DiscardChangesSheet
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDismisser
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.components.rememberKeyboardSettler
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.spenditos.android.data.quota.QuotaCopy
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    onVoice: (() -> Unit)? = null,
    onSentence: (() -> Unit)? = null,
    onScanReceipt: (() -> Unit)? = null,
    /** Opens the Pro screen; null where purchases are not available. */
    onOpenPro: (() -> Unit)? = null,
    prefill: AddTransactionPrefill? = null,
    editTarget: EditTarget? = null,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val amountFocusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNextDuePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var categorySearchText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(editTarget) { editTarget?.let(viewModel::startEdit) }
    LaunchedEffect(prefill, state.categories.isNotEmpty(), editTarget) {
        if (editTarget == null && prefill != null &&
            (state.categories.isNotEmpty() || (prefill.categoryId == null && prefill.categoryHint == null))
        ) {
            viewModel.applyPrefill(prefill)
        }
    }
    LaunchedEffect(editTarget, prefill, state.assistedEntrySource) {
        if (editTarget == null && prefill == null && state.assistedEntrySource == null) {
            amountFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.showValidation, state.amountMissing) {
        if (state.showValidation && state.amountMissing) amountFocusRequester.requestFocus()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddTransactionEvent.Done -> onDone(event.message)
                AddTransactionEvent.SavedAndContinue -> amountFocusRequester.requestFocus()
                AddTransactionEvent.CategoryCreated -> showAddCategory = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (state.hasUnsavedChanges) showDiscardConfirmation = true else onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                Modifier.padding(top = Spacing.s).size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.06f)),
            )
        },
    ) {
        // The sheet is its own window with its own focus manager, so everything that closes the
        // keyboard is created here, inside its content, not in the outer scope.
        val dismissKeyboard = rememberKeyboardDismisser()
        val settleKeyboard = rememberKeyboardSettler()
        DismissKeyboardWhenInactive()
        // Pickers, sheets and dialogs that code opens (paywall, async results) have no tap on the
        // form to close the keyboard first.
        DismissKeyboardWhen(showDatePicker)
        DismissKeyboardWhen(showCategoryPicker)
        DismissKeyboardWhen(showAddCategory)
        DismissKeyboardWhen(showNextDuePicker)
        DismissKeyboardWhen(showDiscardConfirmation)
        DismissKeyboardWhen(showDeleteConfirmation)
        DismissKeyboardWhen(state.billChoices.isNotEmpty())
        DismissKeyboardWhen(state.existingBill != null)
        Column(Modifier.fillMaxWidth().fillMaxHeight().dismissKeyboardOnTapOutside()) {
            QuickAddHeader(
                type = state.type,
                onTypeChange = viewModel::onTypeChange,
                onClose = {
                    if (state.hasUnsavedChanges) showDiscardConfirmation = true else onDismiss()
                },
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().dismissKeyboardOnScroll().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (WindowInsets.isImeVisible) Spacing.xs else Spacing.l,
                ),
            ) {
                state.error?.let { message ->
                    ErrorBanner(
                        message = message,
                        modifier = Modifier.testTag("add.error"),
                        onRetry = viewModel::retry,
                        onDismiss = viewModel::dismissError,
                    )
                }
                state.assistedEntrySource?.let { AssistedEntryBanner(it) }
                if (state.smartEntriesUsed) SmartEntriesUsedBanner(onSeePro = onOpenPro?.let { open -> { dismissKeyboard(); open() } })
                state.undo?.let { undo ->
                    UndoBanner(
                        description = undo.description,
                        amount = undo.amount,
                        currency = state.currency,
                        addedCount = state.addedCount,
                        onUndo = viewModel::undo,
                        modifier = Modifier.testTag("quick-add-undo"),
                    )
                }
                HeroAmountField(
                    value = state.amountText,
                    currency = state.currency,
                    tint = if (state.type == EntryType.INCOME) IosColors.income else IosColors.expense,
                    missing = state.amountMissing,
                    enabled = !state.paymentSaved,
                    keyboardVisible = WindowInsets.isImeVisible,
                    focusRequester = amountFocusRequester,
                    onValueChange = viewModel::onAmountChange,
                    modifier = Modifier.testTag("add.amount"),
                )
                DescriptionField(
                    value = state.description,
                    enabled = !state.paymentSaved,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.testTag("add.description"),
                )
                CategorySection(
                    state = state,
                    onSelect = viewModel::onCategorySelect,
                    // iOS: with no categories of this kind, the chip opens AddCategorySheet instead.
                    onMore = { if (state.visibleCategories.isEmpty()) showAddCategory = true else showCategoryPicker = true },
                )
                DateChoices(
                    date = state.date,
                    onToday = { viewModel.onDateChange(LocalDate.now()) },
                    onYesterday = { viewModel.onDateChange(LocalDate.now().minusDays(1)) },
                    // Closing the keyboard moves the chips, so the dialog opens only once the form
                    // has settled (iOS anchored its popover to the old chip position otherwise).
                    onPickDate = { scope.launch { settleKeyboard(); showDatePicker = true } },
                )
                if (!state.isEditMode) {
                    RecurringSection(
                        state = state,
                        onRecurringChange = viewModel::onMakeRecurringChange,
                        onPresetChange = viewModel::onRecurrencePresetChange,
                        onPickNextDue = { showNextDuePicker = true },
                    )
                }
                if (state.type == EntryType.EXPENSE) {
                    CompactMoodPicker(
                        mood = state.mood,
                        onMoodChange = viewModel::onMoodChange,
                        modifier = Modifier.testTag("quick-add-mood"),
                    )
                }
                if (state.isEditMode) {
                    DeleteTransactionButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.testTag("quick-add-delete"),
                    )
                }
            }

            QuickAddBottomBar(
                state = state,
                onVoice = onVoice?.let { open -> { dismissKeyboard(); open() } },
                onSentence = onSentence?.let { open -> { dismissKeyboard(); open() } },
                onScanReceipt = onScanReceipt?.let { open -> { dismissKeyboard(); open() } },
                onSave = { viewModel.save() },
                onSaveAndContinue = { viewModel.save(andContinue = true) },
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
            )
        }
    }

    if (showAddCategory) {
        com.spenditos.android.feature.settings.categories.AddEditCategorySheet(
            category = null,
            initialIsIncome = state.type == EntryType.INCOME,
            lockedType = state.type == EntryType.INCOME,
            isSaving = state.isSavingCategory,
            error = state.categoryError,
            onDismiss = { showAddCategory = false; viewModel.dismissCategoryError() },
            onSave = viewModel::createCategory,
        )
    }

    if (showCategoryPicker) {
        val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val matching = state.visibleCategories.filter {
            it.name.contains(categorySearchText.trim(), ignoreCase = true)
        }
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = pickerState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            DismissKeyboardWhenInactive()
            Column(
                Modifier.fillMaxWidth().fillMaxHeight().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                    .padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(64.dp))
                    Text(
                        "Select Category",
                        style = IosText.headline,
                        color = IosColors.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { showCategoryPicker = false }, modifier = Modifier.width(64.dp)) {
                        Text("Done", style = IosText.body, color = IosColors.spendOrange)
                    }
                }
                CategorySearchField(
                    value = categorySearchText,
                    onValueChange = { categorySearchText = it },
                    modifier = Modifier.testTag("add.category.search"),
                )
                if (matching.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            SfSymbols.icon("square.grid.2x2"),
                            contentDescription = null,
                            tint = IosColors.textTertiary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(Spacing.m))
                        Text("No categories found", style = IosText.body, color = IosColors.textSecondary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        items(matching, key = { it.id }) { category ->
                            CategoryGridCell(
                                category = category,
                                selected = state.categoryId == category.id,
                                onClick = {
                                    viewModel.onCategorySelect(category.id)
                                    showCategoryPicker = false
                                    categorySearchText = ""
                                },
                                modifier = Modifier.testTag("add.category"),
                            )
                        }
                    }
                }
                // iOS CategoryPickerView: create a category when the search has no exact match.
                val query = categorySearchText.trim()
                if (state.visibleCategories.none { it.name.equals(query, ignoreCase = true) }) {
                    val kind = if (state.type == EntryType.INCOME) "Income" else "Expense"
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                            .background(IosColors.spendOrange.copy(alpha = 0.08f))
                            .border(1.dp, IosColors.spendOrange.copy(alpha = 0.2f), RoundedCornerShape(Radius.m))
                            .clickable(role = Role.Button) {
                                showCategoryPicker = false
                                categorySearchText = ""
                                showAddCategory = true
                            }
                            .padding(vertical = Spacing.m)
                            .testTag("add.category.create"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(20.dp))
                            Text(
                                if (query.isEmpty()) "Add $kind Category" else "Add \"$query\"",
                                style = IosText.callout.w(FontWeight.SemiBold),
                                color = IosColors.spendOrange,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.m))
            }
        }
    }

    if (showDiscardConfirmation) {
        DiscardChangesSheet(
            title = if (state.isEditMode) "Discard your changes?" else "Discard this transaction?",
            message = if (state.isEditMode) "Your edits won't be saved." else "What you entered won't be saved.",
            onKeep = { showDiscardConfirmation = false },
            onDiscard = { showDiscardConfirmation = false; onDismiss() },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false; viewModel.delete() },
                    enabled = !state.isSaving,
                ) { Text("Delete", color = IosColors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    if (state.billChoices.isNotEmpty()) {
        val choices = state.billChoices
        val title = if (choices.size == 1) {
            val kind = if (state.type == EntryType.INCOME) "income" else "bill"
            "Is this your ${choices.first().billName} $kind?"
        } else if (state.type == EntryType.INCOME) "Which income is this?" else "Which bill is this?"
        AlertDialog(
            onDismissRequest = viewModel::dismissBillQuestion,
            title = { Text(title) },
            text = { Text(billChoiceMessage(choices, state.currency)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    choices.forEach { candidate ->
                        TextButton(onClick = { viewModel.chooseBill(candidate) }) {
                            Text(if (choices.size == 1) "Use this one" else candidateLabel(candidate, state.currency))
                        }
                    }
                    TextButton(onClick = viewModel::saveSeparate) {
                        Text(if (choices.size == 1) "Save as separate" else "None of these, save as separate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBillQuestion) { Text("Cancel") }
            },
        )
    }

    state.existingBill?.let { bill ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBillQuestion,
            title = { Text("You already have ${bill.description}") },
            text = {
                Text(
                    "${bill.description} · ${moneyText(bill.amount, state.currency)} · next ${formatShortDate(bill.firstOccurrenceDate)}. " +
                        "Link this payment to it instead of creating a second bill?",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = viewModel::useExistingBill, enabled = !state.isSaving) {
                        Text("Use existing bill")
                    }
                    TextButton(onClick = viewModel::createNewBill, enabled = !state.isSaving) {
                        Text("Create a new bill")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBillQuestion) { Text("Cancel") }
            },
        )
    }

    if (showDatePicker) {
        EntryDatePickerDialog(
            date = state.date,
            onDismiss = { showDatePicker = false },
            onPicked = { picked ->
                viewModel.onDateChange(picked)
                showDatePicker = false
            },
        )
    }

    if (showNextDuePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.nextDue.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(state.date) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= state.date
                    override fun isSelectableYear(year: Int): Boolean = true
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showNextDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onNextDueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showNextDuePicker = false
                }) { Text("OK", color = IosColors.spendOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showNextDuePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState, title = { Text("Next due") }, showModeToggle = false, colors = datePickerColors())
        }
    }
}

/**
 * One row: close on the left, the Expense | Income switch centered in what is left. The switch is
 * the title: it says what is being added and flips it in one tap (create and edit mode alike).
 */
@Composable
private fun QuickAddHeader(
    type: EntryType,
    onTypeChange: (EntryType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp)
                .clickable(role = Role.Button, onClick = onClose)
                .testTag("quick-add-close"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SfSymbols.icon("xmark"),
                contentDescription = "Close",
                tint = IosColors.textPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            EntryTypeSwitch(type, onTypeChange)
        }
        // Mirrors the close button so the switch stays centered in the sheet.
        Spacer(Modifier.size(44.dp))
    }
}

/** Compact Expense | Income switch; the picked side wears its money color (red out, green in). */
@Composable
private fun EntryTypeSwitch(selected: EntryType, onSelect: (EntryType) -> Unit) {
    val tertiary = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    Row(
        Modifier.clip(CircleShape).background(tertiary).padding(3.dp)
            .semantics { contentDescription = "Transaction type" }
            .testTag("quick-add-type-picker"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EntryType.entries.forEach { type ->
            val isSelected = selected == type
            val tint = if (type == EntryType.EXPENSE) IosColors.expense else IosColors.income
            val fill by animateColorAsState(if (isSelected) tint.copy(alpha = 0.15f) else Color.Transparent, tween(200), label = "typeFill")
            val outline by animateColorAsState(if (isSelected) tint.copy(alpha = 0.35f) else Color.Transparent, tween(200), label = "typeOutline")
            val content by animateColorAsState(if (isSelected) tint else IosColors.textSecondary, tween(200), label = "typeContent")
            Row(
                Modifier.height(34.dp).clip(CircleShape)
                    .background(fill)
                    .border(1.dp, outline, CircleShape)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(type) }
                    .padding(horizontal = 10.dp)
                    .testTag("add.type." + type.name.lowercase()),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SfSymbols.icon(if (type == EntryType.EXPENSE) "arrow.down" else "arrow.up"),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (type == EntryType.EXPENSE) "Expense" else "Income",
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AssistedEntryBanner(source: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
            .background(IosColors.spendOrange.copy(alpha = 0.10f))
            .border(1.dp, IosColors.spendOrange.copy(alpha = 0.30f), RoundedCornerShape(Radius.m))
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Suggested from $source", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(
                "Review the suggested values before saving. Every field remains editable.",
                style = IosText.caption,
                color = IosColors.textSecondary,
            )
        }
    }
}

/**
 * Shown after the smart-entry quota ran out: the typed text is already in the description, and the
 * upgrade sits alongside (iOS `sentenceExhaustedBanner`).
 */
@Composable
private fun SmartEntriesUsedBanner(onSeePro: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
            .background(IosColors.spendOrange.copy(alpha = 0.10f))
            .border(1.dp, IosColors.spendOrange.copy(alpha = 0.30f), RoundedCornerShape(Radius.m))
            .padding(Spacing.m)
            .testTag("quick-add-sentence-exhausted"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(QuotaCopy.SENTENCE_EXHAUSTED_TITLE, style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(QuotaCopy.SENTENCE_EXHAUSTED_BODY, style = IosText.caption, color = IosColors.textSecondary)
            if (onSeePro != null) {
                Text(
                    "See Pro",
                    style = IosText.caption.w(FontWeight.SemiBold),
                    color = IosColors.spendOrange,
                    modifier = Modifier.padding(top = 2.dp).clickable(role = Role.Button, onClick = onSeePro)
                        .testTag("quick-add-sentence-exhausted-upgrade"),
                )
            }
        }
    }
}

@Composable
private fun UndoBanner(
    description: String,
    amount: Double,
    currency: String,
    addedCount: Int,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(20.dp))
        Text(
            "Saved " + description.ifEmpty { "transaction" } + " · " + moneyText(amount, currency) + " · $addedCount added",
            style = IosText.subheadline,
            color = IosColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Undo",
            style = IosText.subheadline.w(FontWeight.SemiBold),
            color = IosColors.spendOrange,
            modifier = Modifier.clickable(role = Role.Button, onClick = onUndo),
        )
    }
}

@Composable
private fun HeroAmountField(
    value: String,
    currency: String,
    tint: Color,
    missing: Boolean,
    enabled: Boolean,
    keyboardVisible: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // The amount follows the type: the symbol at 70%, the digits at full strength.
    val amountTint by animateColorAsState(tint, tween(200), label = "amountTint")
    Column(modifier.fillMaxWidth().padding(top = Spacing.s).padding(bottom = if (missing) Spacing.m else 0.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                currencySymbol(currency),
                style = IosText.rounded(34, FontWeight.Bold),
                color = amountTint.copy(alpha = 0.7f),
                modifier = Modifier.alignBy(FirstBaseline),
            )
            Spacer(Modifier.width(Spacing.s))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                keyboardActions = rememberKeyboardDoneActions(),
                interactionSource = interactionSource,
                textStyle = IosText.rounded(46, FontWeight.ExtraBold).copy(color = amountTint),
                modifier = Modifier.weight(1f).alignBy(FirstBaseline).focusRequester(focusRequester)
                    .semantics { contentDescription = "Amount in $currency. Enter the transaction amount" },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "0",
                                style = IosText.rounded(46, FontWeight.ExtraBold),
                                color = IosColors.textTertiary.copy(alpha = 0.55f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Spacer(Modifier.height(if (keyboardVisible) Spacing.xs else Spacing.s))
        HorizontalDivider(
            thickness = if (missing || focused) 2.dp else 1.dp,
            color = when {
                missing -> IosColors.expense
                focused -> IosColors.spendOrange
                else -> cardBorder()
            },
        )
        if (missing) ValidationMessage("Enter an amount above zero")
    }
}

@Composable
private fun ValidationMessage(message: String) {
    Row(
        Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SfSymbols.icon("exclamationmark.circle"),
            contentDescription = null,
            tint = IosColors.expense,
            modifier = Modifier.size(14.dp),
        )
        Text(message, style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.expense)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescriptionField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text("Merchant or description", style = IosText.subheadline, color = IosColors.textSecondary)
        val shape = RoundedCornerShape(Radius.m)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .clip(shape).border(1.dp, cardBorder(), shape)
                .padding(horizontal = Spacing.m, vertical = if (WindowInsets.isImeVisible) Spacing.s else Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("text.alignleft"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = doneKeyboardOptions(),
                keyboardActions = rememberKeyboardDoneActions(),
                textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).then(modifier),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "Where or what, like Starbucks",
                                style = IosText.callout,
                                color = IosColors.textSecondary.copy(alpha = 0.55f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    state: AddTransactionUiState,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text("Category", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
            Spacer(Modifier.weight(1f))
            state.categorySuggestionSource?.let { source ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    if (source == AddTransactionViewModel.RULE_SOURCE) {
                        Icon(Icons.Outlined.Link, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(14.dp))
                    } else {
                        Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(14.dp))
                    }
                    Text(source, style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            state.quickCategoryChoices.take(4).forEach { category ->
                FormChip(
                    title = category.name,
                    category = category,
                    isSelected = state.categoryId == category.id,
                    onClick = { onSelect(category.id) },
                    modifier = Modifier.testTag("add.category"),
                )
            }
            val empty = state.visibleCategories.isEmpty()
            FormChip(
                title = if (empty) "Add a category" else "More",
                systemImage = if (empty) "plus" else "square.grid.2x2",
                onClick = onMore,
                modifier = Modifier.testTag("add.category"),
            )
        }
        if (state.categoryMissing) ValidationMessage("Pick a category")
    }
}

@Composable
private fun FormChip(
    title: String,
    systemImage: String? = null,
    category: Category? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Row(
        modifier.clip(shape)
            .background(if (isSelected) IosColors.spendOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) IosColors.spendOrange else cardBorder(), shape)
            .semantics { this.selected = isSelected }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when {
            category != null -> CategoryIcon(
                category.icon,
                category.name,
                tint = if (isSelected) IosColors.spendOrange else IosColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
            systemImage != null -> Icon(
                SfSymbols.icon(systemImage),
                contentDescription = null,
                tint = if (isSelected) IosColors.spendOrange else IosColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            title,
            style = if (isSelected) IosText.callout.w(FontWeight.SemiBold) else IosText.callout,
            color = if (isSelected) IosColors.spendOrange else IosColors.textPrimary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateChoices(
    date: LocalDate,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onPickDate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Date", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
        val today = date == LocalDate.now()
        val yesterday = date == LocalDate.now().minusDays(1)
        // The Pick date chip lights up only for a custom date (and then shows it), never because
        // the dialog is open.
        val custom = !today && !yesterday
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            DateChip("Today", today, onToday)
            DateChip("Yesterday", yesterday, onYesterday)
            DateChip(
                if (custom) formatShortDate(date.toString()) else "Pick date",
                custom,
                onPickDate,
                symbol = "calendar",
            )
        }
    }
}

/**
 * The Date row's calendar: a Material3 dialog that floats over the form instead of an inline
 * calendar that pushes the fields below it down. Picking a day applies it and closes the dialog
 * (so no OK button), up to one year ahead.
 */
@Composable
private fun EntryDatePickerDialog(date: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val maxDate = remember { LocalDate.now().plusDays(365) }
    val today = LocalDate.now()
    // Today and Yesterday have their own chips, so nothing is pre-selected for them: tapping
    // either day is then a real selection change that closes the dialog.
    val custom = date != today && date != today.minusDays(1)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (custom) date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else null,
        initialDisplayedMonthMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(maxDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= maxDate
                override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
            }
        },
    )
    val initialMillis = remember { pickerState.selectedDateMillis }
    LaunchedEffect(pickerState.selectedDateMillis) {
        val millis = pickerState.selectedDateMillis ?: return@LaunchedEffect
        if (millis == initialMillis) return@LaunchedEffect
        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(
            state = pickerState,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = datePickerColors(),
            modifier = Modifier.testTag("add.date.picker"),
        )
    }
}

@Composable
private fun DateChip(title: String, selected: Boolean, onClick: () -> Unit, symbol: String? = null) {
    val shape = CircleShape
    Row(
        Modifier.clip(shape)
            .background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) IosColors.spendOrange else cardBorder(), shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (symbol != null) {
            Icon(
                SfSymbols.icon(symbol),
                contentDescription = null,
                tint = if (selected) IosColors.spendOrange else IosColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            title,
            style = if (selected) IosText.callout.w(FontWeight.SemiBold) else IosText.callout,
            color = if (selected) IosColors.spendOrange else IosColors.textPrimary,
        )
    }
}

@Composable
private fun RecurringSection(
    state: AddTransactionUiState,
    onRecurringChange: (Boolean) -> Unit,
    onPresetChange: (RecurrencePreset) -> Unit,
    onPickNextDue: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, cardBorder(), shape).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Make it recurring", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                Text(state.recurringSubtitle, style = IosText.caption, color = IosColors.textSecondary)
            }
            com.spenditos.android.ui.components.IosSwitch(
                checked = state.makeRecurring,
                onCheckedChange = onRecurringChange,
                modifier = Modifier.testTag("quick-add-recurring-toggle"),
                tint = IosColors.spendOrange,
            )
        }
        if (state.makeRecurring) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RecurrencePreset.entries.forEach { preset ->
                    FormChip(title = preset.label, isSelected = state.recurrencePreset == preset, onClick = { onPresetChange(preset) })
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onPickNextDue)
                    .testTag("quick-add-next-due"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Next due", style = IosText.callout, color = IosColors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(formatShortDate(state.nextDue.toString()), style = IosText.callout, color = IosColors.textSecondary)
                Spacer(Modifier.width(Spacing.xs))
                Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                Icon(
                    SfSymbols.icon("calendar.badge.clock"),
                    contentDescription = null,
                    tint = IosColors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(state.recurringHint, style = IosText.caption, color = IosColors.textSecondary)
            }
        }
    }
}

@Composable
private fun CompactMoodPicker(
    mood: TransactionMood?,
    onMoodChange: (TransactionMood?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("How did it feel?", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("OPTIONAL", style = IosText.caption2.w(FontWeight.Bold), color = IosColors.textTertiary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TransactionMood.entries.forEach { option ->
                val selected = mood == option
                val dimmed = mood != null && !selected
                val color = moodColor(option)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.m))
                        .background(color.copy(alpha = if (selected) 0.18f else 0.08f))
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            color.copy(alpha = if (selected) 0.6f else 0.22f),
                            RoundedCornerShape(Radius.m),
                        )
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Button) { onMoodChange(if (selected) null else option) }
                        .padding(vertical = 12.dp)
                        .then(if (dimmed) Modifier.alpha(0.42f) else Modifier)
                        .testTag("quick-add-mood." + option.name.lowercase()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        SfSymbols.icon(moodIcon(option)),
                        contentDescription = null,
                        tint = color.copy(alpha = if (selected) 1f else 0.75f),
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        moodLabel(option),
                        style = IosText.caption.w(if (selected) FontWeight.Bold else FontWeight.SemiBold),
                        color = color.copy(alpha = if (selected) 1f else 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteTransactionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, IosColors.expense.copy(alpha = 0.25f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SfSymbols.icon("trash"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.size(19.dp))
        Text("Delete transaction", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.expense)
    }
}

@Composable
private fun QuickAddBottomBar(
    state: AddTransactionUiState,
    onVoice: (() -> Unit)?,
    onSentence: (() -> Unit)?,
    onScanReceipt: (() -> Unit)?,
    onSave: () -> Unit,
    onSaveAndContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // iOS `.background(.ultraThinMaterial)` edge to edge over the grouped background.
        modifier.background(barMaterial())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs + 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (state.isEditMode) {
            QuickAddLoadingButton(
                text = if (state.type == EntryType.EXPENSE) "Update expense" else "Update income",
                isSaving = state.isSaving,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().testTag("quick-add-save"),
                legacyTestTag = "add.save",
            )
        } else {
            // One line: icon-only methods, then Save with a chevron menu holding "Save and add
            // another". On a 320dp phone the icons and the chevron shrink so Save keeps its width.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < COMPACT_BAR_WIDTH
                val iconWidth = if (compact) 40.dp else 44.dp
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        MethodBarButton(
                            icon = "square.and.pencil",
                            label = "Form",
                            selected = true,
                            width = iconWidth,
                            onClick = {},
                            modifier = Modifier.testTag("quick-add-method-manual"),
                        )
                        onVoice?.let { callback ->
                            MethodBarButton(
                                icon = "mic.fill",
                                label = "Voice",
                                selected = false,
                                width = iconWidth,
                                onClick = callback,
                                modifier = Modifier.testTag("quick-add-method-voice"),
                                legacyTestTag = "add.capture",
                            )
                        }
                        onSentence?.let { callback ->
                            MethodBarButton(
                                icon = "text.alignleft",
                                label = "Sentence",
                                selected = false,
                                width = iconWidth,
                                onClick = callback,
                                modifier = Modifier.testTag("quick-add-method-text"),
                            )
                        }
                        if (state.type == EntryType.EXPENSE) {
                            onScanReceipt?.let { callback ->
                                MethodBarButton(
                                    icon = "doc.viewfinder",
                                    label = "Scan",
                                    selected = false,
                                    width = iconWidth,
                                    onClick = callback,
                                    modifier = Modifier.testTag("quick-add-method-photo"),
                                    legacyTestTag = "add.scanReceipt",
                                )
                            }
                        }
                    }
                    SaveSplitButton(
                        isSaving = state.isSaving,
                        chevronWidth = if (compact) 36.dp else 44.dp,
                        onSave = onSave,
                        onSaveAndContinue = onSaveAndContinue,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Below this much room (a 320dp phone), the method icons and the chevron shrink to keep Save wide. */
private val COMPACT_BAR_WIDTH = 300.dp

/**
 * Save fills the row; a chevron after a thin divider opens a menu with "Save and add another"
 * (the batch flow, with its added count and undo, is unchanged: only its entry point moved).
 */
@Composable
private fun SaveSplitButton(
    isSaving: Boolean,
    chevronWidth: Dp,
    onSave: () -> Unit,
    onSaveAndContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.m)
    val background = if (isSaving) IosColors.spendOrange.copy(alpha = 0.72f) else IosColors.spendOrange
    Row(
        modifier.height(52.dp).clip(shape).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .clickable(enabled = !isSaving, role = Role.Button, onClick = onSave)
                .semantics { if (isSaving) stateDescription = "Saving" }
                .testTag("quick-add-save"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.s))
            }
            Text("Save", style = IosText.headline, color = Color.White, maxLines = 1, modifier = Modifier.testTag("add.save"))
        }
        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.3f)))
        Box {
            Box(
                Modifier.width(chevronWidth).fillMaxHeight()
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = "More save options" }
                    .testTag("quick-add-save-menu"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfSymbols.icon("chevron.up"), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Save and add another", style = IosText.callout) },
                    leadingIcon = { Icon(SfSymbols.icon("plus.circle"), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    enabled = !isSaving,
                    onClick = {
                        menuOpen = false
                        onSaveAndContinue()
                    },
                    modifier = Modifier.testTag("quick-add-save-another"),
                )
            }
        }
    }
}

/** Icon-only capture method; TalkBack still reads [label]. */
@Composable
private fun MethodBarButton(
    icon: String,
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    legacyTestTag: String? = null,
) {
    Box(
        modifier.size(width = width, height = 48.dp).clip(RoundedCornerShape(Radius.m))
            .background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else Color.Transparent)
            .semantics { this.selected = selected; contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SfSymbols.icon(icon),
            contentDescription = null,
            tint = if (selected) IosColors.spendOrange else IosColors.textSecondary,
            modifier = Modifier.size(22.dp).let { if (legacyTestTag != null) it.testTag(legacyTestTag) else it },
        )
    }
}

@Composable
private fun QuickAddLoadingButton(
    text: String,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    legacyTestTag: String? = null,
) {
    val shape = RoundedCornerShape(Radius.m)
    val background = if (isSaving) IosColors.spendOrange.copy(alpha = 0.72f) else IosColors.spendOrange
    Row(
        modifier.height(52.dp).clip(shape).background(background)
            .clickable(enabled = !isSaving, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSaving) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.s))
        }
        Text(
            text,
            style = IosText.headline,
            color = Color.White,
            modifier = legacyTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

@Composable
private fun CategorySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        modifier.fillMaxWidth().then(com.spenditos.android.ui.components.subtleGlassModifier())
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(19.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = doneKeyboardOptions(),
            keyboardActions = rememberKeyboardDoneActions(),
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) Text("Search categories...", style = IosText.callout, color = IosColors.textSecondary)
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                SfSymbols.icon("xmark.circle.fill"),
                contentDescription = "Clear search",
                tint = IosColors.textSecondary,
                modifier = Modifier.size(20.dp).clickable(role = Role.Button) { onValueChange("") },
            )
        }
    }
}

@Composable
private fun CategoryGridCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(if (selected) IosColors.spendOrange.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) IosColors.spendOrange.copy(alpha = 0.6f) else IosColors.separator.copy(alpha = 0.35f),
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box {
            CategorySymbolBadge(category.icon, category.name, category.color, size = 48.dp)
            if (selected) {
                Icon(
                    SfSymbols.icon("checkmark.circle.fill"),
                    contentDescription = null,
                    tint = IosColors.spendOrange,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                )
            }
        }
        Text(category.name, style = IosText.callout, color = IosColors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun datePickerColors() = DatePickerDefaults.colors(
    selectedDayContainerColor = IosColors.spendOrange,
    todayDateBorderColor = IosColors.spendOrange,
    todayContentColor = IosColors.spendOrange,
)

@Composable
private fun barMaterial(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xF01C1C1E) else Color(0xF0E4E6EB)

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "CAD" -> "CA$"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> runCatching { Currency.getInstance(currency.uppercase()).symbol }.getOrDefault(currency)
}

private fun moneyText(amount: Double, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply {
        this.currency = Currency.getInstance(currency.uppercase())
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}.getOrElse { currencySymbol(currency) + "%.2f".format(Locale.US, amount) }

private fun formatShortDate(iso: String): String =
    runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }.getOrDefault(iso)

private fun candidateLabel(candidate: BillCandidate, currency: String): String =
    candidate.billName + " · " + moneyText(candidate.estimate, currency) + " · " + formatShortDate(candidate.dueDate)

private fun billChoiceMessage(choices: List<BillCandidate>, currency: String): String {
    if (choices.size == 1) {
        val only = choices.first()
        return only.billName + " · " + moneyText(only.estimate, currency) + " · due " +
            formatShortDate(only.dueDate) + ". Using it replaces the estimate, so it isn't counted twice."
    }
    return "It looks like more than one upcoming bill. Pick the one it pays so it isn't counted twice."
}

private fun moodLabel(mood: TransactionMood): String = when (mood) {
    TransactionMood.WORTH_IT -> "Worth It"
    TransactionMood.NECESSARY -> "Necessary"
    TransactionMood.REGRET -> "Regret"
    TransactionMood.IMPULSIVE -> "Impulsive"
}

private fun moodIcon(mood: TransactionMood): String = when (mood) {
    TransactionMood.WORTH_IT -> "star.fill"
    TransactionMood.NECESSARY -> "checkmark.circle.fill"
    TransactionMood.REGRET -> "exclamationmark.triangle.fill"
    TransactionMood.IMPULSIVE -> "bolt.fill"
}

@Composable
private fun moodColor(mood: TransactionMood): Color = when (mood) {
    TransactionMood.WORTH_IT -> IosColors.income
    TransactionMood.NECESSARY -> Color(0xFF007AFF)
    TransactionMood.REGRET -> IosColors.expense
    TransactionMood.IMPULSIVE -> Color(0xFFFF9500)
}

/** iOS `Color.cardBorder.opacity(0.55)`: the separator (already translucent) at 55% of its alpha. */
@Composable
private fun cardBorder(): Color = IosColors.separator.let { it.copy(alpha = it.alpha * 0.55f) }
