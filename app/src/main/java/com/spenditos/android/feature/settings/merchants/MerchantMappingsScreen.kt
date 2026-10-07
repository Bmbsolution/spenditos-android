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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

/** Settings > Merchant names: how raw statement text becomes the name you want to see. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMappingsScreen(onBack: () -> Unit, viewModel: MerchantMappingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MerchantMapping?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            MerchantHeader(onBack, { creating = true })
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("merchants.loading"), color = MaterialTheme.colorScheme.primary)
                }
                state.loadError != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassCard {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("merchants.error"))
                            TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("merchants.retry")) { Text("Try again") }
                        }
                    }
                }
                else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    if (state.items.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.l).testTag("merchants.empty"),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            GlassCard {
                                Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    IconChip(Icons.Filled.Store, MaterialTheme.colorScheme.primary, size = 56.dp)
                                    Text("No names yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Bank statements often show text like “SQ *COFFEE 1234”. Add a name and imports will show “Coffee” instead.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().testTag("merchants.list"),
                            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.m),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                val category = state.categories.firstOrNull { it.id == item.categoryId }?.name
                                val subtitle = listOfNotNull(item.rawPattern, category, "Pattern".takeIf { item.isRegex }).joinToString(" · ")
                                GlassCard(
                                    modifier = Modifier.testTag("merchants.row.${item.id}"),
                                    onClick = { editing = item },
                                ) {
                                    IosRow(
                                        title = item.cleanMerchantName,
                                        subtitle = subtitle,
                                        icon = Icons.Filled.Store,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        chevron = true,
                                        divider = false,
                                    )
                                }
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
}

@Composable
private fun MerchantHeader(onBack: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("merchants.back")) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Merchant names", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterEnd).testTag("merchants.add")) {
            Text("Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.l).testTag("merchants.editor"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(if (item == null) "Add a name" else "Edit name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(64.dp))
            }
            GlassCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedTextField(raw, { raw = it.take(200) }, label = { Text("Text on your statement") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("merchants.raw"))
                    OutlinedTextField(clean, { clean = it.take(100) }, label = { Text("Name to show") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("merchants.clean"))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionHeader("Category", Modifier.padding(start = 0.dp, top = 0.dp, bottom = 0.dp))
                Box {
                    GlassCard {
                        IosRow(
                            title = "Category",
                            value = state.categories.firstOrNull { it.id == categoryId }?.name ?: "None",
                            icon = Icons.Filled.Category,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { menu = true },
                            modifier = Modifier.testTag("merchants.category"),
                            divider = false,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { categoryId = null; menu = false })
                        state.categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; menu = false })
                        }
                    }
                }
            }
            GlassCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Match as a pattern", style = MaterialTheme.typography.bodyLarge)
                        Text("For advanced matching; leave off to match this text anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = regex, onCheckedChange = { regex = it }, modifier = Modifier.testTag("merchants.regex"))
                }
            }
            state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("merchants.actionError")) }
            Box(Modifier.fillMaxWidth()) {
                PillButton(
                    text = if (state.isSaving) "Saving…" else "Save",
                    onClick = { onSave(raw, clean, categoryId, regex) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("merchants.save"),
                )
                if (state.isSaving) CircularProgressIndicator(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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
