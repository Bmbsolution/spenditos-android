package com.spenditos.android.feature.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

private val categoryColors = listOf(
    "#B85F42", "#B86A6A", "#7E6A8D", "#5D738B", "#3F7D70",
    "#6F8C76", "#B59A63", "#8D6D58", "#747A83", "#8A7BA8",
)

private val categoryIcons = listOf("🍽️", "🛒", "🏠", "🚗", "🎁", "💼", "💰", "❤️", "📚", "✈️", "🐾", "🏷️")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditCategorySheet(
    category: Category?,
    initialIsIncome: Boolean,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (CategoryDraft) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var color by remember(category?.id) { mutableStateOf(category?.color ?: categoryColors.first()) }
    var icon by remember(category?.id) { mutableStateOf(category?.icon) }
    var isIncome by remember(category?.id) { mutableStateOf(category?.isIncome ?: initialIsIncome) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("categories.editor.cancel")) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    if (category == null) "New category" else "Edit category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(64.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Name")
                GlassCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m).testTag("categories.editor.name"),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Type")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                    listOf(false to "Expense", true to "Income").forEach { (income, label) ->
                        val selected = isIncome == income
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                                .clickable(role = Role.Tab) { isIncome = income }
                                .semantics { this.selected = selected }
                                .testTag("categories.editor.type.${label.lowercase()}")
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Color")
                GlassCard {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        categoryColors.forEach { option ->
                            val tint = categorySwatch(option)
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(tint)
                                    .then(if (color == option) Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable(role = Role.Button) { color = option }
                                    .semantics { selected = color == option }
                                    .testTag("categories.editor.color.$option"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (color == option) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Icon (optional)")
                GlassCard {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        CategoryIconChoice(value = "None", selected = icon == null, modifier = Modifier.testTag("categories.editor.icon.none")) { icon = null }
                        categoryIcons.forEach { option ->
                            CategoryIconChoice(option, selected = icon == option, modifier = Modifier.testTag("categories.editor.icon.$option")) { icon = option }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SheetSectionLabel("Preview")
                GlassCard {
                    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(categorySwatch(color).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(icon ?: "🏷️", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(name.ifBlank { "Category Name" }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("categories.editor.error")) }
            Box(Modifier.fillMaxWidth()) {
                PillButton(
                    text = if (category == null) "Create category" else "Save changes",
                    onClick = { onSave(CategoryDraft(name, color, icon, isIncome)) },
                    enabled = !isSaving && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("categories.editor.save"),
                )
                if (isSaving) CircularProgressIndicator(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun CategoryIconChoice(value: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(Radius.s))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.s)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = if (value == "None") MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
}

private fun categorySwatch(hex: String): Color = Color(runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.GRAY))
