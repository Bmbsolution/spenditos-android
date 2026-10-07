package com.spenditos.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    state: TransactionListUiState,
    onDismiss: () -> Unit,
    onSelectDateRange: (DateRangeFilter) -> Unit,
    onCustomStart: (LocalDate) -> Unit,
    onCustomEnd: (LocalDate) -> Unit,
    onQuickToday: () -> Unit,
    onQuickThisWeek: () -> Unit,
    onQuickThisMonth: () -> Unit,
    onQuickLastDays: (Long) -> Unit,
    onType: (EntryType?) -> Unit,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit,
    onMinAmount: (Double?) -> Unit,
    onMaxAmount: (Double?) -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (FilterPreset) -> Unit,
    onDeletePreset: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val filters = state.filters
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var categorySearch by remember { mutableStateOf("") }
    var showSave by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    LaunchedEffect(filters.presetEpoch) {
        minText = filters.minAmount?.let { "%.2f".format(Locale.US, it) } ?: ""
        maxText = filters.maxAmount?.let { "%.2f".format(Locale.US, it) } ?: ""
    }
    val locale = LocalConfiguration.current.locales[0]
    val symbol = currencySymbol(state.currency)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("transaction-filter-sheet"),
    ) {
        Column(Modifier.fillMaxWidth().testTag("transaction-filter-sheet")) {
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.s)) {
                Text(
                    "Filters",
                    modifier = Modifier.align(Alignment.Center),
                    style = IosText.headline,
                    color = IosColors.textPrimary,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text("Done", style = IosText.body, color = IosColors.spendOrange)
                }
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .padding(top = Spacing.m, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                if (filters.presets.isNotEmpty()) {
                    PresetsSection(filters.presets, onLoadPreset, onDeletePreset)
                }
                QuickDatesSection(onQuickToday, onQuickThisWeek, onQuickThisMonth, onQuickLastDays)
                DateRangeSection(
                    selected = filters.dateRange,
                    customStart = filters.customStart,
                    customEnd = filters.customEnd,
                    locale = locale,
                    onSelect = onSelectDateRange,
                    onPickStart = { pickingStart = true },
                    onPickEnd = { pickingEnd = true },
                )
                AmountRangeSection(
                    symbol = symbol,
                    minText = minText,
                    maxText = maxText,
                    onMin = { raw ->
                        val sanitized = sanitizeAmountInput(raw)
                        minText = sanitized
                        onMinAmount(sanitized.toDoubleOrNull())
                    },
                    onMax = { raw ->
                        val sanitized = sanitizeAmountInput(raw)
                        maxText = sanitized
                        onMaxAmount(sanitized.toDoubleOrNull())
                    },
                )
                TypeSection(filters.selectedType, onType)
                CategorySection(
                    categories = state.categories,
                    selected = filters.selectedCategoryIds,
                    type = filters.selectedType,
                    search = categorySearch,
                    onSearch = { categorySearch = it },
                    onToggle = onToggleCategory,
                    onClear = onClearCategories,
                )
                BottomActions(
                    onSave = { showSave = true },
                    onApply = onApply,
                    onReset = onReset,
                )
            }
        }
    }

    if (showSave) {
        SavePresetDialog(
            onDismiss = { showSave = false },
            onSave = { name ->
                onSavePreset(name)
                showSave = false
            },
        )
    }
    if (pickingStart) {
        LocalDateDialog(
            initial = filters.customStart,
            onDismiss = { pickingStart = false },
            onConfirm = {
                onCustomStart(it)
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        LocalDateDialog(
            initial = filters.customEnd,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                onCustomEnd(it)
                pickingEnd = false
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = IosText.headline, color = IosColors.textPrimary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsSection(
    presets: List<FilterPreset>,
    onLoad: (FilterPreset) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Saved Presets")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            presets.forEachIndexed { index, preset ->
                key(preset.id) {
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete(index)
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.s))
                                    .background(IosColors.expense)
                                    .padding(horizontal = Spacing.m),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    SfSymbols.icon("trash"),
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.2.dp),
                                )
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.s))
                                .background(Color.White.copy(alpha = 0.5f))
                                .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(Radius.s))
                                .clickable(role = Role.Button) { onLoad(preset) }
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Icon(
                                SfSymbols.icon("bookmark.fill"),
                                contentDescription = null,
                                tint = IosColors.spendOrange,
                                modifier = Modifier.size(14.4.dp),
                            )
                            Text(
                                preset.name,
                                modifier = Modifier.weight(1f),
                                style = IosText.callout,
                                color = IosColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                SfSymbols.icon("chevron.right"),
                                contentDescription = null,
                                tint = IosColors.textTertiary,
                                modifier = Modifier.size(14.4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDatesSection(
    onToday: () -> Unit,
    onThisWeek: () -> Unit,
    onThisMonth: () -> Unit,
    onLastDays: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Quick Dates")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            QuickDateButton("Today", onToday)
            QuickDateButton("This Week", onThisWeek)
            QuickDateButton("This Month", onThisMonth)
            QuickDateButton("Last 30d") { onLastDays(30) }
            QuickDateButton("Last 90d") { onLastDays(90) }
        }
    }
}

@Composable
private fun QuickDateButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.s))
            .background(Color.White.copy(alpha = 0.5f))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(Radius.s))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        style = IosText.subheadline,
        color = IosColors.textPrimary,
    )
}

@Composable
private fun DateRangeSection(
    selected: DateRangeFilter,
    customStart: LocalDate,
    customEnd: LocalDate,
    locale: Locale,
    onSelect: (DateRangeFilter) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    val medium = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Date Range")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DateRangeFilter.entries.forEach { filter ->
                val on = selected == filter
                Text(
                    filter.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.s))
                        .background(if (on) IosColors.spendOrange.copy(alpha = 0.3f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (on) IosColors.spendOrange else Color.Black.copy(alpha = 0.06f),
                            RoundedCornerShape(Radius.s),
                        )
                        .clickable(role = Role.Button) { onSelect(filter) }
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    style = IosText.callout,
                    color = if (on) Color.White else Color.Gray,
                )
            }
        }
        if (selected == DateRangeFilter.CUSTOM) {
            DateBoundRow("From", customStart.format(medium), onPickStart)
            DateBoundRow("To", customEnd.format(medium), onPickEnd)
        }
    }
}

@Composable
private fun DateBoundRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(subtleGlassModifier())
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = IosText.callout, color = IosColors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, style = IosText.callout, color = IosColors.spendOrange)
    }
}

@Composable
private fun AmountRangeSection(
    symbol: String,
    minText: String,
    maxText: String,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Amount Range")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            AmountField(symbol, minText, "Min", onMin, Modifier.weight(1f))
            AmountField(symbol, maxText, "Max", onMax, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AmountField(
    symbol: String,
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.then(subtleGlassModifier()).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(symbol, style = IosText.callout, color = IosColors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = IosText.callout, color = IosColors.textTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TypeSection(selected: EntryType?, onSelect: (EntryType?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Type")
        Row(
            modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).clip(RoundedCornerShape(Radius.m)),
        ) {
            TypeButton("All", selected == null, IosColors.spendOrange, Modifier.weight(1f)) { onSelect(null) }
            TypeButton("Income", selected == EntryType.INCOME, IosColors.income, Modifier.weight(1f)) {
                onSelect(EntryType.INCOME)
            }
            TypeButton("Expenses", selected == EntryType.EXPENSE, IosColors.expense, Modifier.weight(1f)) {
                onSelect(EntryType.EXPENSE)
            }
        }
    }
}

@Composable
private fun TypeButton(label: String, selected: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .background(if (selected) tint.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.m),
        style = IosText.callout,
        color = if (selected) Color.White else Color.Gray,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CategorySection(
    categories: List<Category>,
    selected: Set<String>,
    type: EntryType?,
    search: String,
    onSearch: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    val visible = categories.filter { category ->
        val typeOk = when (type) {
            EntryType.EXPENSE -> !category.isIncome
            EntryType.INCOME -> category.isIncome
            null -> true
        }
        typeOk && (search.isBlank() || category.name.contains(search, ignoreCase = true))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Categories")
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                Text(
                    "Clear",
                    modifier = Modifier.clickable(role = Role.Button, onClick = onClear),
                    style = IosText.subheadline,
                    color = IosColors.spendOrange,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(subtleGlassModifier(RoundedCornerShape(Radius.l)))
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                SfSymbols.icon("magnifyingglass"),
                contentDescription = null,
                tint = IosColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                decorationBox = { inner ->
                    Box {
                        if (search.isEmpty()) {
                            Text("Search categories...", style = IosText.callout, color = IosColors.textSecondary)
                        }
                        inner()
                    }
                },
            )
            if (search.isNotEmpty()) {
                Icon(
                    SfSymbols.icon("xmark.circle.fill"),
                    contentDescription = "Clear",
                    tint = IosColors.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(role = Role.Button) { onSearch("") },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            visible.forEach { category ->
                val on = category.id in selected
                val chip = categoryColor(category.color)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.s))
                        .background(if (on) chip.copy(alpha = 0.3f) else Color.Transparent)
                        .border(1.dp, if (on) chip else Color.Black.copy(alpha = 0.06f), RoundedCornerShape(Radius.s))
                        .clickable(role = Role.Button) { onToggle(category.id) }
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(chip))
                    Text(
                        category.name,
                        style = IosText.subheadline,
                        color = if (on) Color.White else Color.Gray,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActions(onSave: () -> Unit, onApply: () -> Unit, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, IosColors.spendOrange.copy(alpha = 0.4f), RoundedCornerShape(Radius.m))
                .clickable(role = Role.Button, onClick = onSave)
                .padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                SfSymbols.icon("bookmark"),
                contentDescription = null,
                tint = IosColors.spendOrange,
                modifier = Modifier.size(20.4.dp),
            )
            Text("Save Preset", style = IosText.headline, color = IosColors.spendOrange)
        }
        Text(
            "Apply Filters",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.m))
                .background(IosColors.spendOrange)
                .clickable(role = Role.Button, onClick = onApply)
                .padding(Spacing.m),
            style = IosText.headline,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            "Reset All",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onReset)
                .padding(Spacing.m),
            style = IosText.headline,
            color = IosColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Enter a name for this filter preset.", style = IosText.callout, color = IosColors.textSecondary)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = IosText.body.copy(color = IosColors.textPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(subtleGlassModifier())
                        .padding(Spacing.m),
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) Text("Preset Name", style = IosText.body, color = IosColors.textTertiary)
                            inner()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) onSave(trimmed)
                else onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onConfirm(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "CAD" -> "CA$"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> runCatching { java.util.Currency.getInstance(currency.uppercase()).symbol }.getOrDefault(currency)
}
