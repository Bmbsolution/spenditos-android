package com.spenditos.android.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
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
    initialIsIncome: Boolean = false,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (RecurringDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editIsIncome = template?.let { item -> categories.firstOrNull { it.id == item.categoryId }?.isIncome == true }
    var draft by remember(template?.id, editIsIncome, initialIsIncome) {
        mutableStateOf(
            template?.let { RecurringDraft.from(it, categories.firstOrNull { category -> category.id == it.categoryId }?.isIncome == true) }
                ?: RecurringDraft(
                    isIncome = initialIsIncome,
                    categoryId = categories.firstOrNull { it.isIncome == initialIsIncome }?.id,
                ),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryChoices by remember { mutableStateOf(false) }
    val visibleCategories = categories.filter { it.isIncome == draft.isIncome }
    val frequencyOptions = listOf("daily", "weekly", "bi-weekly", "semi-monthly", "monthly", "quarterly", "semi-annually", "yearly")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
                .padding(top = Spacing.s, bottom = 32.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", Modifier.clickable(role = Role.Button, onClick = onDismiss), style = IosText.body, color = IosColors.spendOrange)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (template == null) "New income or bill" else "Edit income or bill", style = IosText.headline, color = IosColors.textPrimary)
                }
                Spacer(Modifier.width(48.dp))
            }
            error?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(IosColors.expense.copy(alpha = 0.10f))
                        .padding(Spacing.m).testTag("recurring.form.error"),
                    style = IosText.subheadline,
                    color = IosColors.expense,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("Description")
                RecurringTextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it.take(160)) },
                    placeholder = "e.g., Netflix, Rent, Salary",
                    tag = "recurring.form.description",
                    iconName = "list.bullet",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("Amount")
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(currencySymbol(currency), style = IosText.rounded(22, FontWeight.SemiBold), color = IosColors.textTertiary)
                    BasicTextField(
                        value = draft.amountText,
                        onValueChange = { draft = draft.copy(amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                        modifier = Modifier.weight(1f).testTag("recurring.form.amount"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = IosText.rounded(34, FontWeight.Bold).copy(color = IosColors.textPrimary),
                        cursorBrush = SolidColor(IosColors.spendOrange),
                        decorationBox = { inner ->
                            Box {
                                if (draft.amountText.isEmpty()) Text("0.00", style = IosText.rounded(34, FontWeight.Bold), color = IosColors.textTertiary)
                                inner()
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("The amount changes each time", style = IosText.subheadline, color = IosColors.textPrimary)
                        Text("Shown as ≈. You confirm the real amount when it's logged.", style = IosText.caption, color = IosColors.textTertiary)
                    }
                    Switch(
                        checked = draft.amountVaries,
                        onCheckedChange = { draft = draft.copy(amountVaries = it) },
                        modifier = Modifier.testTag("recurring.form.amountVaries"),
                        colors = SwitchDefaults.colors(checkedTrackColor = IosColors.spendOrange),
                    )
                }
                val lowerDescription = draft.description.lowercase()
                val everydayHint = !draft.isIncome && listOf("gas", "fuel", "grocer", "food", "dining", "restaurant", "coffee", "takeout").any(lowerDescription::contains)
                if (everydayHint) {
                    Text(
                        "Logging each purchase yourself? Skip this bill. What you log shows up as everyday spending in Month plan.",
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).background(IosColors.spendOrange.copy(alpha = 0.08f)).padding(Spacing.s),
                        style = IosText.caption,
                        color = IosColors.textSecondary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("Frequency")
                Column(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m))).padding(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    frequencyOptions.chunked(2).forEach { frequencyRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            frequencyRow.forEach { frequency ->
                                val selected = draft.frequency == frequency
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.s))
                                        .background(if (selected) IosColors.spendOrange.copy(alpha = 0.25f) else Color.Transparent)
                                        .border(
                                            if (selected) 1.5.dp else 0.5.dp,
                                            if (selected) IosColors.spendOrange.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.06f),
                                            RoundedCornerShape(Radius.s),
                                        )
                                        .clickable(role = Role.Button) { draft = draft.copy(frequency = frequency) }
                                        .testTag("recurring.form.frequency.$frequency")
                                        .padding(vertical = Spacing.m),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(frequencyLabel(frequency), style = IosText.callout, color = if (selected) Color.White else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("Category")
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .clickable(role = Role.Button) { showCategoryChoices = !showCategoryChoices }
                        .testTag("recurring.form.category.selector").padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    val selectedCategory = categories.firstOrNull { it.id == draft.categoryId }
                    if (selectedCategory != null) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(selectedCategory.color)))
                        Icon(CategoryIcons.symbol(selectedCategory.icon, selectedCategory.name), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(18.dp))
                        Text(selectedCategory.name, style = IosText.body, color = IosColors.textPrimary)
                    } else {
                        Icon(SfSymbols.icon("circle.grid.3x3"), contentDescription = null, tint = IosColors.textSecondary)
                        Text("Select a category", style = IosText.body, color = IosColors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
                }
                if (showCategoryChoices) {
                    if (visibleCategories.isEmpty()) {
                        Text("No matching categories yet.", Modifier.testTag("recurring.form.categories.empty"), style = IosText.subheadline, color = IosColors.textSecondary)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            visibleCategories.forEach { category ->
                                val selected = draft.categoryId == category.id
                                Row(
                                    Modifier.clip(RoundedCornerShape(Radius.m))
                                        .background(if (selected) categoryColor(category.color).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable(role = Role.Button) { draft = draft.copy(categoryId = category.id); showCategoryChoices = false }
                                        .testTag("recurring.form.category.${category.id}")
                                        .padding(horizontal = Spacing.s, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    CategorySymbolBadge(category.icon, category.name, category.color, size = 32.dp)
                                    Text(category.name, style = IosText.caption, color = IosColors.textPrimary)
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                ) {
                    listOf(false, true).forEach { isIncome ->
                        val selected = draft.isIncome == isIncome
                        Box(
                            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(13.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable(role = Role.Tab) {
                                    draft = draft.copy(isIncome = isIncome, categoryId = null)
                                    showCategoryChoices = false
                                }
                                .testTag("recurring.form.type.${if (isIncome) "income" else "bill"}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (isIncome) "Income" else "Bill", style = IosText.subheadline.w(if (selected) FontWeight.SemiBold else FontWeight.Medium))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("First Occurrence")
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .clickable(role = Role.Button) { showDatePicker = true }
                        .testTag("recurring.form.date").padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textSecondary)
                    Text(draft.firstOccurrenceDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = IosText.body, color = IosColors.textPrimary)
                }
            }
            GlassCard {
                Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SfSymbols.icon("power"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(24.dp))
                    Text("Active", Modifier.weight(1f), style = IosText.body, color = IosColors.textPrimary)
                    Switch(
                        checked = draft.isActive,
                        onCheckedChange = { draft = draft.copy(isActive = it) },
                        modifier = Modifier.testTag("recurring.form.active"),
                        colors = SwitchDefaults.colors(checkedTrackColor = IosColors.spendOrange),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FormLabel("Advanced")
                GlassCard {
                    Column {
                        AdvancedNumberRow(
                            icon = "repeat",
                            title = "Repeat Every",
                            value = draft.repeatIntervalText,
                            placeholder = "1",
                            suffix = draft.frequency.replace('-', ' ').lowercase(),
                            tag = "recurring.form.repeatInterval",
                            onValueChange = { draft = draft.copy(repeatIntervalText = it.filter(Char::isDigit)) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator.copy(alpha = 0.45f))
                        AdvancedNumberRow(
                            icon = "number",
                            title = "End After",
                            value = draft.occurrencesRemainingText,
                            placeholder = "Never",
                            suffix = "times",
                            tag = "recurring.form.occurrencesRemaining",
                            onValueChange = { draft = draft.copy(occurrencesRemainingText = it.filter(Char::isDigit)) },
                        )
                        if (draft.frequency == "semi-monthly") {
                            HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator.copy(alpha = 0.45f))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(SfSymbols.icon("calendar.badge.clock"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(24.dp))
                                Text("2nd Day of Month", Modifier.padding(start = Spacing.s), style = IosText.body, color = IosColors.textPrimary)
                                Text("Required", Modifier.padding(start = Spacing.xs), style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.warning)
                                Spacer(Modifier.weight(1f))
                                BasicTextField(
                                    value = draft.secondOccurrenceDayText,
                                    onValueChange = { draft = draft.copy(secondOccurrenceDayText = it.filter(Char::isDigit).take(2)) },
                                    modifier = Modifier.width(48.dp).testTag("recurring.form.secondDay"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = IosText.body.copy(color = IosColors.textPrimary),
                                    cursorBrush = SolidColor(IosColors.spendOrange),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.m))
            RecurringSavePill(
                text = if (template == null) "Save" else "Save changes",
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
private fun FormLabel(text: String) {
    Text(text, style = IosText.subheadline, color = IosColors.textSecondary)
}

@Composable
private fun RecurringTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    iconName: String,
) {
    Row(
        Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m))).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon(iconName), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            singleLine = true,
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            cursorBrush = SolidColor(IosColors.spendOrange),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = IosText.callout, color = IosColors.textTertiary)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun AdvancedNumberRow(
    icon: String,
    title: String,
    value: String,
    placeholder: String,
    suffix: String,
    tag: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon(icon), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(24.dp))
        Text(title, style = IosText.body, color = IosColors.textPrimary)
        Spacer(Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(if (suffix == "times") 70.dp else 50.dp).testTag(tag),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = IosText.body.copy(color = IosColors.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.End),
            cursorBrush = SolidColor(IosColors.spendOrange),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = IosText.body, color = IosColors.textTertiary)
                    inner()
                }
            },
        )
        Text(suffix, Modifier.width(70.dp), style = IosText.subheadline, color = IosColors.textSecondary)
    }
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> runCatching { java.util.Currency.getInstance(currency).getSymbol() }.getOrDefault(currency)
}

@Composable
private fun RecurringSavePill(text: String, onClick: () -> Unit, enabled: Boolean, isLoading: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (enabled) IosColors.spendOrange else IosColors.spendOrange.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
        else Text(text, style = IosText.body.w(FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White)
    }
}

private fun frequencyLabel(value: String): String = when (value) {
    "bi-weekly" -> "Bi-weekly"
    "semi-monthly" -> "Semi-monthly"
    "semi-annually" -> "Semi-annually"
    else -> value.replaceFirstChar(Char::uppercase)
}
