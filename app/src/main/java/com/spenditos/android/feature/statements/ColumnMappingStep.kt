package com.spenditos.android.feature.statements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Spacing

@Composable
fun ColumnMappingStep(
    analysis: StatementAnalysisResult,
    mapping: StatementColumnMapping,
    onMappingChange: (StatementColumnMapping) -> Unit,
    onPreview: () -> Unit,
    isWorking: Boolean,
) {
    var openColumn by remember { mutableStateOf<StatementColumn?>(null) }
    val headerOptions = listOf<String?>(null) + analysis.headers
    val columns = listOf(
        Triple(StatementColumn.DATE, "Date", mapping.dateColumn),
        Triple(StatementColumn.DESCRIPTION, "Description", mapping.descriptionColumn),
        Triple(StatementColumn.AMOUNT, "Amount", mapping.amountColumn),
        Triple(StatementColumn.DEBIT, "Debit", mapping.debitColumn),
        Triple(StatementColumn.CREDIT, "Credit", mapping.creditColumn),
    )

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("Map columns", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.s))
        Text(
            "Confirm which header matches each field.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                IconChip(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, size = 32.dp)
                Text("AI confidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.0f".format(mapping.confidence * 100)}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Choose an amount column or both debit and credit columns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.s))

        SectionHeader("Columns", Modifier.padding(top = Spacing.s))
        GlassCard {
            columns.forEachIndexed { index, (column, label, selected) ->
                val enabled = when (column) {
                    StatementColumn.AMOUNT -> mapping.debitColumn == null && mapping.creditColumn == null
                    StatementColumn.DEBIT, StatementColumn.CREDIT -> mapping.amountColumn == null
                    else -> true
                } && !isWorking
                val icon = when (column) {
                    StatementColumn.DATE -> Icons.Filled.CalendarMonth
                    StatementColumn.DESCRIPTION -> Icons.Filled.Description
                    StatementColumn.AMOUNT -> Icons.Filled.AttachMoney
                    StatementColumn.DEBIT -> Icons.Filled.ArrowDownward
                    StatementColumn.CREDIT -> Icons.Filled.ArrowUpward
                }
                IosRow(
                    title = label,
                    value = selected ?: "None",
                    icon = icon,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = if (enabled) ({ openColumn = column }) else null,
                    chevron = enabled,
                    divider = index < columns.lastIndex,
                    titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("statementImport.mapping.${column.name.lowercase()}"),
                )
            }
        }

        analysis.preview.firstOrNull()?.let { firstRow ->
            SectionHeader("First data row", Modifier.padding(top = Spacing.s))
            GlassCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    firstRow.forEachIndexed { index, cell ->
                        val header = analysis.headers.getOrNull(index) ?: "Column ${index + 1}"
                        Text("$header: ${cell.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("statementImport.mapping.sample.$index"))
                    }
                }
            }
        }
        if (!mapping.isProcessable) {
            Text(
                "Choose a date, description, and amount columns.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.s).testTag("statementImport.mapping.help"),
            )
        }
        PillButton(
            text = "Preview",
            onClick = onPreview,
            enabled = mapping.isProcessable && !isWorking,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("statementImport.mapping.preview"),
        )
    }

    openColumn?.let { column ->
        val label = columns.first { it.first == column }.second
        AlertDialog(
            onDismissRequest = { openColumn = null },
            title = { Text("$label column") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    headerOptions.forEachIndexed { index, header ->
                        TextButton(
                            onClick = {
                                onMappingChange(changeStatementMapping(mapping, column, header))
                                openColumn = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("statementImport.mapping.option.${column.name.lowercase()}.$index"),
                        ) { Text(header ?: "None") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openColumn = null }, modifier = Modifier.testTag("statementImport.mapping.cancel")) { Text("Close") }
            },
        )
    }
}
