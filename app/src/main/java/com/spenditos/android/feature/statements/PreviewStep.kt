package com.spenditos.android.feature.statements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.categoryNameFor
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
        Text("Import preview", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SummaryCard("Total", preview.transactions.size, Modifier.weight(1f))
            SummaryCard("Income", incomeCount, Modifier.weight(1f), finance.income)
            SummaryCard("Expenses", expenseCount, Modifier.weight(1f), finance.expense)
        }
        Text("Unknown categories will use the default category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Existing duplicates are skipped automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (preview.transactions.isEmpty()) {
            Text("No transactions found.", modifier = Modifier.testTag("statementImport.preview.empty"))
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("statementImport.preview.list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(preview.transactions) { index, row ->
                    val color = if (row.amount > 0) finance.income else finance.expense
                    Column(Modifier.fillMaxWidth().testTag("statementImport.preview.row.$index").padding(vertical = Spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.importDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(formatStatementAmount(row.amount, currency), color = color, style = MaterialTheme.typography.titleSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.date.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(preview.categoryNameFor(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(
            onClick = onImport,
            enabled = !isWorking && preview.transactions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("statementImport.preview.import"),
        ) { Text("Import ${preview.transactions.size} transactions") }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Signed money in the account currency (positive = income, negative = expense). */
private fun formatStatementAmount(amount: Double, currency: String): String =
    com.spenditos.android.ui.components.Money.signed(kotlin.math.abs(amount), isIncome = amount > 0, currencyCode = currency)
