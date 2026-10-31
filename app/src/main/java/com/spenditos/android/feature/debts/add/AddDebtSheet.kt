package com.spenditos.android.feature.debts.add

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.IosSegmented
import com.spenditos.android.ui.components.IosSwitch
import com.spenditos.android.ui.components.ScopedViewModels
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDismisser
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * "Add a debt" (docs/features/debts.md; iOS `AddDebtSheet`): name, I owe, plan by (what I pay or when
 * I'm done), how often, next payment, optional interest, and a live plan from the server before anything
 * is saved. [onCreated] gets the new debt's id once it is saved; the sheet closes right after.
 *
 * The sheet owns a fresh ViewModel per opening, so a host does not need `ScopedViewModels`.
 */
@Composable
fun AddDebtSheet(onDismiss: () -> Unit, onCreated: (debtId: String) -> Unit, prefill: DebtPrefill? = null) {
    ScopedViewModels { AddDebtSheetContent(onDismiss, onCreated, prefill) }
}

@Composable
private fun AddDebtSheetContent(
    onDismiss: () -> Unit,
    onCreated: (debtId: String) -> Unit,
    prefill: DebtPrefill?,
    viewModel: DebtFormViewModel = hiltViewModel(),
) {
    // Before the first frame, so the form never flashes empty; applies once per ViewModel.
    remember(viewModel) {
        viewModel.applyPrefill(prefill)
        true
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardAlert by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DebtFormEvent.Created -> {
                    onCreated(event.debtId)
                    onDismiss()
                }
            }
        }
    }

    val cancel = { if (state.hasUnsavedChanges) showDiscardAlert = true else onDismiss() }

    ModalBottomSheet(
        onDismissRequest = cancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        // The sheet is its own window, so its focus manager is not the screen's.
        val focusManager = LocalFocusManager.current
        val dismissKeyboard = rememberKeyboardDismisser()
        DismissKeyboardWhenInactive()
        DismissKeyboardWhen(showDiscardAlert || showDatePicker)
        Column(
            Modifier.fillMaxWidth().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
                .padding(top = Spacing.m, bottom = Spacing.xl).imePadding().navigationBarsPadding()
                .testTag("debts.add.sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // Inline navigation title with Cancel on the left.
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.common_cancel),
                    Modifier.clickable(role = Role.Button, onClick = cancel).testTag("debts.add.cancel"),
                    style = IosText.body,
                    color = IosColors.spendOrange,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_add_a_debt), style = IosText.headline, color = IosColors.textPrimary)
                }
                Spacer(Modifier.width(48.dp))
            }
            state.error?.let { message ->
                ErrorBanner(message = message, onDismiss = viewModel::dismissError, modifier = Modifier.testTag("debts.add.error"))
            }
            NameSection(state, viewModel, onNext = { focusManager.moveFocus(FocusDirection.Down) })
            DebtAmountField(
                title = stringResource(R.string.debts_i_owe),
                placeholder = "0",
                value = state.owedText,
                onValueChange = viewModel::onOwedChange,
                error = state.owedError,
                currency = state.currency,
                tag = "debts.add.owed",
            )
            PlanBySection(state, viewModel)
            if (state.mode != DebtPlanMode.WHEN_I_CAN) {
                FrequencySection(state, viewModel)
                NextPaymentSection(state, onPick = { showDatePicker = true })
            }
            InterestSection(state, viewModel)
            PreviewCard(state)
            DebtSavePill(
                text = stringResource(R.string.common_add_debt),
                isLoading = state.isSaving,
                onClick = {
                    dismissKeyboard()
                    viewModel.save()
                },
                modifier = Modifier.testTag("debts.add.save"),
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.firstPaymentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(state.today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= state.today
                    override fun isSelectableYear(year: Int): Boolean = year >= state.today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onFirstPaymentDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("debts.add.date.confirm"),
                ) { Text(stringResource(R.string.common_ok), color = IosColors.spendOrange) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(
                state = pickerState,
                title = { Text(stringResource(R.string.debts_next_payment), Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = IosColors.spendOrange,
                    todayDateBorderColor = IosColors.spendOrange,
                    todayContentColor = IosColors.spendOrange,
                ),
            )
        }
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text(stringResource(R.string.debts_discard_this_debt)) },
            text = { Text(stringResource(R.string.common_what_you_entered_won_t)) },
            confirmButton = {
                TextButton(onClick = { showDiscardAlert = false; onDismiss() }) { Text(stringResource(R.string.common_discard), color = IosColors.expense) }
            },
            dismissButton = { TextButton(onClick = { showDiscardAlert = false }) { Text(stringResource(R.string.common_keep_editing)) } },
        )
    }
}

// region Sections

@Composable
private fun NameSection(state: DebtFormUiState, viewModel: DebtFormViewModel, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(stringResource(R.string.common_name))
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .errorStroke(state.nameError != null).padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("text.alignleft"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.weight(1f).testTag("debts.add.name").semantics { contentDescription = Strings.get(R.string.common_name) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                cursorBrush = SolidColor(IosColors.spendOrange),
                decorationBox = { inner ->
                    Box {
                        if (state.name.isEmpty()) Text(stringResource(R.string.debts_name_placeholder), style = IosText.callout, color = IosColors.textTertiary)
                        inner()
                    }
                },
            )
        }
        state.nameError?.let { FieldError(it) }
    }
}

@Composable
private fun PlanBySection(state: DebtFormUiState, viewModel: DebtFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FieldLabel(stringResource(R.string.debts_plan_by))
            IosSegmented(
                options = DebtPlanMode.entries.map { it.label },
                selectedIndex = state.mode.ordinal,
                onSelect = { viewModel.setMode(DebtPlanMode.entries[it]) },
                // IosSegmented carries its own 20 dp side insets; this sheet already has them.
                modifier = Modifier.bleed(Spacing.screen),
                tagPrefix = "debts.add.mode",
            )
        }
        when (state.mode) {
            DebtPlanMode.WHAT_I_PAY -> DebtAmountField(
                title = stringResource(R.string.debts_each_payment),
                placeholder = "0",
                value = state.paymentText,
                onValueChange = viewModel::onPaymentChange,
                error = state.paymentError,
                currency = state.currency,
                tag = "debts.add.payment",
            )
            DebtPlanMode.WHEN_IM_DONE -> TargetMonthPicker(state, viewModel)
            DebtPlanMode.WHEN_I_CAN -> Text(
                stringResource(R.string.debts_no_payments_are_planned_record),
                style = IosText.subheadline,
                color = IosColors.textSecondary,
                modifier = Modifier.testTag("debts.add.whenICanNote"),
            )
        }
    }
}

@Composable
private fun TargetMonthPicker(state: DebtFormUiState, viewModel: DebtFormViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val years = (state.today.year..(state.today.year + 40)).toList()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(stringResource(R.string.debts_paid_off_by))
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics { contentDescription = Strings.get(R.string.debts_paid_off_by) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            PickerMenu(
                label = state.targetMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                options = Month.entries.map { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) },
                onSelect = { index -> viewModel.onTargetMonthChange(YearMonth.of(state.targetMonth.year, index + 1)) },
                tag = "debts.add.targetMonth",
            )
            PickerMenu(
                label = state.targetMonth.year.toString(),
                options = years.map(Int::toString),
                onSelect = { index -> viewModel.onTargetMonthChange(YearMonth.of(years[index], state.targetMonth.monthValue)) },
                tag = "debts.add.targetYear",
            )
        }
        state.targetError?.let { FieldError(it) }
    }
}

@Composable
private fun FrequencySection(state: DebtFormUiState, viewModel: DebtFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(stringResource(R.string.debts_how_often))
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("repeat"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            PickerMenu(
                label = DebtAddText.frequencyLabel(state.frequency),
                options = DebtAddText.frequencies.map(DebtAddText::frequencyLabel),
                onSelect = { viewModel.onFrequencyChange(DebtAddText.frequencies[it]) },
                tag = "debts.add.frequency",
            )
        }
    }
}

@Composable
private fun NextPaymentSection(state: DebtFormUiState, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(stringResource(R.string.debts_next_payment))
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .clickable(role = Role.Button, onClick = onPick)
                .testTag("debts.add.date").padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                state.firstPaymentDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = IosText.body,
                color = IosColors.textPrimary,
            )
        }
        state.firstPaymentError?.let { FieldError(it) }
    }
}

@Composable
private fun InterestSection(state: DebtFormUiState, viewModel: DebtFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.debts_charges_interest), style = IosText.callout, color = IosColors.textPrimary)
                Text(stringResource(R.string.debts_off_for_money_you_borrowed), style = IosText.caption, color = IosColors.textTertiary)
            }
            IosSwitch(
                checked = state.chargesInterest,
                onCheckedChange = viewModel::onChargesInterestChange,
                modifier = Modifier.testTag("debts.add.interestToggle"),
                tint = IosColors.spendOrange,
            )
        }
        if (state.chargesInterest) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FieldLabel(stringResource(R.string.debts_interest_rate_a_year))
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .errorStroke(state.aprError != null).padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    BasicTextField(
                        value = state.aprText,
                        onValueChange = { viewModel.onAprChange(it.filter(::isAmountChar)) },
                        modifier = Modifier.weight(1f).testTag("debts.add.apr")
                            .semantics { contentDescription = Strings.get(R.string.debts_interest_rate_percent_a_year) },
                        singleLine = true,
                        keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                        keyboardActions = rememberKeyboardDoneActions(),
                        textStyle = AMOUNT_STYLE.copy(color = IosColors.textPrimary),
                        cursorBrush = SolidColor(IosColors.spendOrange),
                        decorationBox = { inner ->
                            Box {
                                if (state.aprText.isEmpty()) Text(stringResource(R.string.debts_apr_placeholder), style = AMOUNT_STYLE, color = IosColors.textTertiary)
                                inner()
                            }
                        },
                    )
                    Text("%", style = AMOUNT_STYLE, color = IosColors.textTertiary)
                }
                state.aprError?.let { FieldError(it) }
            }
        }
    }
}

// endregion

// region Live plan

@Composable
private fun PreviewCard(state: DebtFormUiState) {
    val headline = state.previewHeadline
    val neverPaysOff = state.preview?.neverPaysOff == true
    when {
        headline != null -> {
            val detail = state.previewDetail
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange.copy(alpha = 0.08f))
                    .padding(Spacing.m)
                    .semantics(mergeDescendants = true) {
                        contentDescription = Strings.get(R.string.debts_plan_a11y, listOfNotNull(headline, detail).joinToString(". "))
                    }
                    .testTag("debts.add.preview"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                    Icon(
                        SfSymbols.icon(if (neverPaysOff) "exclamationmark.triangle" else "calendar.badge.checkmark"),
                        contentDescription = null,
                        tint = if (neverPaysOff) IosColors.expense else IosColors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        headline,
                        style = IosText.callout.w(FontWeight.SemiBold),
                        color = if (neverPaysOff) IosColors.expense else IosColors.textPrimary,
                    )
                }
                detail?.let { Text(it, style = IosText.subheadline, color = IosColors.textSecondary) }
            }
        }
        state.previewError != null -> Row(
            Modifier.testTag("debts.add.previewError"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SfSymbols.icon("exclamationmark.circle"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.size(18.dp))
            Text(state.previewError, style = IosText.subheadline, color = IosColors.expense)
        }
        state.isPreviewing -> Row(
            Modifier.testTag("debts.add.previewLoading"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = IosColors.spendOrange, strokeWidth = 2.dp)
            Text(stringResource(R.string.debts_working_out_your_plan), style = IosText.subheadline, color = IosColors.textSecondary)
        }
    }
}

// endregion

// region Pieces

private fun isAmountChar(c: Char): Boolean = c.isDigit() || c == '.' || c == ','

private val AMOUNT_STYLE = IosText.rounded(22, FontWeight.SemiBold)

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = IosText.subheadline, color = IosColors.textSecondary)
}

@Composable
private fun FieldError(text: String) {
    Text(text, style = IosText.caption, color = IosColors.expense)
}

/** iOS `.overlay(RoundedRectangle(cornerRadius: radiusM).stroke(error ? expense.opacity(0.6) : clear, 1))`. */
@Composable
private fun Modifier.errorStroke(hasError: Boolean): Modifier =
    if (hasError) border(1.dp, IosColors.expense.copy(alpha = 0.6f), RoundedCornerShape(Radius.m)) else this

/** Lets a child that adds its own [inset] side padding line up with the surrounding column's edges. */
private fun Modifier.bleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-extra / 2, 0) }
}

/** iOS "currency symbol + decimal pad" in the app's card style (`DebtAmountField`). */
@Composable
private fun DebtAmountField(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    currency: String,
    tag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(title)
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .errorStroke(error != null).padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(currencySymbol(currency), style = AMOUNT_STYLE, color = IosColors.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(::isAmountChar)) },
                modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = title },
                singleLine = true,
                keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                keyboardActions = rememberKeyboardDoneActions(),
                textStyle = AMOUNT_STYLE.copy(color = IosColors.textPrimary),
                cursorBrush = SolidColor(IosColors.spendOrange),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = AMOUNT_STYLE, color = IosColors.textTertiary)
                        inner()
                    }
                },
            )
        }
        error?.let { message ->
            Text(
                message,
                Modifier.semantics { contentDescription = "$title: $message" },
                style = IosText.caption,
                color = IosColors.expense,
            )
        }
    }
}

/** iOS `Picker` with the menu style, tinted with the brand color. */
@Composable
private fun PickerMenu(label: String, options: List<String>, onSelect: (Int) -> Unit, tag: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable(role = Role.Button) { expanded = true }.testTag(tag).padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(label, style = IosText.body, color = IosColors.spendOrange)
            Icon(SfSymbols.icon("chevron.up.chevron.down"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(12.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    modifier = Modifier.testTag("$tag.$index"),
                )
            }
        }
    }
}

/** iOS `LoadingButton`: 52 pt tall, radius M, brand fill; busy while saving. */
@Composable
private fun DebtSavePill(text: String, isLoading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.m))
            .background(if (isLoading) IosColors.spendOrange.copy(alpha = 0.6f) else IosColors.spendOrange)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = IosText.headline, color = Color.White)
        }
    }
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> runCatching { java.util.Currency.getInstance(currency).getSymbol() }.getOrDefault(currency)
}

// endregion
