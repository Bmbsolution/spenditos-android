package com.spenditos.android.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal

/**
 * iOS BillAmountChangeSheet: inline title is the bill name, a grouped amount row, and a footer
 * that the usual amount in Income & bills stays put.
 */
@Composable
fun ChangeAmountSheet(
    row: MonthPlanRecurringRow,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
    onReset: () -> Unit,
) {
    var amountText by remember { mutableStateOf((row.amounts.firstOrNull() ?: row.usualAmount).setScale(2).toPlainString()) }
    val amount = parsePlanAmount(amountText)
    val canSave = amount != null && row.dates.size <= 10
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.l).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", Modifier.clickable(role = Role.Button, onClick = onDismiss), style = IosText.body, color = IosColors.spendOrange)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(row.description, style = IosText.headline, color = IosColors.textPrimary, maxLines = 1)
                }
                Text(
                    "Save",
                    Modifier.testTag("monthPlan.changeAmount.save").clickable(enabled = canSave, role = Role.Button) {
                        amount?.let { onSave(it); onDismiss() }
                    },
                    style = IosText.body.w(FontWeight.SemiBold),
                    color = if (canSave) IosColors.spendOrange else IosColors.spendOrange.copy(alpha = 0.4f),
                )
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Amount", style = IosText.body, color = IosColors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        modifier = Modifier.fillMaxWidth(0.45f).testTag("monthPlan.changeAmount.value"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = IosText.body.copy(color = IosColors.textPrimary, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(IosColors.spendOrange),
                    )
                }
            }
            Text(
                "Only this month. ${row.description} stays ${Money.format(row.usualAmount.toDouble(), currency)} in Income & bills. It's logged with your amount when you apply your plan.",
                style = IosText.footnote,
                color = IosColors.textSecondary,
            )
            if (row.dates.size > 10) {
                Text("Amount changes are unavailable for this schedule.", style = IosText.caption, color = IosColors.expense)
            }
            if (row.hasAmountChange) {
                HorizontalDivider(color = IosColors.separator)
                Text(
                    "Reset to usual amount",
                    Modifier.fillMaxWidth().testTag("monthPlan.changeAmount.reset")
                        .clickable(role = Role.Button) { onReset(); onDismiss() }
                        .padding(vertical = Spacing.s),
                    style = IosText.caption.w(FontWeight.SemiBold),
                    color = IosColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
