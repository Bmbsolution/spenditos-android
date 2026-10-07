package com.spenditos.android.feature.transactions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onDone: (message: String) -> Unit,
    onScanReceipt: (() -> Unit)? = null,
    onCapture: (() -> Unit)? = null,
    prefill: AddTransactionPrefill? = null,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }

    // Categories arrive from the Room cache, so apply the prefill once they are available.
    LaunchedEffect(prefill, state.categories.isNotEmpty()) {
        if (prefill != null && state.categories.isNotEmpty()) viewModel.applyPrefill(prefill)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is AddTransactionEvent.Done) onDone(event.message) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
                .imePadding().navigationBarsPadding().padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "New transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.xs),
                textAlign = TextAlign.Center,
            )
            if (onScanReceipt != null || onCapture != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    onScanReceipt?.let { scan ->
                        MethodPill(
                            label = "Scan a receipt",
                            icon = Icons.Filled.CameraAlt,
                            onClick = scan,
                            modifier = Modifier.weight(1f).testTag("add.scanReceipt"),
                        )
                    }
                    onCapture?.let { capture ->
                        MethodPill(
                            label = "Type or speak",
                            icon = Icons.Filled.Mic,
                            onClick = capture,
                            modifier = Modifier.weight(1f).testTag("add.capture"),
                        )
                    }
                }
            }
            EntryTypeSelector(selected = state.type, onSelect = viewModel::onTypeChange)

            IosEntryField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = "Amount (${state.currency})",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black),
                placeholder = "0",
                modifier = Modifier.testTag("add.amount"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Category", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.visibleCategories.isEmpty()) {
                    Text(
                        "No ${if (state.type == EntryType.INCOME) "income" else "expense"} categories yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    state.visibleCategories.forEach { category ->
                        CategoryPill(
                            category = category,
                            selected = state.categoryId == category.id,
                            onClick = { viewModel.onCategorySelect(category.id) },
                            modifier = Modifier.testTag("add.category"),
                        )
                    }
                }
            }

            IosEntryField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Description (optional)",
                singleLine = true,
                modifier = Modifier.testTag("add.description"),
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { showDatePicker = true }),
                shape = RoundedCornerShape(Radius.m),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.date == LocalDate.now()) "Today" else state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            state.error?.let { AddErrorBanner(it, Modifier.testTag("add.error")) }
            SavePill(
                saving = state.isSaving,
                enabled = state.canSave,
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().testTag("add.save"),
            )
        }
    }

    if (showDatePicker) {
        // DatePicker works in UTC millis; convert as a calendar date, never through the local zone.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun EntryTypeSelector(selected: EntryType, onSelect: (EntryType) -> Unit) {
    val finance = LocalFinanceColors.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EntryType.entries.forEach { type ->
            val isSelected = selected == type
            val tint = if (type == EntryType.EXPENSE) finance.expense else finance.income
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(type) }
                    .testTag("add.type.${type.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (type == EntryType.EXPENSE) "Expense" else "Income",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun IosEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(shape = RoundedCornerShape(Radius.m)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 14.dp).then(modifier),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryPill(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = categoryColor(category.color)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(shape)
            .background(if (selected) tint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(BorderStroke(1.dp, if (selected) tint.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (!category.icon.isNullOrBlank()) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(category.icon, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            IconChip(
                icon = androidx.compose.material.icons.Icons.Filled.Sell,
                tint = tint,
                size = 24.dp,
                shape = RoundedCornerShape(8.dp),
            )
        }
        Text(category.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MethodPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.height(52.dp).clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.size(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AddErrorBanner(message: String, modifier: Modifier = Modifier) {
    val finance = LocalFinanceColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
            .background(finance.expense.copy(alpha = 0.12f))
            .border(1.dp, finance.expense.copy(alpha = 0.4f), RoundedCornerShape(Radius.m))
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = finance.expense, modifier = Modifier.padding(end = Spacing.s))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
    }
}

@Composable
private fun SavePill(saving: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val enabledTint = MaterialTheme.colorScheme.primary
    Box(
        modifier.height(52.dp).clip(CircleShape)
            .background(if (enabled || saving) enabledTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        else Text("Save", style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
