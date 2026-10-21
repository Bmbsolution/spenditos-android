package com.spenditos.android.feature.debts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** iOS `DebtExtraPaymentSheet`: amount, date (not in the future), one button. Online only. */
@Composable
internal fun DebtExtraPaymentSheet(
    state: DebtDetailUiState,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
    onSubmit: (amount: Double, date: LocalDate) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val amount = parseDebtAmount(amountText)
    val error = extraPaymentAmountError(amountText, showErrors)
    DebtSheetFrame(title = "Extra payment", cancelTag = "debt.extra.cancel", onDismiss = onDismiss, enabled = !state.isWorking) {
        state.error?.let { ErrorBanner(it, onDismiss = onDismissError, modifier = Modifier.testTag("debt.extra.error")) }
        DebtAmountField(
            title = "Extra payment",
            placeholder = "0",
            text = amountText,
            onTextChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            currencySymbol = currencySymbol(state.currency),
            error = error,
            tag = "debt.extra.amount",
        )
        DebtDateField(epochDay = epochDay, onChange = { epochDay = it }, tag = "debt.extra.date")
        Text(
            "Added to your transactions as a payment on ${state.debt?.name ?: "this debt"}.",
            style = IosText.caption,
            color = IosColors.textTertiary,
        )
        DebtLoadingButton(
            "Add extra payment",
            isLoading = state.isWorking,
            modifier = Modifier.testTag("debt.extra.submit"),
        ) {
            showErrors = true
            if (amount != null && amount > 0) onSubmit(amount, LocalDate.ofEpochDay(epochDay))
        }
    }
}

/** iOS `DebtCorrectBalanceSheet`: what you owe on a day (not in the future). */
@Composable
internal fun DebtCorrectBalanceSheet(
    state: DebtDetailUiState,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
    onSubmit: (balance: Double, date: LocalDate) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(state.summary?.balance?.let(::balanceFieldText).orEmpty()) }
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val amount = parseDebtAmount(amountText)
    val error = correctBalanceAmountError(amountText, showErrors)
    DebtSheetFrame(title = "Correct balance", cancelTag = "debt.correct.cancel", onDismiss = onDismiss, enabled = !state.isWorking) {
        state.error?.let { ErrorBanner(it, onDismiss = onDismissError, modifier = Modifier.testTag("debt.correct.error")) }
        Text(
            "Use this when the lender's number is different from yours. Your balance is set to what you enter from that day on.",
            style = IosText.subheadline,
            color = IosColors.textSecondary,
        )
        DebtAmountField(
            title = "I owe today",
            placeholder = "0",
            text = amountText,
            onTextChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            currencySymbol = currencySymbol(state.currency),
            error = error,
            tag = "debt.correct.amount",
        )
        DebtDateField(epochDay = epochDay, onChange = { epochDay = it }, tag = "debt.correct.date")
        DebtLoadingButton(
            "Correct balance",
            isLoading = state.isWorking,
            modifier = Modifier.testTag("debt.correct.submit"),
        ) {
            showErrors = true
            if (amount != null && amount >= 0) onSubmit(amount, LocalDate.ofEpochDay(epochDay))
        }
    }
}

/** Inline-titled sheet: "Cancel" leading, 17 semibold title centered, scrolling content. */
@Composable
private fun DebtSheetFrame(
    title: String,
    cancelTag: String,
    onDismiss: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        DismissKeyboardWhenInactive()
        Column(
            Modifier.fillMaxWidth().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
                .padding(top = Spacing.s, bottom = Spacing.l).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onDismiss).testTag(cancelTag),
                    style = IosText.body,
                    color = IosColors.spendOrange,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(title, style = IosText.headline, color = IosColors.textPrimary)
                }
                Spacer(Modifier.width(48.dp))
            }
            content()
        }
    }
}

/** "Date" label and a card that opens a date picker limited to today and earlier. */
@Composable
private fun DebtDateField(epochDay: Long, onChange: (Long) -> Unit, tag: String) {
    var showPicker by remember { mutableStateOf(false) }
    DismissKeyboardWhen(showPicker)
    val date = LocalDate.ofEpochDay(epochDay)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Date", style = IosText.subheadline, color = IosColors.textSecondary)
        Text(
            DebtFormat.shortDate(date.toString()) ?: date.toString(),
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .clickable(role = Role.Button) { showPicker = true }
                .padding(Spacing.m).testTag(tag),
            style = IosText.body.w(FontWeight.Medium),
            color = IosColors.spendOrange,
        )
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())

                    override fun isSelectableYear(year: Int): Boolean = true
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    showPicker = false
                }) { Text("OK", color = IosColors.spendOrange) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = IosColors.spendOrange,
                    todayDateBorderColor = IosColors.spendOrange,
                    todayContentColor = IosColors.spendOrange,
                ),
            )
        }
    }
}
