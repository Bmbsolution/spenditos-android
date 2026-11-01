package com.spenditos.android.feature.debts

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/**
 * The Debts section of Plan -> Income & bills (iOS `DebtsListSection`): "Debts" with what is owed (or
 * "All paid off"), one row per debt, paid-off debts last and dimmed, and an "Add a debt" action. The
 * floating add button of Income & bills owns adding income and bills; this section only adds debts.
 */
internal fun LazyListScope.debtsSection(
    state: DebtListUiState,
    onOpen: (debtId: String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    item("debts.header") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Strings.get(R.string.debts_debts),
                Modifier.weight(1f).testTag("debts.section").semantics { contentDescription = Strings.get(R.string.debts_debts) },
                style = IosText.headline,
                color = IosColors.textSecondary,
            )
            state.headerSubtitle?.let { subtitle ->
                Text(
                    subtitle,
                    Modifier.padding(start = Spacing.s).testTag("debts.header.total"),
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = if (state.allPaidOff) IosColors.income else IosColors.textPrimary,
                )
            }
        }
    }
    if (state.error != null && !state.hasDebts) {
        item("debts.error") {
            ErrorBanner(
                state.error,
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).testTag("debts.error"),
                onRetry = onRetry,
            )
        }
    }
    state.sortedDebts.forEach { plan ->
        item("debts.row.${plan.id}") {
            DebtRowView(
                plan = plan,
                currency = state.currency,
                onClick = { onOpen(plan.id) },
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).testTag("debts.row.${plan.id}"),
            )
        }
    }
    if (state.hasLoaded || state.hasDebts) {
        if (!state.hasDebts && state.error == null) {
            item("debts.empty") {
                Column(
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).fillMaxWidth()
                        .then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .padding(Spacing.m).testTag("debts.empty"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(Strings.get(R.string.debts_no_debts_yet), style = IosText.body.w(FontWeight.Medium), color = IosColors.textPrimary)
                    Text(
                        Strings.get(R.string.debts_add_what_you_owe_pick),
                        style = IosText.subheadline,
                        color = IosColors.textSecondary,
                    )
                }
            }
        }
        item("debts.add") {
            Row(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange.copy(alpha = 0.10f))
                    .clickable(role = Role.Button, onClickLabel = Strings.get(R.string.debts_opens_a_form_to_add), onClick = onAdd)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp)
                    .testTag("debts.add"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(19.2.dp))
                Text(Strings.get(R.string.common_add_a_debt), style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
