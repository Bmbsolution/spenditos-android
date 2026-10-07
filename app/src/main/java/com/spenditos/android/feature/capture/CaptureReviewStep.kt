package com.spenditos.android.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun CaptureReviewStep(
    items: List<CaptureReviewItem>,
    categories: List<Category>,
    currency: String,
    onIncludeChange: (String) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onDescriptionChange: (String, String) -> Unit,
    onDateChange: (String, LocalDate) -> Unit,
    onCategoryChange: (String, String?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth().testTag("capture.review.list"),
    ) {
        items.forEach { item ->
            val key = item.idempotencyKeyId
            var showCategoryPicker by remember(key) { mutableStateOf(false) }
            var categorySearchText by remember(key) { mutableStateOf("") }
            var showDatePicker by remember(key) { mutableStateOf(false) }
            val date = runCatching { LocalDate.parse(item.date) }.getOrDefault(LocalDate.now())
            val income = item.transactionType.equals("income", ignoreCase = true)
            val choices = categories.filter { it.isIncome == income }
            val selectedCategory = choices.firstOrNull { it.id == item.categoryId }
            val categoryLabel = selectedCategory?.name ?: item.categoryHint?.takeIf { it.isNotBlank() } ?: "Select category"
            val typeTint = if (income) IosColors.income else IosColors.expense

            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("capture.item.$key"),
                shape = RoundedCornerShape(Radius.l),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Box(
                            Modifier.size(24.dp).clickable(
                                enabled = !item.isFrozen,
                                role = Role.Button,
                                onClick = { onIncludeChange(key) },
                            ).semantics { selected = item.include }.testTag("capture.include.$key"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (item.include) SfSymbols.icon("checkmark.circle.fill") else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (item.include) IosColors.spendOrange else IosColors.textTertiary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        CategorySymbolBadge(
                            icon = selectedCategory?.icon ?: CategoryIcons.sfSymbolName(null, categoryLabel),
                            name = categoryLabel,
                            colorHex = selectedCategory?.color,
                            size = 44.dp,
                        )
                        Column(Modifier.weight(1f).alpha(if (item.include) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                item.description.ifBlank { "Transaction" },
                                style = IosText.body.w(FontWeight.SemiBold),
                                color = IosColors.textPrimary,
                                maxLines = 1,
                            )
                            val subtitle = listOf(categoryLabel, item.date).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(subtitle, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1)
                        }
                        Text(
                            summaryAmountText(item.amountText, currency, income),
                            style = IosText.rounded(17, FontWeight.Medium),
                            color = typeTint,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                        )
                    }

                    ReviewInputField(
                        value = item.amountText,
                        onValueChange = { onAmountChange(key, it) },
                        label = "Amount ($currency)",
                        enabled = !item.isFrozen,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("capture.amount.$key"),
                        textStyle = IosText.rounded(20, FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    )
                    ReviewInputField(
                        value = item.description,
                        onValueChange = { onDescriptionChange(key, it) },
                        label = "Description",
                        enabled = !item.isFrozen,
                        modifier = Modifier.testTag("capture.description.$key"),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Category", style = IosText.subheadline, color = IosColors.textSecondary)
                        ReviewCategorySelectionRow(
                            selectedCategory = selectedCategory,
                            hint = item.categoryHint,
                            onClick = { showCategoryPicker = true },
                            modifier = Modifier.testTag("capture.category.$key"),
                            enabled = !item.isFrozen,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Date", style = IosText.subheadline, color = IosColors.textSecondary)
                        ReviewChoice(
                            text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            symbol = "calendar",
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("capture.date.$key"),
                            enabled = !item.isFrozen,
                        )
                    }

                    if (!item.currency.isNullOrBlank() && !item.currency.equals(currency, ignoreCase = true)) {
                        Text("Parsed as ${item.currency.uppercase()}", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("capture.currency.$key"))
                    }
                    if (item.isFrozen) Text("Retry to confirm this save before editing.", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("capture.frozen.$key"))
                    item.failureReason?.let { Text(it, color = IosColors.expense, style = IosText.footnote, modifier = Modifier.testTag("capture.failure.$key")) }
                }
            }

            if (showDatePicker) {
                val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { onDateChange(key, LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                            showDatePicker = false
                        }, modifier = Modifier.testTag("capture.date.confirm.$key")) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }, modifier = Modifier.testTag("capture.date.cancel.$key")) { Text("Cancel") } },
                ) { DatePicker(state = pickerState, modifier = Modifier.testTag("capture.date.picker.$key")) }
            }

            if (showCategoryPicker) {
                val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val filtered = choices.filter { it.name.contains(categorySearchText.trim(), ignoreCase = true) }
                ModalBottomSheet(onDismissRequest = { showCategoryPicker = false }, sheetState = pickerState) {
                    Column(
                        Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(44.dp))
                            Text("Select Category", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            TextButton(onClick = { showCategoryPicker = false }, modifier = Modifier.width(64.dp)) {
                                Text("Done", style = IosText.body.w(FontWeight.Medium), color = IosColors.spendOrange)
                            }
                        }
                        ReviewCategorySearch(
                            value = categorySearchText,
                            onValueChange = { categorySearchText = it },
                        )
                        ReviewChoice(
                            text = "Uncategorized",
                            symbol = "tag",
                            onClick = {
                                onCategoryChange(key, null)
                                showCategoryPicker = false
                            },
                            modifier = Modifier.testTag("capture.category.none.$key"),
                            trailingSymbol = if (item.categoryId == null) "checkmark.circle.fill" else null,
                        )
                        if (filtered.isEmpty()) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Outlined.GridView, contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(48.dp))
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
                                items(filtered, key = { it.id }) { category ->
                                    ReviewCategoryCell(
                                        category = category,
                                        selected = item.categoryId == category.id,
                                        onClick = {
                                            onCategoryChange(key, category.id)
                                            showCategoryPicker = false
                                            categorySearchText = ""
                                        },
                                        modifier = Modifier.testTag("capture.category.${category.id}"),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.m))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = IosText.body,
    textAlign: TextAlign = TextAlign.Start,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = IosText.footnote, color = IosColors.textSecondary)
        GlassCard(shape = RoundedCornerShape(Radius.m)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                textStyle = textStyle.copy(color = IosColors.textPrimary, textAlign = textAlign),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 15.dp).then(modifier),
            )
        }
    }
}

@Composable
private fun ReviewCategorySelectionRow(
    selectedCategory: Category?,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (selectedCategory != null) {
                CategorySymbolBadge(selectedCategory.icon, selectedCategory.name, selectedCategory.color, size = 36.dp)
                Text(selectedCategory.name, style = IosText.body, color = IosColors.textPrimary)
            } else {
                Icon(SfSymbols.icon("tag"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(hint ?: "Select category", style = IosText.body, color = IosColors.textPrimary)
                    if (hint != null) Text("Suggested category", style = IosText.caption, color = IosColors.textSecondary)
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReviewChoice(
    text: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingSymbol: String? = null,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(SfSymbols.icon(symbol), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
            Text(text, style = IosText.body, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
            trailingSymbol?.let { Icon(SfSymbols.icon(it), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun ReviewCategorySearch(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        Modifier.fillMaxWidth().then(subtleGlassModifier(shape)).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
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
                modifier = Modifier.size(18.dp).clickable(role = Role.Button) { onValueChange("") },
            )
        }
    }
}

@Composable
private fun ReviewCategoryCell(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                )
            }
        }
        Text(category.name, style = IosText.body, color = IosColors.textPrimary, maxLines = 1)
    }
}

private fun summaryAmountText(amountText: String, currency: String, income: Boolean): String {
    val sign = if (income) "+" else "−"
    val amount = amountText.trim().replace(',', '.').toDoubleOrNull() ?: return sign + "—"
    val formatted = runCatching {
        NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency.uppercase()) }.format(amount)
    }.getOrDefault("${currency.uppercase()} ${"%.2f".format(amount)}")
    return sign + formatted
}
