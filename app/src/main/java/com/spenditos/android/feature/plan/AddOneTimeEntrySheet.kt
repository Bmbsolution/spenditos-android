package com.spenditos.android.feature.plan

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Saves a date as its epoch day so it survives recreation in the saved-state Bundle. */
internal val LocalDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOneTimeEntrySheet(
    month: YearMonth,
    categories: List<Category>,
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (String, BigDecimal, String, LocalDate) -> Boolean,
    /** iOS PlanningDraftEntrySheet `suggestion`: Plan checkup "Review draft" prefills it. */
    suggestion: com.spenditos.android.data.model.MonthlyPlanCheckupSuggestion? = null,
) {
    val expenseCategories = categories.filterNot { it.isIncome }
    val firstCategory = suggestion?.categoryId?.takeIf { id -> categories.any { it.id == id } }
        ?: expenseCategories.firstOrNull()?.id ?: categories.firstOrNull()?.id.orEmpty()
    var isIncome by remember { mutableStateOf(categories.firstOrNull { it.id == firstCategory }?.isIncome ?: false) }
    var description by rememberSaveable { mutableStateOf(suggestion?.description.orEmpty()) }
    var amountText by rememberSaveable { mutableStateOf(suggestion?.amount?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(firstCategory) }
    var date by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(month.atDay(1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val availableCategories = categories.filter { it.isIncome == isIncome }
    val amount = parsePlanAmount(amountText)
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    val canSubmit = amount != null && description.isNotBlank() && categoryId.isNotBlank() && availableCategories.isNotEmpty()
    val submit = {
        val parsed = amount
        if (parsed == null || categoryId !in availableCategories.map { it.id } || YearMonth.from(date) != month || description.isBlank()) {
            error = Strings.get(R.string.plan_enter_a_description_amount_category)
        } else if (onAdd(description, parsed, categoryId, date)) {
            onDismiss()
        } else {
            error = Strings.get(R.string.plan_enter_a_description_amount_category)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        DismissKeyboardWhenInactive()
        DismissKeyboardWhen(showDatePicker || showCategoryPicker || showTypeMenu)
        Column(
            modifier = Modifier.fillMaxWidth().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.l).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.common_cancel), Modifier.clickable(role = Role.Button, onClick = onDismiss), style = IosText.body, color = IosColors.spendOrange)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.plan_add_to_plan), style = IosText.headline, color = IosColors.textPrimary)
                }
                Text(
                    stringResource(R.string.plan_add_to_draft),
                    Modifier.testTag("monthPlan.oneTime.add").clickable(enabled = canSubmit, role = Role.Button, onClick = submit),
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = if (canSubmit) IosColors.spendOrange else IosColors.spendOrange.copy(alpha = 0.4f),
                )
            }
            GroupedFormCard {
                Text(stringResource(R.string.plan_planned_item), Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), style = IosText.caption.w(FontWeight.Bold), color = IosColors.textSecondary)
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { showTypeMenu = true }
                            .padding(horizontal = Spacing.m, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(stringResource(R.string.common_type), style = IosText.body, color = IosColors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(if (isIncome) stringResource(R.string.common_income) else stringResource(R.string.common_expense), style = IosText.body, color = IosColors.textSecondary)
                        Icon(SfSymbols.icon("chevron.up.chevron.down"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_expense)) },
                            onClick = {
                                showTypeMenu = false
                                isIncome = false
                                categoryId = categories.firstOrNull { !it.isIncome }?.id.orEmpty()
                            },
                            modifier = Modifier.testTag("monthPlan.oneTime.expense"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_income)) },
                            onClick = {
                                showTypeMenu = false
                                isIncome = true
                                categoryId = categories.firstOrNull { it.isIncome }?.id.orEmpty()
                            },
                            modifier = Modifier.testTag("monthPlan.oneTime.income"),
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                BasicFormRow(label = stringResource(R.string.common_description), tag = "monthPlan.oneTime.description") {
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it.take(200); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = doneKeyboardOptions(),
                        keyboardActions = rememberKeyboardDoneActions(),
                        textStyle = IosText.body.copy(color = IosColors.textPrimary),
                        cursorBrush = SolidColor(IosColors.spendOrange),
                        decorationBox = { inner -> Box { if (description.isEmpty()) Text(stringResource(R.string.common_description), style = IosText.body, color = IosColors.textTertiary); inner() } },
                    )
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                BasicFormRow(label = stringResource(R.string.common_amount), tag = "monthPlan.oneTime.amount") {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                        keyboardActions = rememberKeyboardDoneActions(),
                        textStyle = IosText.body.copy(color = IosColors.textPrimary),
                        cursorBrush = SolidColor(IosColors.spendOrange),
                        decorationBox = { inner -> Box { if (amountText.isEmpty()) Text(stringResource(R.string.common_amount), style = IosText.body, color = IosColors.textTertiary); inner() } },
                    )
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { showCategoryPicker = true }
                            .testTag("monthPlan.oneTime.category").padding(horizontal = Spacing.m, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(stringResource(R.string.common_category), style = IosText.body, color = IosColors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        val selectedCategory = availableCategories.firstOrNull { it.id == categoryId }
                        if (selectedCategory != null) {
                            CategorySymbolBadge(selectedCategory.icon, selectedCategory.name, selectedCategory.color, size = 28.dp)
                            Text(selectedCategory.name, style = IosText.body, color = IosColors.textPrimary)
                        } else {
                            Text(stringResource(R.string.plan_select), style = IosText.body, color = IosColors.textSecondary)
                        }
                        Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showCategoryPicker, onDismissRequest = { showCategoryPicker = false }) {
                        availableCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name, style = IosText.subheadline) },
                                leadingIcon = { CategorySymbolBadge(category.icon, category.name, category.color, size = 32.dp) },
                                onClick = { categoryId = category.id; showCategoryPicker = false },
                                modifier = Modifier.testTag("monthPlan.oneTime.category.${category.id}"),
                            )
                        }
                    }
                }
                if (availableCategories.isEmpty()) {
                    Text(stringResource(R.string.plan_no_categories_available_add_one), Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), style = IosText.footnote, color = IosColors.textSecondary)
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { showDatePicker = true }
                        .testTag("monthPlan.oneTime.date").padding(horizontal = Spacing.m, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.common_date), style = IosText.body, color = IosColors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = IosText.body, color = IosColors.textSecondary)
                }
            }
            Text(
                stringResource(R.string.plan_this_goes_into_your_draft),
                Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
                style = IosText.footnote,
                color = IosColors.textSecondary,
            )
            error?.let {
                Text(it, Modifier.testTag("monthPlan.oneTime.error"), style = IosText.caption, color = IosColors.expense)
            }
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

@Composable
private fun GroupedFormCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun BasicFormRow(label: String, tag: String, field: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(tag).padding(horizontal = Spacing.m, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(label, style = IosText.body, color = IosColors.textPrimary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { field() }
    }
}


