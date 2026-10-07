package com.spenditos.android.feature.statements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.categoryNameFor
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing

@Composable
fun PreviewStep(
    preview: StatementPreviewResult,
    isWorking: Boolean,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    currency: String = "USD",
) {
    val finance = LocalFinanceColors.current
    val incomeCount = preview.transactions.count { it.amount > 0 }
    val expenseCount = preview.transactions.count { it.amount < 0 }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Import preview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.s))
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                SummaryStat("Total", preview.transactions.size, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                SummaryStat("Income", incomeCount, finance.income, Modifier.weight(1f))
                SummaryStat("Expenses", expenseCount, finance.expense, Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Spacing.s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Unknown categories will use the default category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Existing duplicates are skipped automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (preview.transactions.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth().testTag("statementImport.preview.empty")) {
                Text("No transactions found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.m))
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("statementImport.preview.list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(preview.transactions) { index, row ->
                    val color = if (row.amount > 0) finance.income else finance.expense
                    GlassCard(Modifier.fillMaxWidth().testTag("statementImport.preview.row.$index")) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text(row.importDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatStatementAmount(row.amount, currency), color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.date.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(preview.categoryNameFor(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        PillButton(
            text = "Import ${preview.transactions.size} transactions",
            onClick = onImport,
            enabled = !isWorking && preview.transactions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("statementImport.preview.import"),
        )
    }
}

@Composable
private fun SummaryStat(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(count.toString(), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Signed money in the account currency (positive = income, negative = expense). */
private fun formatStatementAmount(amount: Double, currency: String): String =
    com.spenditos.android.ui.components.Money.signed(kotlin.math.abs(amount), isIncome = amount > 0, currencyCode = currency)
