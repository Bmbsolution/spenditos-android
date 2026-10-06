package com.spenditos.android.feature.settings.categories

import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.spenditos.android.ui.components.categoryColor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.Category
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(
                        onClick = { categoryToEdit = null; showEditor = true },
                        modifier = Modifier.testTag("categories.add"),
                    ) { Text("Add") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (selectedIncome) 1 else 0) {
                Tab(
                    selected = !selectedIncome,
                    onClick = { selectedIncome = false },
                    text = { Text("Expenses") },
                    modifier = Modifier.testTag("categories.tab.expenses"),
                )
                Tab(
                    selected = selectedIncome,
                    onClick = { selectedIncome = true },
                    text = { Text("Income") },
                    modifier = Modifier.testTag("categories.tab.income"),
                )
            }
            state.error?.let {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("categories.error"))
                    TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("categories.retry")) { Text("Try again") }
                }
            }
            if (state.isOffline) {
                Text("Check your connection and try again.", modifier = Modifier.padding(horizontal = Spacing.screen).testTag("categories.offline"))
            }

            val visible = state.categories.filter { it.isIncome == selectedIncome }
            when {
                state.isLoading && state.categories.isEmpty() -> {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.padding(24.dp).testTag("categories.loading"))
                    }
                }
                visible.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(Spacing.l),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("No ${if (selectedIncome) "income" else "expense"} categories yet.", modifier = Modifier.testTag("categories.empty"))
                        Text("Add a category to get started.", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { categoryToEdit = null; showEditor = true },
                            modifier = Modifier.padding(top = 12.dp).testTag("categories.empty.add"),
                        ) { Text("Add category") }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = if (selectedIncome) "Income categories" else "Expense categories",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s + Spacing.xs).testTag("categories.section"),
                        )
                    }
                    items(visible, key = Category::id) { category ->
                        Row(Modifier.fillMaxWidth().testTag("categories.row.${category.id}")) {
                            ListItem(
                                headlineContent = { Text(category.name) },
                                leadingContent = { Box(Modifier.size(20.dp).background(categoryColor(category.color), CircleShape)) },
                                supportingContent = category.icon?.takeIf { it.isNotBlank() }?.let { icon -> { Text(icon) } },
                                modifier = Modifier.weight(1f).clickable {
                                    categoryToEdit = category
                                    showEditor = true
                                }.testTag("categories.item.${category.id}"),
                            )
                            Row(Modifier.padding(end = 8.dp).align(Alignment.CenterVertically)) {
                                TextButton(
                                    onClick = { categoryToEdit = category; showEditor = true },
                                    modifier = Modifier.testTag("categories.edit.${category.id}"),
                                ) { Text("Edit") }
                                TextButton(
                                    onClick = { categoryToDelete = category },
                                    modifier = Modifier.testTag("categories.delete.${category.id}"),
                                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
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
