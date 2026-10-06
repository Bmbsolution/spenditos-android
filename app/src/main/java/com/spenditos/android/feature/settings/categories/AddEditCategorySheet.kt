package com.spenditos.android.feature.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.ui.theme.Spacing

private val categoryColors = listOf(
    "#B85F42", "#B86A6A", "#7E6A8D", "#5D738B", "#3F7D70",
    "#6F8C76", "#B59A63", "#8D6D58", "#747A83", "#8A7BA8",
)

private val categoryIcons = listOf("🍽️", "🛒", "🏠", "🚗", "🎁", "💼", "💰", "❤️", "📚", "✈️", "🐾", "🏷️")

@OptIn(ExperimentalLayoutApi::class)
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (category == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("categories.editor.name"),
            )
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Expense", true to "Income").forEach { (income, label) ->
                    FilterChip(
                        selected = isIncome == income,
                        onClick = { isIncome = income },
                        label = { Text(label) },
                        modifier = Modifier.testTag("categories.editor.type.${label.lowercase()}"),
                    )
                }
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryColors.forEach { option ->
                    FilterChip(
                        selected = color == option,
                        onClick = { color = option },
                        label = { Text("●", color = runCatching { android.graphics.Color.parseColor(option) }.getOrDefault(android.graphics.Color.GRAY).let { androidx.compose.ui.graphics.Color(it) }) },
                        modifier = Modifier.testTag("categories.editor.color.$option"),
                    )
                }
            }
            Text("Icon (optional)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = icon == null,
                    onClick = { icon = null },
                    label = { Text("None") },
                    modifier = Modifier.testTag("categories.editor.icon.none"),
                )
                categoryIcons.forEach { option ->
                    FilterChip(
                        selected = icon == option,
                        onClick = { icon = option },
                        label = { Text(option) },
                        modifier = Modifier.testTag("categories.editor.icon.$option"),
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("categories.editor.error")) }
            Button(
                onClick = { onSave(CategoryDraft(name, color, icon, isIncome)) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("categories.editor.save"),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                else Text(if (category == null) "Create category" else "Save changes")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("categories.editor.cancel")) { Text("Cancel") }
        }
    }
}
