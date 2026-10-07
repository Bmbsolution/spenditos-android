package com.spenditos.android.feature.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.CategorySymbolBadge
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

@Composable
fun CategoriesManagementScreen(onBack: () -> Unit, viewModel: CategoriesManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedIncome by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CategoriesManagementEvent.Saved -> {
                    showEditor = false
                    categoryToEdit = null
                }
                CategoriesManagementEvent.Deleted -> categoryToDelete = null
            }
        }
    }

    val visible = state.categories.filter { it.isIncome == selectedIncome }
        .filter { searchText.trim().isEmpty() || it.name.contains(searchText.trim(), ignoreCase = true) }

    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { CategoriesHeader(onBack) { categoryToEdit = null; showEditor = true } }
            item {
                CategoryTypePicker(
                    selectedIncome = selectedIncome,
                    onSelectIncome = { selectedIncome = it; searchText = "" },
                )
            }
            item { CategorySearchBar(searchText, { searchText = it }) }

            state.error?.let { message ->
                item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s)) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(message, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("categories.error"))
                            TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("categories.retry")) { Text("Try again") }
                        }
                    }
                }
            }
            if (state.isOffline) {
                item {
                    Text(
                        "Check your connection and try again.",
                        style = IosText.footnote,
                        color = IosColors.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("categories.offline"),
                    )
                }
            }
            when {
                state.isLoading && state.categories.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("categories.loading"), color = IosColors.spendOrange)
                    }
                }
                visible.isEmpty() -> item {
                    EmptyCategories(selectedIncome, onAdd = { categoryToEdit = null; showEditor = true })
                }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (selectedIncome) "Income categories" else "Expense categories",
                                style = IosText.subheadline.w(FontWeight.SemiBold),
                                color = IosColors.textSecondary,
                                modifier = Modifier.weight(1f).testTag("categories.section"),
                            )
                            Text(visible.size.toString(), style = IosText.caption, color = IosColors.textTertiary)
                        }
                    }
                    item {
                        Column(
                            Modifier.padding(horizontal = Spacing.screen).clip(RoundedCornerShape(Spacing.m))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(Spacing.m)),
                        ) {
                            visible.forEachIndexed { index, category ->
                                CategoryManagementRow(
                                    category = category,
                                    onOpen = { categoryToEdit = category; showEditor = true },
                                )
                                if (index < visible.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = IosColors.separator)
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { categoryToEdit = null; showEditor = true }
                                    .padding(horizontal = Spacing.m, vertical = Spacing.m).testTag("categories.add.inline"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                Icon(SfSymbols.icon("plus"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
                                Text("Add category", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AddEditCategorySheet(
            category = categoryToEdit,
            initialIsIncome = selectedIncome,
            lockedType = selectedIncome,
            isSaving = state.isSaving,
            error = state.error,
            onDismiss = { showEditor = false; categoryToEdit = null },
            onSave = { draft -> viewModel.saveCategory(categoryToEdit?.id, draft) },
            onDelete = categoryToEdit?.takeUnless { it.isSystemSuggestion }?.let { current ->
                {
                    showEditor = false
                    categoryToEdit = null
                    categoryToDelete = current
                }
            },
        )
    }

    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category", style = IosText.headline) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Are you sure you want to delete \"${category.name}\"? This action cannot be undone.", style = IosText.body)
                    state.error?.let { Text(it, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("categories.delete.error")) }
                    if (state.isOffline) Text("Check your connection and try again.", style = IosText.footnote, modifier = Modifier.testTag("categories.delete.offline"))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(category.id) }, enabled = !state.isSaving, modifier = Modifier.testTag("categories.confirmDelete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { categoryToDelete = null }, modifier = Modifier.testTag("categories.cancelDelete")) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoriesHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange, modifier = Modifier.size(22.dp))
        }
        Text("Categories", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
        IconButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterEnd).size(44.dp).testTag("categories.add")) {
            Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = "Add category", tint = IosColors.spendOrange, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CategorySearchBar(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.s)
            .clip(RoundedCornerShape(Spacing.m)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(Spacing.m))
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon("magnifyingglass"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            modifier = Modifier.weight(1f).testTag("categories.search"),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Search categories", style = IosText.callout, color = IosColors.textTertiary)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            TextButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp).testTag("categories.search.clear")) {
                Icon(SfSymbols.icon("xmark.circle.fill"), contentDescription = "Clear category search", tint = IosColors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyCategories(isIncome: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(SfSymbols.icon("tag.slash"), contentDescription = null, tint = IosColors.spendOrange.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Text("No ${if (isIncome) "income" else "expense"} categories", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.testTag("categories.empty"))
        Text("Add a category to get started", style = IosText.callout, color = IosColors.textSecondary, maxLines = 3)
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(IosColors.spendOrange)
                .clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 24.dp, vertical = 8.dp).testTag("categories.empty.add"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Add category", style = IosText.callout.w(FontWeight.Bold), color = Color.Black)
        }
    }
}

@Composable
private fun CategoryTypePicker(selectedIncome: Boolean, onSelectIncome: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s)
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        listOf(false to "Expenses", true to "Income").forEach { (income, label) ->
            val selected = selectedIncome == income
            val tint = if (selected) IosColors.textPrimary else IosColors.textSecondary
            Row(
                Modifier.weight(1f).clip(CircleShape)
                    .then(
                        if (selected) Modifier.dropShadow(
                            CircleShape,
                            Shadow(radius = 8.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.08f),
                        ) else Modifier,
                    )
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelectIncome(income) }
                    .semantics { this.selected = selected }
                    .testTag(if (income) "categories.tab.income" else "categories.tab.expenses")
                    .padding(vertical = Spacing.s + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            ) {
                Icon(SfSymbols.icon(if (income) "arrow.up" else "arrow.down"), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(label, style = IosText.callout.w(FontWeight.SemiBold), color = tint)
            }
        }
    }
}

@Composable
private fun CategoryManagementRow(category: Category, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("categories.row.${category.id}")) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen)
                .testTag("categories.item.${category.id}")
                .padding(horizontal = Spacing.m, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CategorySymbolBadge(category.icon, category.name, category.color, size = 40.dp)
            Text(category.name, style = IosText.callout, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
            if (category.isSystemSuggestion) {
                Text(
                    "Default",
                    style = IosText.caption,
                    color = IosColors.textSecondary,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}
