package com.spenditos.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    var showCategoryPicker by remember { mutableStateOf(false) }
    var categorySearchText by remember { mutableStateOf("") }
    val quickCategories = remember(state.visibleCategories, state.categoryId) {
        val selected = state.visibleCategories.firstOrNull { it.id == state.categoryId }
        val rest = state.visibleCategories.filterNot { it.id == state.categoryId }
        (listOfNotNull(selected) + rest).take(4)
    }

    // Categories arrive from the Room cache, so apply the prefill once they are available.
    LaunchedEffect(prefill, state.categories.isNotEmpty()) {
        if (prefill != null && state.categories.isNotEmpty()) viewModel.applyPrefill(prefill)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is AddTransactionEvent.Done) onDone(event.message) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                Modifier.padding(top = Spacing.s).size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.06f)),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().imePadding().navigationBarsPadding(),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Box(Modifier.fillMaxWidth().height(44.dp).padding(top = Spacing.xs)) {
                    Text(
                        if (state.type == EntryType.INCOME) "Add income" else "Add expense",
                        style = IosText.headline,
                        color = IosColors.textPrimary,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 44.dp),
                        textAlign = TextAlign.Center,
                    )
                    Icon(
                        SfSymbols.icon("xmark"),
                        contentDescription = "Close",
                        tint = IosColors.textPrimary,
                        modifier = Modifier.align(Alignment.CenterStart).size(44.dp)
                            .clickable(role = Role.Button, onClick = onDismiss).padding(12.dp),
                    )
                }

                EntryTypeSelector(selected = state.type, onSelect = viewModel::onTypeChange)

                state.error?.let { AddErrorBanner(it, Modifier.testTag("add.error")) }

                if (onScanReceipt != null || onCapture != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Text(
                            "How would you like to add it?",
                            style = IosText.title3.w(FontWeight.SemiBold),
                            color = IosColors.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                            onCapture?.let { capture ->
                                MethodOptionCard(
                                    title = "Type or speak",
                                    subtitle = "Say or enter the details",
                                    symbol = "mic.fill",
                                    tint = Color(0xFF1565C0),
                                    modifier = Modifier.weight(1f).testTag("add.capture"),
                                    onClick = capture,
                                )
                            }
                            onScanReceipt?.let { scan ->
                                MethodOptionCard(
                                    title = "Scan Receipt",
                                    subtitle = "Extract from photo",
                                    symbol = "camera.viewfinder",
                                    tint = IosColors.income,
                                    modifier = Modifier.weight(1f).testTag("add.scanReceipt"),
                                    onClick = scan,
                                )
                            }
                        }
                    }
                }

                HeroAmountField(
                    value = state.amountText,
                    currency = state.currency,
                    onValueChange = viewModel::onAmountChange,
                    modifier = Modifier.testTag("add.amount"),
                )

                DescriptionField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.testTag("add.description"),
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Category", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        quickCategories.forEach { category ->
                            QuickCategoryChip(
                                category = category,
                                selected = state.categoryId == category.id,
                                onClick = { viewModel.onCategorySelect(category.id) },
                                modifier = Modifier.testTag("add.category"),
                            )
                        }
                        val noCategories = state.visibleCategories.isEmpty()
                        QuickCategoryChip(
                            title = if (noCategories) "Add a category" else "More",
                            symbol = if (noCategories) "plus" else "square.grid.2x2",
                            onClick = { showCategoryPicker = true },
                        )
                    }
                }

                DateChoices(
                    date = state.date,
                    showPicker = showDatePicker,
                    onToday = { viewModel.onDateChange(LocalDate.now()); showDatePicker = false },
                    onYesterday = { viewModel.onDateChange(LocalDate.now().minusDays(1)); showDatePicker = false },
                    onPickDate = { showDatePicker = !showDatePicker },
                    onDatePicked = viewModel::onDateChange,
                )

                Spacer(Modifier.height(Spacing.m))
            }

            SavePill(
                saving = state.isSaving,
                enabled = state.canSave,
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s)
                    .testTag("add.save"),
            )
        }
    }

    if (showCategoryPicker) {
        val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val matching = state.visibleCategories.filter { it.name.contains(categorySearchText.trim(), ignoreCase = true) }
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = pickerState,
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(44.dp))
                    Text(
                        "Select Category",
                        style = IosText.headline,
                        color = IosColors.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { showCategoryPicker = false }, modifier = Modifier.width(64.dp)) {
                        Text("Done", style = IosText.body.w(FontWeight.Medium), color = IosColors.spendOrange)
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
                Spacer(Modifier.height(Spacing.m))
            }
        }
    }
}

@Composable
private fun EntryTypeSelector(selected: EntryType, onSelect: (EntryType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EntryType.entries.forEach { type ->
            val isSelected = selected == type
            val tint = if (type == EntryType.EXPENSE) IosColors.expense else IosColors.income
            Row(
                Modifier.weight(1f).height(42.dp).clip(CircleShape)
                    .background(if (isSelected) tint.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (isSelected) tint.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                    .clickable(role = Role.Tab) { onSelect(type) }
                    .testTag("add.type.${type.name.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SfSymbols.icon(if (type == EntryType.EXPENSE) "arrow.down" else "arrow.up"),
                    contentDescription = null,
                    tint = if (isSelected) tint else IosColors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    if (type == EntryType.EXPENSE) "Expense" else "Income",
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = if (isSelected) tint else IosColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MethodOptionCard(
    title: String,
    subtitle: String,
    symbol: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.l)
    Box(modifier) {
        GlassCard(
            modifier = Modifier.heightIn(min = 112.dp).clickable(role = Role.Button, onClick = onClick),
            shape = shape,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.s + 2.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp)
                            .dropShadow(RoundedCornerShape(Radius.m), Shadow(radius = 16.dp, color = tint, offset = DpOffset(0.dp, 4.dp), alpha = 0.3f))
                            .clip(RoundedCornerShape(Radius.m))
                            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.72f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(SfSymbols.icon("arrow.up.right"), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary, maxLines = 1)
                    Text(subtitle, style = IosText.caption, color = IosColors.textSecondary, maxLines = 2)
                }
            }
        }
        Box(Modifier.matchParentSize().border(1.dp, tint.copy(alpha = 0.18f), shape))
    }
}

@Composable
private fun HeroAmountField(
    value: String,
    currency: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val symbol = currencySymbol(currency)
    Column(modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                symbol,
                style = IosText.rounded(34, FontWeight.Bold),
                color = IosColors.textSecondary,
                modifier = Modifier.alignBy(FirstBaseline),
            )
            Spacer(Modifier.width(Spacing.s))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                interactionSource = interactionSource,
                textStyle = IosText.rounded(46, FontWeight.ExtraBold).copy(color = IosColors.textPrimary),
                modifier = Modifier.weight(1f).alignBy(FirstBaseline),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text("0", style = IosText.rounded(46, FontWeight.ExtraBold), color = IosColors.textTertiary.copy(alpha = 0.55f))
                        innerTextField()
                    }
                },
            )
        }
        Spacer(Modifier.height(Spacing.s))
        HorizontalDivider(
            thickness = if (focused) 2.dp else 1.dp,
            color = if (focused) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun QuickCategoryChip(
    category: Category? = null,
    title: String = category?.name.orEmpty(),
    symbol: String? = category?.let { CategoryIcons.sfSymbolName(it.icon, it.name) },
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Row(
        modifier.clip(shape)
            .background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f),
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (symbol != null) {
            Icon(
                if (symbol == "square.grid.2x2") Icons.Outlined.GridView else SfSymbols.icon(symbol),
                contentDescription = null,
                tint = if (selected) IosColors.spendOrange else IosColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            title,
            style = if (selected) IosText.callout.w(FontWeight.SemiBold) else IosText.callout,
            color = if (selected) IosColors.spendOrange else IosColors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryGridCell(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                    modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                )
            }
        }
        Text(category.name, style = IosText.callout, color = IosColors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun CategorySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        modifier.fillMaxWidth().then(subtleGlassModifier(shape)).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(19.dp))
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
                modifier = Modifier.size(20.dp).clickable(role = Role.Button) { onValueChange("") },
            )
        }
    }
}

@Composable
private fun DescriptionField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
        Text("Merchant or description", style = IosText.subheadline, color = IosColors.textSecondary)
        val shape = RoundedCornerShape(Radius.m)
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .clip(shape)
                .border(1.dp, IosColors.separator.copy(alpha = 0.55f), shape)
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Outlined.FormatAlignLeft, contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = IosText.callout.copy(color = IosColors.textPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).then(modifier),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text("Where or what, like Starbucks", style = IosText.callout, color = IosColors.textSecondary.copy(alpha = 0.55f))
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun DateChoices(
    date: LocalDate,
    showPicker: Boolean,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onPickDate: () -> Unit,
    onDatePicked: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Date", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textSecondary)
        val today = date == LocalDate.now()
        val yesterday = date == LocalDate.now().minusDays(1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DateChip("Today", today, onToday)
            DateChip("Yesterday", yesterday, onYesterday)
            DateChip(
                if (!today && !yesterday) date.format(DateTimeFormatter.ofPattern("MMM d")) else "Pick date",
                !today && !yesterday || showPicker,
                onPickDate,
                symbol = "calendar",
            )
        }
        if (showPicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            LaunchedEffect(pickerState.selectedDateMillis) {
                val millis = pickerState.selectedDateMillis ?: return@LaunchedEffect
                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                if (picked != date) onDatePicked(picked)
            }
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
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

@Composable
private fun DateChip(title: String, selected: Boolean, onClick: () -> Unit, symbol: String? = null) {
    val shape = CircleShape
    Row(
        Modifier.clip(shape)
            .background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f),
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (symbol != null) Icon(SfSymbols.icon(symbol), contentDescription = null, tint = if (selected) IosColors.spendOrange else IosColors.textSecondary, modifier = Modifier.size(19.dp))
        Text(title, style = if (selected) IosText.callout.w(FontWeight.SemiBold) else IosText.callout, color = if (selected) IosColors.spendOrange else IosColors.textPrimary)
    }
}

@Composable
private fun AddErrorBanner(message: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(IosColors.expense.copy(alpha = 0.12f))
            .border(1.dp, IosColors.expense.copy(alpha = 0.45f), shape)
            .padding(Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon("exclamationmark.circle.fill"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.padding(end = Spacing.s))
        Text(message, style = IosText.callout, color = IosColors.textPrimary, modifier = modifier.weight(1f))
    }
}

@Composable
private fun SavePill(saving: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    val background = when {
        saving -> IosColors.spendOrange.copy(alpha = 0.72f)
        enabled -> IosColors.spendOrange
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier.height(52.dp).clip(shape).background(background)
            .then(if (enabled) Modifier else Modifier.border(1.dp, IosColors.separator.copy(alpha = 0.4f), shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Text("Save", style = IosText.headline, color = if (enabled || saving) Color.White else IosColors.textTertiary)
        }
    }
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "CAD" -> "CA$"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> runCatching { java.util.Currency.getInstance(currency.uppercase()).symbol }.getOrDefault(currency)
}
