package com.spenditos.android.feature.settings.categories

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Spacing

@Composable
fun CategoriesManagementScreen(onBack: () -> Unit, viewModel: CategoriesManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedIncome by remember { mutableStateOf(false) }
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
    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = Spacing.l),
        ) {
            item { CategoriesHeader(onBack) {
                categoryToEdit = null
                showEditor = true
            } }
            item {
                CategoryTypeSegment(
                    selectedIncome = selectedIncome,
                    onSelectIncome = { selectedIncome = it },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
                )
            }
            state.error?.let { message ->
                item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s)) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("categories.error"))
                            TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("categories.retry")) { Text("Try again") }
                        }
                    }
                }
            }
            if (state.isOffline) {
                item {
                    Text(
                        "Check your connection and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("categories.offline"),
                    )
                }
            }
            when {
                state.isLoading && state.categories.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("categories.loading"), color = MaterialTheme.colorScheme.primary)
                    }
                }
                visible.isEmpty() -> item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.l)) {
                        Column(
                            Modifier.fillMaxWidth().padding(Spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                            Text("No ${if (selectedIncome) "income" else "expense"} categories yet.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("categories.empty"))
                            Text("Add a category to get started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PillButton(
                                text = "Add category",
                                onClick = { categoryToEdit = null; showEditor = true },
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("categories.empty.add"),
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(
                                if (selectedIncome) "Income categories" else "Expense categories",
                                modifier = Modifier.weight(1f).testTag("categories.section"),
                            )
                            Text(
                                visible.size.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = Spacing.screen, top = Spacing.l),
                            )
                        }
                    }
                    item {
                        GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
                            visible.forEachIndexed { index, category ->
                                CategoryManagementRow(
                                    category = category,
                                    index = index,
                                    onOpen = { categoryToEdit = category; showEditor = true },
                                    onEdit = { categoryToEdit = category; showEditor = true },
                                    onDelete = { categoryToDelete = category },
                                )
                                if (index < visible.lastIndex) {
                                    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                }
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
            isSaving = state.isSaving,
            error = state.error,
            onDismiss = { showEditor = false; categoryToEdit = null },
            onSave = { draft -> viewModel.saveCategory(categoryToEdit?.id, draft) },
        )
    }

    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete \"${category.name}\"? This can't be undone.")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("categories.delete.error")) }
                    if (state.isOffline) Text("Check your connection and try again.", modifier = Modifier.testTag("categories.delete.offline"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteCategory(category.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("categories.confirmDelete"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { categoryToDelete = null }, modifier = Modifier.testTag("categories.cancelDelete")) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoriesHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterEnd).size(44.dp).testTag("categories.add")) {
            Icon(Icons.Filled.AddCircle, contentDescription = "Add category", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun CategoryTypeSegment(selectedIncome: Boolean, onSelectIncome: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        CategoryTypeTab(
            label = "Expenses",
            selected = !selectedIncome,
            onClick = { onSelectIncome(false) },
            modifier = Modifier.weight(1f).testTag("categories.tab.expenses"),
        )
        CategoryTypeTab(
            label = "Income",
            selected = selectedIncome,
            onClick = { onSelectIncome(true) },
            modifier = Modifier.weight(1f).testTag("categories.tab.income"),
        )
    }
}

@Composable
private fun CategoryTypeTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(13.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun CategoryManagementRow(category: Category, index: Int, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("categories.row.${category.id}"), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).testTag("categories.item.${category.id}").padding(horizontal = Spacing.m, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val tint = com.spenditos.android.ui.components.categoryColor(category.color)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(category.icon?.takeIf { it.isNotBlank() } ?: "🏷️", style = MaterialTheme.typography.titleMedium)
            }
            Text(category.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (category.isSystemSuggestion) {
                Text(
                    "Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp).testTag("categories.edit.${category.id}")) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit ${category.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).testTag("categories.delete.${category.id}")) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${category.name}", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}
