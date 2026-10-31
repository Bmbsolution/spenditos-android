package com.spenditos.android.feature.home.monthbalance

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import com.spenditos.android.data.monthbalance.MonthBalanceKind
import com.spenditos.android.feature.debts.add.DebtAddText
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * The month-start card on Home (docs/features/month-balance.md; iOS `MonthBalanceCardView`): last month ended
 * short or ahead. A shortfall can open the Add debt sheet prefilled ([onAddDebt]); a surplus can be carried in as
 * a read-only line. Dismissible, once per month, and only ever on Home.
 */
@Composable
fun MonthBalanceCard(
    card: MonthBalanceCard,
    currency: String,
    locale: Locale,
    onAddDebt: () -> Unit,
    onAnswer: (MonthBalanceChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = card.data
    val money = { value: Double -> DebtAddText.money(value, currency, locale) }
    val headline = MonthBalanceCopy.headline(data, money) ?: return
    val question = MonthBalanceCopy.question(data) ?: return
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier.fillMaxWidth()
            .background(IosColors.spendOrange.copy(alpha = 0.08f), shape)
            .border(1.dp, IosColors.spendOrange.copy(alpha = 0.3f), shape)
            .padding(Spacing.m)
            .testTag("month-balance-card"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(headline) }
                append(" $question")
            },
            style = IosText.callout,
            color = IosColors.textPrimary,
            modifier = Modifier.testTag("month-balance-text"),
        )
        MonthBalanceCopy.note(data, money)?.let { note ->
            Text(note, style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.testTag("month-balance-note"))
        }
        card.error?.let { Text(it, style = IosText.caption, color = IosColors.expense, modifier = Modifier.testTag("month-balance-error")) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (data.kind == MonthBalanceKind.SHORTFALL) {
                Pill(stringResource(R.string.common_add_debt), primary = true, enabled = !card.isSaving, tag = "month-balance-add-debt", onClick = onAddDebt)
                Pill(stringResource(R.string.home_not_needed), primary = false, enabled = !card.isSaving, tag = "month-balance-dismiss") { onAnswer(MonthBalanceChoice.DISMISSED) }
            } else {
                Pill(stringResource(R.string.home_carry_over), primary = true, enabled = !card.isSaving, tag = "month-balance-carry") { onAnswer(MonthBalanceChoice.CARRIED) }
                Pill(stringResource(R.string.home_no_thanks), primary = false, enabled = !card.isSaving, tag = "month-balance-dismiss") { onAnswer(MonthBalanceChoice.DISMISSED) }
            }
        }
    }
}

@Composable
private fun Pill(title: String, primary: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        title,
        style = IosText.subheadline.w(if (primary) FontWeight.Bold else FontWeight.SemiBold),
        color = if (primary) Color.White else IosColors.textPrimary,
        modifier = Modifier
            .background(if (primary) IosColors.spendOrange else IosColors.textSecondary.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag(tag),
    )
}
