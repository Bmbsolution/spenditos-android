package com.spenditos.android.feature.statements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

private val SystemBlue = Color(0xFF007AFF)
@Composable
private fun secondarySystemBackgroundColor(): Color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
fun ColumnMappingStep(
    analysis: StatementAnalysisResult,
    mapping: StatementColumnMapping,
    onMappingChange: (StatementColumnMapping) -> Unit,
    onPreview: () -> Unit,
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    var openColumn by remember { mutableStateOf<StatementColumn?>(null) }
    val headerOptions = listOf<String?>(null) + analysis.headers
    val columns = listOf(
        Triple(StatementColumn.DATE, "Date Column", mapping.dateColumn),
        Triple(StatementColumn.DESCRIPTION, "Description Column", mapping.descriptionColumn),
        Triple(StatementColumn.AMOUNT, "Amount Column", mapping.amountColumn),
        Triple(StatementColumn.DEBIT, "Debit Column (optional)", mapping.debitColumn),
        Triple(StatementColumn.CREDIT, "Credit Column (optional)", mapping.creditColumn),
    )

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(
            Modifier.fillMaxWidth().background(secondarySystemBackgroundColor()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Map Columns", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.padding(top = 16.dp))
            Text(
                "AI detected columns. Confirm which header plays each role.",
                style = IosText.caption,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            androidx.compose.material3.Icon(
                SfSymbols.icon("wand.and.stars"),
                contentDescription = null,
                tint = SystemBlue,
                modifier = Modifier.size(20.4.dp),
            )
            Text("AI Confidence: ${"%.0f".format(mapping.confidence * 100)}%", style = IosText.subheadline, color = IosColors.textPrimary)
        }

        SectionHeader("Columns", Modifier.padding(top = Spacing.s))
        GlassCard {
            columns.forEachIndexed { index, (column, label, selected) ->
                val enabled = when (column) {
                    StatementColumn.AMOUNT -> mapping.debitColumn == null && mapping.creditColumn == null
                    StatementColumn.DEBIT, StatementColumn.CREDIT -> mapping.amountColumn == null
                    else -> true
                } && !isWorking
                IosRow(
                    title = label,
                    value = selected ?: "None",
                    onClick = if (enabled) ({ openColumn = column }) else null,
                    chevron = enabled,
                    divider = index < columns.lastIndex,
                    titleColor = if (enabled) IosColors.textPrimary else IosColors.textSecondary,
                    modifier = Modifier.testTag("statementImport.mapping.${column.name.lowercase()}"),
                )
            }
        }

        analysis.preview.firstOrNull()?.let { firstRow ->
            SectionHeader("First Data Row", Modifier.padding(top = Spacing.s))
            GlassCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    firstRow.forEachIndexed { index, cell ->
                        val header = analysis.headers.getOrNull(index) ?: "Column ${index + 1}"
                        Text("$header: ${cell.text}", style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.testTag("statementImport.mapping.sample.$index"))
                    }
                }
            }
        }
        if (!mapping.isProcessable) {
            Text(
                "Map at least a date column, a description column, and an amount (or debit and credit) column.",
                color = Color(0xFFFF9500),
                style = IosText.caption,
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = 6.dp).testTag("statementImport.mapping.help"),
            )
        }
        StatementActionButton(
            text = "Preview Import",
            symbol = null,
            onClick = onPreview,
            enabled = mapping.isProcessable && !isWorking,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("statementImport.mapping.preview"),
        )
    }

    openColumn?.let { column ->
        val label = columns.first { it.first == column }.second
        AlertDialog(
            onDismissRequest = { openColumn = null },
            title = { Text(label, style = IosText.headline) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    headerOptions.forEachIndexed { index, header ->
                        TextButton(
                            onClick = {
                                onMappingChange(changeStatementMapping(mapping, column, header))
                                openColumn = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("statementImport.mapping.option.${column.name.lowercase()}.$index"),
                        ) { Text(header ?: "None", style = IosText.body) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openColumn = null }, modifier = Modifier.testTag("statementImport.mapping.cancel")) { Text("Close", style = IosText.body) }
            },
        )
    }
}
