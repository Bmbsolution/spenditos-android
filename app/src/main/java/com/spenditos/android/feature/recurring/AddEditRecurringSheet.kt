package com.spenditos.android.feature.recurring

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.WheelPicker
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
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

/** Survives rotation/process death as plain Bundle values (date as epoch day, amount as text). */
internal val RecurringDraftSaver = mapSaver<RecurringDraft>(
    save = { draft ->
        mapOf(
            "description" to draft.description,
            "amountText" to draft.amountText,
            "amountVaries" to draft.amountVaries,
            "frequency" to draft.frequency,
            "isIncome" to draft.isIncome,
            "categoryId" to draft.categoryId,
            "firstOccurrenceDate" to draft.firstOccurrenceDate.toEpochDay(),
            "isActive" to draft.isActive,
            "repeatIntervalText" to draft.repeatIntervalText,
            "occurrencesRemainingText" to draft.occurrencesRemainingText,
            "secondOccurrenceDayText" to draft.secondOccurrenceDayText,
        )
    },
    restore = { map ->
        RecurringDraft(
            description = map["description"] as String? ?: "",
            amountText = map["amountText"] as String? ?: "",
            amountVaries = map["amountVaries"] as Boolean? ?: false,
            frequency = map["frequency"] as String? ?: "monthly",
            isIncome = map["isIncome"] as Boolean? ?: false,
            categoryId = map["categoryId"] as String?,
            firstOccurrenceDate = (map["firstOccurrenceDate"] as Long?)?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
            isActive = map["isActive"] as Boolean? ?: true,
            repeatIntervalText = map["repeatIntervalText"] as String? ?: "1",
            occurrencesRemainingText = map["occurrencesRemainingText"] as String? ?: "",
            secondOccurrenceDayText = map["secondOccurrenceDayText"] as String? ?: "15",
        )
    },
)

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
    var draft by rememberSaveable(template?.id, editIsIncome, initialIsIncome, stateSaver = RecurringDraftSaver) {
        mutableStateOf(
            template?.let { RecurringDraft.from(it, categories.firstOrNull { category -> category.id == it.categoryId }?.isIncome == true) }
                // No preselected category (iOS starts empty): Save stays disabled until the user picks one,
                // so a new bill never lands in whichever category happens to sort first.
                ?: RecurringDraft(isIncome = initialIsIncome),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showSecondDayPicker by remember { mutableStateOf(false) }
    val frequencyOptions = listOf("daily", "weekly", "bi-weekly", "semi-monthly", "monthly", "quarterly", "semi-annually", "yearly")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        DismissKeyboardWhenInactive()
        DismissKeyboardWhen(showDatePicker || showCategoryPicker || showSecondDayPicker)
        Column(
            Modifier.fillMaxWidth().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
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
                    iconName = "text.alignleft",
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
                        keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                        keyboardActions = rememberKeyboardDoneActions(),
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
                    com.spenditos.android.ui.components.IosSwitch(
                        checked = draft.amountVaries,
                        onCheckedChange = { draft = draft.copy(amountVaries = it) },
                        modifier = Modifier.testTag("recurring.form.amountVaries"),
                        tint = IosColors.spendOrange,
                    )
                }
                val lowerDescription = draft.description.lowercase()
                val everydayHint = !draft.isIncome && listOf("gas", "fuel", "grocer", "food", "dining", "restaurant", "coffee", "takeout").any(lowerDescription::contains)
                if (everydayHint) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).background(IosColors.spendOrange.copy(alpha = 0.08f)).padding(Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(SfSymbols.icon("lightbulb"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(
                            "Logging each purchase yourself? Skip this bill. What you log shows up in Transactions and Reports.",
                            style = IosText.caption,
                            color = IosColors.textSecondary,
                        )
                    }
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
                                    Text(frequencyLabel(frequency), style = IosText.callout, color = if (selected) IosColors.textPrimary else IosColors.textSecondary)
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
                        .clickable(role = Role.Button) { showCategoryPicker = true }
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
                        Icon(SfSymbols.icon("square.grid.2x2"), contentDescription = null, tint = IosColors.textSecondary)
                        Text("Select a category", style = IosText.body, color = IosColors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
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
                    com.spenditos.android.ui.components.IosSwitch(
                        checked = draft.isActive,
                        onCheckedChange = { draft = draft.copy(isActive = it) },
                        modifier = Modifier.testTag("recurring.form.active"),
                        tint = IosColors.spendOrange,
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
                            suffix = draft.frequency.lowercase(),
                            tag = "recurring.form.repeatInterval",
                            onValueChange = { draft = draft.copy(repeatIntervalText = it.filter(Char::isDigit)) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = Color.Black.copy(alpha = 0.06f))
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
                            HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = Color.Black.copy(alpha = 0.06f))
                            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(SfSymbols.icon("calendar.badge.plus"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(24.dp))
                                    Text("2nd Day of Month", Modifier.padding(start = Spacing.s), style = IosText.body, color = IosColors.textPrimary)
                                    Text("Required", Modifier.padding(start = Spacing.xs), style = IosText.caption.w(FontWeight.SemiBold), color = Color(0xFFFF9500))
                                }
                                val day = draft.secondOccurrenceDay
                                val dayShape = RoundedCornerShape(Radius.s)
                                Row(
                                    Modifier.fillMaxWidth().clip(dayShape)
                                        .background(IosColors.spendOrange.copy(alpha = 0.08f))
                                        .border(1.dp, IosColors.spendOrange.copy(alpha = 0.7f), dayShape)
                                        .clickable(role = Role.Button) { showSecondDayPicker = true }
                                        .testTag("recurring.form.secondDay")
                                        .padding(horizontal = Spacing.m, vertical = Spacing.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                ) {
                                    Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.spendOrange)
                                    Text(
                                        if (day == null) "Select a day" else "Day $day",
                                        Modifier.weight(1f),
                                        style = IosText.callout.w(FontWeight.SemiBold),
                                        color = if (day == null) IosColors.textSecondary else IosColors.textPrimary,
                                    )
                                    Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                "Choose the other day this transaction occurs each month.",
                                Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s),
                                style = IosText.caption,
                                color = IosColors.textSecondary,
                            )
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

    if (showCategoryPicker) {
        RecurringCategoryPickerSheet(
            categories = categories,
            isIncome = draft.isIncome,
            selectedId = draft.categoryId,
            onTypeChange = { isIncome -> draft = draft.copy(isIncome = isIncome, categoryId = null) },
            onSelect = { category ->
                draft = draft.copy(isIncome = category.isIncome, categoryId = category.id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
    if (showSecondDayPicker) {
        SecondDayPickerSheet(
            initialDay = draft.secondOccurrenceDay ?: 1,
            onDismiss = { showSecondDayPicker = false },
            onDone = { day ->
                draft = draft.copy(secondOccurrenceDayText = day.toString())
                showSecondDayPicker = false
            },
        )
    }
}

@Composable
private fun RecurringCategoryPickerSheet(
    categories: List<Category>,
    isIncome: Boolean,
    selectedId: String?,
    onTypeChange: (Boolean) -> Unit,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var typeMenu by remember { mutableStateOf(false) }
    val filtered = categories.filter { it.isIncome == isIncome && (search.isBlank() || it.name.contains(search, ignoreCase = true)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        DismissKeyboardWhenInactive()
        Column(Modifier.fillMaxWidth().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll().padding(bottom = 32.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Select Category", style = IosText.headline, color = IosColors.textPrimary)
                }
                Text("Done", Modifier.clickable(role = Role.Button, onClick = onDismiss), style = IosText.body, color = IosColors.spendOrange)
            }
            Box(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.m)) {
                Row(
                    Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .clickable(role = Role.Button) { typeMenu = true }
                        .padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Icon(SfSymbols.icon(if (isIncome) "arrow.down" else "arrow.up"), contentDescription = null, tint = IosColors.textSecondary)
                    Text(if (isIncome) "Income Categories" else "Expense Categories", Modifier.weight(1f), style = IosText.body, color = IosColors.textPrimary)
                    Icon(SfSymbols.icon("chevron.down"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Expense") },
                        onClick = { typeMenu = false; onTypeChange(false) },
                        modifier = Modifier.testTag("recurring.form.type.bill"),
                    )
                    DropdownMenuItem(
                        text = { Text("Income") },
                        onClick = { typeMenu = false; onTypeChange(true) },
                        modifier = Modifier.testTag("recurring.form.type.income"),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.m)
                    .then(subtleGlassModifier(RoundedCornerShape(Radius.m))).padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary)
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = doneKeyboardOptions(),
                    keyboardActions = rememberKeyboardDoneActions(),
                    textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                    cursorBrush = SolidColor(IosColors.spendOrange),
                    decorationBox = { inner ->
                        Box {
                            if (search.isEmpty()) Text("Search categories...", style = IosText.callout, color = IosColors.textTertiary)
                            inner()
                        }
                    },
                )
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Icon(SfSymbols.icon("square.grid.2x2"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(40.dp))
                    Text("No categories found", Modifier.testTag("recurring.form.categories.empty"), style = IosText.body, color = IosColors.textSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = Spacing.screen).padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(filtered, key = Category::id) { category ->
                        val selected = category.id == selectedId
                        val tint = categoryColor(category.color)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                                .background(if (selected) tint.copy(alpha = 0.15f) else Color.Transparent)
                                .border(if (selected) 1.5.dp else 0.5.dp, if (selected) tint else Color.Black.copy(alpha = 0.06f), RoundedCornerShape(Radius.m))
                                .clickable(role = Role.Button) { onSelect(category) }
                                .testTag("recurring.form.category.${category.id}")
                                .padding(vertical = Spacing.m),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = if (selected) 0.4f else 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(CategoryIcons.symbol(category.icon, category.name), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                category.name,
                                style = IosText.callout,
                                color = if (selected) IosColors.textPrimary else IosColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondDayPickerSheet(initialDay: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var day by remember { mutableStateOf(initialDay.coerceIn(1, 31)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", Modifier.clickable(role = Role.Button, onClick = onDismiss), style = IosText.body, color = IosColors.spendOrange)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Choose second day", style = IosText.headline, color = IosColors.textPrimary)
                }
                Text("Done", Modifier.clickable(role = Role.Button) { onDone(day) }, style = IosText.body.w(FontWeight.SemiBold), color = IosColors.spendOrange)
            }
            Text(
                "Choose the other day this transaction occurs each month.",
                Modifier.padding(horizontal = Spacing.screen),
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            WheelPicker(
                labels = (1..31).map { "Day $it" },
                selectedIndex = day - 1,
                onSelect = { day = it + 1 },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                wrap = true,
            )
        }
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
            keyboardOptions = doneKeyboardOptions(),
            keyboardActions = rememberKeyboardDoneActions(),
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
            keyboardOptions = doneKeyboardOptions(KeyboardType.Number),
            keyboardActions = rememberKeyboardDoneActions(),
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
