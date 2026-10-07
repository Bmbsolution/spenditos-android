package com.spenditos.android.feature.settings.merchants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/** Settings > Merchant rules. Android also keeps statement-name cleanup fields in this editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMappingsScreen(onBack: () -> Unit, viewModel: MerchantMappingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MerchantMapping?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MerchantMapping?>(null) }

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            MerchantHeader(onBack, { creating = true })
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("merchants.loading"), color = IosColors.spendOrange)
                }
                state.loadError != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassCard {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(state.loadError.orEmpty(), style = IosText.footnote, color = IosColors.expense, modifier = Modifier.testTag("merchants.error"))
                            TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("merchants.retry")) { Text("Try again") }
                        }
                    }
                }
                else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    if (state.items.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.l).testTag("merchants.empty"),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(SfSymbols.icon("arrow.triangle.branch"), contentDescription = null, tint = IosColors.spendOrange.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                            Text("No merchant rules yet", style = IosText.headline, color = IosColors.textPrimary)
                            Text("Add rules to auto-categorize transactions.", style = IosText.callout, color = IosColors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("Bank statements often show text like “SQ *COFFEE 1234”. Add a name and imports will show “Coffee” instead.", style = IosText.footnote, color = IosColors.textTertiary)
                            PillButton("Add Rule", { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("merchants.empty.add"))
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().testTag("merchants.list"),
                            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                val category = state.categories.firstOrNull { it.id == item.categoryId }
                                MerchantMappingRow(item, category, onClick = { editing = item }, onDelete = { pendingDelete = item })
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val item = editing
        MerchantEditor(
            item = item,
            state = state,
            onDismiss = { creating = false; editing = null; viewModel.clearError() },
            onSave = { raw, clean, categoryId, regex ->
                viewModel.save(item?.id, raw, clean, categoryId, regex) { creating = false; editing = null }
            },
            onDelete = item?.let { current -> { viewModel.delete(current.id) { editing = null } } },
        )
    }

    pendingDelete?.let { mapping ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Rule", style = IosText.headline) },
            text = { Text("Are you sure you want to delete the rule for \"${mapping.rawPattern}\"? This action cannot be undone.", style = IosText.body) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.delete(mapping.id) {} }) { Text("Delete", color = IosColors.expense) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MerchantHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("merchants.back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text("Merchant Rules", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
        IconButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterEnd).size(44.dp).testTag("merchants.add")) {
            Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = "Add merchant rule", tint = IosColors.spendOrange, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun MerchantMappingRow(item: MerchantMapping, category: Category?, onClick: () -> Unit, onDelete: () -> Unit) {
    GlassCard(Modifier.testTag("merchants.row.${item.id}"), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(category?.let { categoryColor(it.color) } ?: IosColors.neutral))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(item.rawPattern, style = IosText.callout, color = IosColors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (category != null) {
                        Icon(CategoryIcons.symbol(category.icon, category.name), contentDescription = null, tint = categoryColor(category.color).copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                        Text(category.name, style = IosText.subheadline, color = categoryColor(category.color).copy(alpha = 0.8f))
                    } else {
                        Text("Unknown category", style = IosText.subheadline, color = IosColors.neutral)
                    }
                }
                Text("Shows as ${item.cleanMerchantName}${if (item.isRegex) " · Pattern" else ""}", style = IosText.footnote, color = IosColors.textSecondary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).testTag("merchants.delete.${item.id}")) {
                Icon(SfSymbols.icon("trash"), contentDescription = "Delete rule for ${item.rawPattern}", tint = IosColors.expense.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantEditor(
    item: MerchantMapping?,
    state: MerchantMappingsUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var raw by rememberSaveable(item?.id) { mutableStateOf(item?.rawPattern.orEmpty()) }
    var clean by rememberSaveable(item?.id) { mutableStateOf(item?.cleanMerchantName.orEmpty()) }
    var categoryId by rememberSaveable(item?.id) { mutableStateOf(item?.categoryId) }
    var regex by rememberSaveable(item?.id) { mutableStateOf(item?.isRegex ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selectedCategory = state.categories.firstOrNull { it.id == categoryId }
    val canSave = raw.isNotBlank() && clean.isNotBlank() && !state.isSaving

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.l).testTag("merchants.editor"),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", style = IosText.body, color = IosColors.textSecondary) }
                Text(if (item == null) "New Rule" else "Edit Rule", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                Box(Modifier.size(64.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Merchant Pattern")
                MerchantInput(raw, { raw = it.take(200) }, "e.g. starbucks, uber, amazon", "merchants.raw")
                Text("Case-insensitive. Matches any transaction containing this text.", style = IosText.caption, color = IosColors.textTertiary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Name to show")
                MerchantInput(clean, { clean = it.take(100) }, "Coffee", "merchants.clean")
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Category")
                GlassCard(shape = RoundedCornerShape(Radius.m)) {
                    if (state.categories.isEmpty()) {
                        Text("No categories available", style = IosText.body, color = IosColors.textSecondary, modifier = Modifier.padding(Spacing.m))
                    }
                    state.categories.filter { it.isIncome.not() }.takeIf { it.isNotEmpty() }?.let { rows ->
                        CategorySection("Expense", rows, categoryId) { categoryId = it }
                    }
                    state.categories.filter { it.isIncome }.takeIf { it.isNotEmpty() }?.let { rows ->
                        CategorySection("Income", rows, categoryId) { categoryId = it }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { categoryId = null }
                            .padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("merchants.category.none"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Text("None", style = IosText.body, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                        if (categoryId == null) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = IosColors.spendOrange)
                    }
                }
            }

            GlassCard(shape = RoundedCornerShape(Radius.m)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Match as a pattern", style = IosText.body, color = IosColors.textPrimary)
                        Text("For advanced matching; leave off to match this text anywhere.", style = IosText.caption, color = IosColors.textSecondary)
                    }
                    Switch(checked = regex, onCheckedChange = { regex = it }, modifier = Modifier.testTag("merchants.regex"))
                }
            }

            if (canSave && selectedCategory != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SheetSectionLabel("Preview")
                    GlassCard(shape = RoundedCornerShape(Radius.m)) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(selectedCategory.color)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(raw, style = IosText.callout, color = IosColors.textPrimary)
                                if (clean.isNotBlank() && clean != raw) {
                                    Text("Shows as $clean", style = IosText.footnote, color = IosColors.textSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Icon(CategoryIcons.symbol(selectedCategory.icon, selectedCategory.name), contentDescription = null, tint = categoryColor(selectedCategory.color).copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                                    Text(selectedCategory.name, style = IosText.subheadline, color = categoryColor(selectedCategory.color).copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            state.actionError?.let { Text(it, style = IosText.footnote, color = IosColors.expense, modifier = Modifier.testTag("merchants.actionError")) }
            Box(Modifier.fillMaxWidth()) {
                PillButton(
                    text = if (item == null) "Create Rule" else "Update Rule",
                    onClick = { onSave(raw, clean, categoryId, regex) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().testTag("merchants.save"),
                )
                if (state.isSaving) CircularProgressIndicator(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("merchants.delete")) {
                    Text("Delete", color = IosColors.expense)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this name?", style = IosText.headline) },
            text = { Text("Imports will show the original statement text again.", style = IosText.body) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = IosColors.expense) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategorySection(title: String, categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
    Column {
        Text(title.uppercase(), style = IosText.caption, color = IosColors.textTertiary, modifier = Modifier.padding(start = Spacing.s, top = Spacing.s, bottom = Spacing.xs))
        categories.forEachIndexed { index, category ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(category.id) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("merchants.category.${category.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category.color)))
                Icon(CategoryIcons.symbol(category.icon, category.name), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(20.dp))
                Text(category.name, style = IosText.callout, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                if (selectedId == category.id) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = IosColors.spendOrange)
            }
            if (index < categories.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 56.dp), color = IosColors.separator.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MerchantInput(value: String, onChange: (String) -> Unit, placeholder: String, tag: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = IosText.body.copy(color = IosColors.textPrimary),
        modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag(tag),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = IosText.body, color = IosColors.textTertiary); inner() } },
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(text, style = IosText.subheadline, color = IosColors.textSecondary, modifier = Modifier.padding(start = Spacing.xs))
}
