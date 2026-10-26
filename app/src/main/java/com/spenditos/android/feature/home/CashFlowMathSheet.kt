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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.monthbalance.MonthBalanceKind
import com.spenditos.android.feature.debts.add.DebtAddText
import com.spenditos.android.feature.home.monthbalance.MonthBalanceCopy
import com.spenditos.android.feature.home.monthbalance.MonthBalanceFollowUp
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowMathSheet(
    status: CashFlowStatus,
    breakdown: CashFlowBreakdown,
    currency: String,
    locale: Locale,
    onDismiss: () -> Unit,
    /** "Remove" on the Carried in row (month-start balance); null hides it. */
    onRemoveCarriedIn: (() -> Unit)? = null,
    /** "Not included" / "Not tracked" row under the equation; null when there is nothing to act on. */
    followUp: MonthBalanceFollowUp? = null,
    onCarryOver: () -> Unit = {},
    onAddDebt: () -> Unit = {},
    /** An answer is being saved. */
    saving: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.m).testTag("home.math.sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.title")) {
                Text("How it’s calculated", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("home.math.done")) {
                    Text("Done", style = IosText.body.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                }
            }

            GlassCard {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("This month", style = IosText.footnote.w(FontWeight.SemiBold), color = IosColors.textSecondary)
                    EquationRow("Income received", status.received, "+", currency, locale)
                    EquationRow("Income expected", status.expected, "+", currency, locale)
                    if (status.carriedIn.signum() > 0) {
                        EquationRow(
                            title = "Carried in",
                            amount = status.carriedIn,
                            sign = "+",
                            currency = currency,
                            locale = locale,
                            detail = "Last month’s balance you chose to include",
                            action = onRemoveCarriedIn?.let { remove ->
                                {
                                    TextButton(
                                        onClick = remove,
                                        enabled = !saving,
                                        modifier = Modifier.semantics { contentDescription = MonthBalanceCopy.REMOVE_DESCRIPTION }
                                            .testTag("month-balance-remove-math"),
                                    ) { Text(MonthBalanceCopy.REMOVE, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.spendOrange) }
                                }
                            },
                        )
                    }
                    EquationRow(
                        title = "Spent so far",
                        amount = status.spent,
                        sign = "−",
                        currency = currency,
                        locale = locale,
                        detail = "${money(status.spent - status.everyday, currency, locale)} bills · ${money(status.everyday, currency, locale)} everyday",
                    )
                    val entries = if (status.billsLeftCount == 1) "1 entry still to come" else "${status.billsLeftCount} entries still to come"
                    EquationRow(
                        title = "Bills left",
                        amount = status.billsLeft,
                        sign = "−",
                        currency = currency,
                        locale = locale,
                        detail = entries,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.total")) {
                        Text(
                            if (status.leftAfterBills < BigDecimal.ZERO) "Short after bills" else "Left after bills",
                            style = IosText.callout.w(FontWeight.Bold),
                            color = IosColors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            signed(status.leftAfterBills, currency, locale),
                            style = IosText.rounded(17, FontWeight.Bold),
                            color = if (status.leftAfterBills < BigDecimal.ZERO) IosColors.expense else IosColors.income,
                        )
                    }
                    followUp?.let { FollowUpRow(it, currency, locale, saving, onCarryOver, onAddDebt) }
                    if (status.income.signum() > 0) {
                        Text(
                            "That’s ${percent(status.incomeLeftShare)} of this month’s income (${money(status.income, currency, locale)}).",
                            style = IosText.caption,
                            color = IosColors.textSecondary,
                        )
                    }
                    Text(
                        "Entries dated today or earlier count as received or spent; later ones as expected or still to come. Bills are entries created from a bill in Plan › Bills.",
                        style = IosText.caption,
                        color = IosColors.textSecondary,
                    )
                }
            }

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
    /** An action under the amount, aligned to the end (Carried in: Remove). */
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().testTag("home.math.equation.${title.lowercase().replace(' ', '_')}")) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = IosText.callout, color = IosColors.textPrimary)
                detail?.let { Text(it, style = IosText.caption, color = IosColors.textSecondary) }
            }
            Text("$sign ${money(amount, currency, locale)}", style = IosText.rounded(15, FontWeight.SemiBold), color = IosColors.textPrimary)
        }
        if (action != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
        }
    }
}

/**
 * After "No thanks" / "Not needed" or a removed carry-over, the one thing left out of the numbers, with a way back
 * ("Carry over", or the same prefilled Add debt sheet as the card). Home math sheet only, current month only.
 */
@Composable
private fun FollowUpRow(
    followUp: MonthBalanceFollowUp,
    currency: String,
    locale: Locale,
    saving: Boolean,
    onCarryOver: () -> Unit,
    onAddDebt: () -> Unit,
) {
    val data = followUp.data
    val line = MonthBalanceCopy.followUpLine(data) { value -> DebtAddText.money(value, currency, locale) } ?: return
    val surplus = data.kind == MonthBalanceKind.SURPLUS
    Row(
        Modifier.fillMaxWidth().testTag("month-balance-follow-up"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(line, style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.weight(1f))
        TextButton(
            onClick = if (surplus) onCarryOver else onAddDebt,
            enabled = !saving,
            modifier = Modifier.testTag(if (surplus) "month-balance-carry-again" else "month-balance-add-debt-again"),
        ) { Text(if (surplus) "Carry over" else "Add debt", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.spendOrange) }
    }
}

@Composable
private fun BreakdownGroup(title: String, lines: List<CashFlowBreakdown.Line>, currency: String, locale: Locale) {
    if (lines.isEmpty()) return
    GlassCard(modifier = Modifier.testTag("home.math.group.${title.lowercase().replace(' ', '_')}")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("home.math.groupHeader.${title.lowercase().replace(' ', '_')}")) {
                Text(title, style = IosText.footnote.w(FontWeight.SemiBold), color = IosColors.textSecondary, modifier = Modifier.weight(1f))
                Text(money(lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.amount }, currency, locale), style = IosText.footnote, color = IosColors.textSecondary)
            }
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("home.math.line.${line.id}"),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(line.name, style = IosText.callout, color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${line.category} · ${line.date.format(DateTimeFormatter.ofPattern("MMM d", locale))}",
                            style = IosText.caption,
                            color = IosColors.textSecondary,
                        )
                    }
                    Spacer(Modifier.width(Spacing.s))
                    Text(money(line.amount, currency, locale), style = IosText.rounded(15, FontWeight.Medium), color = IosColors.textPrimary)
                }
            }
        }
    }
}

private fun money(amount: BigDecimal, currency: String, locale: Locale): String =
    Money.format(amount.setScale(2, RoundingMode.HALF_UP).toDouble(), currency, locale)

private fun signed(amount: BigDecimal, currency: String, locale: Locale): String =
    if (amount.signum() < 0) "−${money(amount.abs(), currency, locale)}" else money(amount, currency, locale)

private fun percent(share: BigDecimal): String {
    if (share.signum() > 0 && share < BigDecimal("0.01")) return "less than 1%"
    return "${share.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt()}%"
}
