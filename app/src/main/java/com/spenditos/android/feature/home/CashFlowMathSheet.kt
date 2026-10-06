package com.spenditos.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CashFlowMathSheet(
    status: CashFlowStatus,
    breakdown: CashFlowBreakdown,
    currency: String,
    locale: Locale,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.title")) {
                Text("How it’s calculated", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("home.math.done")) { Text("Done") }
            }
            Text("This month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            EquationRow("Income received", status.received, "+", currency, locale)
            EquationRow("Income expected", status.expected, "+", currency, locale)
            EquationRow(
                title = "Spent so far",
                amount = status.spent,
                sign = "−",
                currency = currency,
                locale = locale,
                detail = "${money(status.spent - status.everyday, currency, locale)} bills · ${money(status.everyday, currency, locale)} everyday",
            )
            EquationRow(
                title = "Bills left",
                amount = status.billsLeft,
                sign = "−",
                currency = currency,
                locale = locale,
                detail = "${status.billsLeftCount} still to come",
            )
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.total")) {
                Text(
                    if (status.leftAfterBills < BigDecimal.ZERO) "Short after bills" else "Left after bills",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(money(status.leftAfterBills, currency, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (status.income.signum() > 0) {
                val percent = status.incomeLeftShare.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt()
                Text(
                    "That’s $percent% of this month’s income (${money(status.income, currency, locale)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Entries dated today or earlier count as received or spent. Later entries count as expected or still to come.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BreakdownGroup("Income received", breakdown.received, currency, locale)
            BreakdownGroup("Income expected", breakdown.expected, currency, locale)
            BreakdownGroup("Bills paid", breakdown.billsPaid, currency, locale)
            BreakdownGroup("Everyday spending", breakdown.everyday, currency, locale)
            BreakdownGroup("Bills left", breakdown.billsLeft, currency, locale)
        }
    }
}

@Composable
private fun EquationRow(
    title: String,
    amount: BigDecimal,
    sign: String,
    currency: String,
    locale: Locale,
    detail: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag("home.math.equation.${title.lowercase().replace(' ', '_')}"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text("$sign ${money(amount, currency, locale)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BreakdownGroup(title: String, lines: List<CashFlowBreakdown.Line>, currency: String, locale: Locale) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("home.math.group.${title.lowercase().replace(' ', '_')}")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.groupHeader.${title.lowercase().replace(' ', '_')}")) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(money(lines.sumOf { it.amount }, currency, locale), style = MaterialTheme.typography.titleSmall)
        }
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().testTag("home.math.line.${line.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${line.category} · ${line.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Text(money(line.amount, currency, locale), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun money(amount: BigDecimal, currency: String, locale: Locale): String =
    Money.format(amount.setScale(2, RoundingMode.HALF_UP).toDouble(), currency, locale)
