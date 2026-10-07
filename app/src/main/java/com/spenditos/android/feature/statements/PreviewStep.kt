package com.spenditos.android.feature.statements

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

private val SystemBlue = Color(0xFF007AFF)
private val SystemGreen = Color(0xFF34C759)
private val SystemRed = Color(0xFFFF3B30)

@Composable
private fun secondarySystemBackgroundColor(): Color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
fun PreviewStep(
    preview: StatementPreviewResult,
    isWorking: Boolean,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    currency: String = "USD",
) {
    val incomeCount = preview.transactions.count { it.amount > 0 }
    val expenseCount = preview.transactions.count { it.amount < 0 }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(
            Modifier.fillMaxWidth().background(secondarySystemBackgroundColor()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Import Preview", style = IosText.headline, color = IosColors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                SummaryStat("Total", preview.transactions.size, SystemBlue)
                SummaryStat("Income", incomeCount, SystemGreen)
                SummaryStat("Expenses", expenseCount, SystemRed)
            }
            Text(
                "Duplicates already in your account are skipped automatically.",
                style = IosText.caption,
                color = IosColors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Text(
            "Unknown categories will use the default category.",
            style = IosText.footnote,
            color = IosColors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
        if (preview.transactions.isEmpty()) {
            GlassCard(Modifier.weight(1f).fillMaxWidth().testTag("statementImport.preview.empty"), shape = RoundedCornerShape(12.dp)) {
                Text("No transactions found.", style = IosText.body, color = IosColors.textSecondary, modifier = Modifier.padding(Spacing.m))
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("statementImport.preview.list")) {
                itemsIndexed(preview.transactions) { index, row ->
                    Column(Modifier.fillMaxWidth().testTag("statementImport.preview.row.$index")) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.importDescription, style = IosText.headline, color = IosColors.textPrimary)
                                Text(row.date.toString(), style = IosText.caption, color = IosColors.textSecondary)
                            }
                            Text(formatStatementAmount(row.amount, currency), color = if (row.amount > 0) SystemGreen else SystemRed, style = IosText.headline)
                        }
                        if (index < preview.transactions.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = Spacing.m), color = IosColors.separator)
                        }
                    }
                }
            }
        }
        StatementActionButton(
            text = "Import ${preview.transactions.size} Transactions",
            symbol = null,
            onClick = onImport,
            enabled = !isWorking && preview.transactions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("statementImport.preview.import"),
        )
    }
}

@Composable
private fun SummaryStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(), color = color, style = IosText.title2.w(FontWeight.Bold))
        Text(label, style = IosText.caption, color = IosColors.textSecondary)
    }
}

/** Signed money in the account currency (positive = income, negative = expense). */
private fun formatStatementAmount(amount: Double, @Suppress("UNUSED_PARAMETER") currency: String): String = "%.2f".format(amount)
