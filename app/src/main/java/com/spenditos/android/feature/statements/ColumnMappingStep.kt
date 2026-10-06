package com.spenditos.android.feature.statements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementColumnMapping
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
        Text("Map columns", style = MaterialTheme.typography.titleLarge)
        Text("Confirm which header matches each field.", style = MaterialTheme.typography.bodyMedium)
        Text("AI confidence: ${"%.0f".format(mapping.confidence * 100)}%", style = MaterialTheme.typography.bodySmall)
        Text("Choose an amount column or both debit and credit columns.", style = MaterialTheme.typography.bodySmall)

        columns.forEach { (column, label, selected) ->
            val enabled = when (column) {
                StatementColumn.AMOUNT -> mapping.debitColumn == null && mapping.creditColumn == null
                StatementColumn.DEBIT, StatementColumn.CREDIT -> mapping.amountColumn == null
                else -> true
            }
            OutlinedButton(
                onClick = { openColumn = column },
                enabled = enabled && !isWorking,
                modifier = Modifier.fillMaxWidth().testTag("statementImport.mapping.${column.name.lowercase()}"),
            ) {
                Text("$label: ${selected ?: "None"}")
            }
        }

        analysis.preview.firstOrNull()?.let { firstRow ->
            Text("First data row", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.s))
            firstRow.forEachIndexed { index, cell ->
                val header = analysis.headers.getOrNull(index) ?: "Column ${index + 1}"
                Text("$header: ${cell.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("statementImport.mapping.sample.$index"))
            }
        }
        if (!mapping.isProcessable) {
            Text("Choose a date, description, and amount columns.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("statementImport.mapping.help"))
        }
        Button(
            onClick = onPreview,
            enabled = mapping.isProcessable && !isWorking,
            modifier = Modifier.fillMaxWidth().testTag("statementImport.mapping.preview"),
        ) { Text("Preview") }
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
