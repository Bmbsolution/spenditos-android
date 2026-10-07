package com.spenditos.android.feature.settings.merchants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.ui.theme.Spacing

/** Settings > Merchant names: how raw statement text becomes the name you want to see. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMappingsScreen(onBack: () -> Unit, viewModel: MerchantMappingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MerchantMapping?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("merchants.back")) { Text("Back") }
            Text("Merchant names", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = true }, modifier = Modifier.testTag("merchants.add")) { Text("Add") }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("merchants.loading"))
            }
            state.loadError != null && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("merchants.error"))
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.testTag("merchants.retry")) { Text("Try again") }
            }
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                if (state.items.isEmpty()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen).testTag("merchants.empty"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("No names yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Bank statements often show text like “SQ *COFFEE 1234”. Add a name and imports will show “Coffee” instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("merchants.list")) {
                        items(state.items, key = { it.id }) { item ->
                            val category = state.categories.firstOrNull { it.id == item.categoryId }?.name
                            ListItem(
                                headlineContent = { Text(item.cleanMerchantName) },
                                supportingContent = {
                                    Text(listOfNotNull(item.rawPattern, category, "Pattern".takeIf { item.isRegex }).joinToString(" · "))
                                },
                                modifier = Modifier.clickable(role = Role.Button) { editing = item }.testTag("merchants.row.${item.id}"),
                            )
                            HorizontalDivider()
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
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.m).testTag("merchants.editor"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(if (item == null) "Add a name" else "Edit name", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(raw, { raw = it.take(200) }, label = { Text("Text on your statement") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("merchants.raw"))
            OutlinedTextField(clean, { clean = it.take(100) }, label = { Text("Name to show") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("merchants.clean"))
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().testTag("merchants.category")) {
                    Text("Category: " + (state.categories.firstOrNull { it.id == categoryId }?.name ?: "None"))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { categoryId = null; menu = false })
                    state.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; menu = false })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Match as a pattern")
                    Text("For advanced matching; leave off to match this text anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = regex, onCheckedChange = { regex = it }, modifier = Modifier.testTag("merchants.regex"))
            }
            state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("merchants.actionError")) }
            Button(onClick = { onSave(raw, clean, categoryId, regex) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("merchants.save")) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("merchants.delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this name?") },
            text = { Text("Imports will show the original statement text again.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
