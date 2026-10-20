package com.spenditos.android.feature.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.debts.DebtWithPlan
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/** Shared pieces of the debts screens (iOS `DebtComponents.swift`). */

/** A thin bar with the share paid off. Reads out as "38 percent paid off". iOS default height is 8 (`progressBarHeight`). */
@Composable
internal fun DebtProgressBar(progress: Double, modifier: Modifier = Modifier, isPaidOff: Boolean = false, height: Dp = 8.dp) {
    val clamped = progress.coerceIn(0.0, 1.0).toFloat()
    val percent = (clamped * 100).toInt()
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(IosColors.textPrimary.copy(alpha = 0.10f))
            .clearAndSetSemantics {
                contentDescription = "Progress"
                stateDescription = if (isPaidOff) "Paid off" else "$percent percent paid off"
            },
    ) {
        // The fill is never narrower than a dot once there is some progress, like the iOS capsule.
        val fillWidth = maxOf(if (clamped > 0f) height else 0.dp, maxWidth * clamped)
        Box(
            Modifier.width(fillWidth).height(height).clip(CircleShape)
                .background(if (isPaidOff) IosColors.income else IosColors.spendOrange),
        )
    }
}

@Composable
internal fun PaidOffPill(modifier: Modifier = Modifier) {
    Text(
        "Paid off",
        modifier.background(IosColors.income.copy(alpha = 0.14f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
        style = IosText.caption2.w(FontWeight.Bold),
        color = IosColors.income,
        maxLines = 1,
    )
}

/**
 * One debt in Income & bills: name, payment, progress and where it ends. Paid-off debts are dimmed
 * and carry a "Paid off" pill.
 */
@Composable
internal fun DebtRowView(plan: DebtWithPlan, currency: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = plan.summary
    val isPaidOff = summary.isPaidOff
    val paymentLine = if (isPaidOff || plan.paymentsStopped) null else DebtFormat.paymentLine(summary, currency)
    val progressLine = DebtFormat.progressLine(plan, currency)
    val percent = (summary.progress * 100).toInt()
    val label = buildList {
        add(plan.debt.name)
        if (isPaidOff) add("Paid off")
        if (paymentLine != null) add(paymentLine)
    }.joinToString(", ")
    val value = if (isPaidOff) progressLine else "$progressLine, $percent percent paid off"

    Row(
        modifier.fillMaxWidth().alpha(if (isPaidOff) 0.6f else 1f)
            .then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
            .clickable(role = Role.Button, onClickLabel = "Opens this debt", onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(IosColors.spendOrange.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(
                SfSymbols.icon(if (isPaidOff) "checkmark" else "creditcard"),
                contentDescription = null,
                tint = if (isPaidOff) IosColors.income else IosColors.textPrimary,
                modifier = Modifier.size(19.2.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.debt.name,
                        Modifier.weight(1f, fill = false),
                        style = IosText.body.w(FontWeight.Medium),
                        color = IosColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isPaidOff) PaidOffPill()
                }
                if (paymentLine != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(paymentLine, style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                }
            }
            DebtProgressBar(summary.progress, isPaidOff = isPaidOff)
            Text(
                progressLine,
                style = IosText.caption,
                color = if (plan.projection.neverPaysOff && !isPaidOff) IosColors.expense else IosColors.textSecondary,
            )
        }
    }
}

/** "Currency symbol + decimal pad" in the app's card style, with an inline message underneath when something is off. */
@Composable
internal fun DebtAmountField(
    title: String,
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    tag: String,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = IosText.subheadline, color = IosColors.textSecondary)
        val shape = RoundedCornerShape(Radius.m)
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(shape))
                .border(1.dp, if (error == null) Color.Transparent else IosColors.expense.copy(alpha = 0.6f), shape)
                .padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(currencySymbol, style = IosText.rounded(22, FontWeight.SemiBold), color = IosColors.textTertiary, modifier = Modifier.clearAndSetSemantics {})
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = title },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = IosText.rounded(22, FontWeight.SemiBold).copy(color = IosColors.textPrimary),
                cursorBrush = SolidColor(IosColors.spendOrange),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(placeholder, style = IosText.rounded(22, FontWeight.SemiBold), color = IosColors.textTertiary)
                        inner()
                    }
                },
            )
        }
        if (error != null) {
            Text(
                error,
                style = IosText.caption,
                color = IosColors.expense,
                modifier = Modifier.semantics { contentDescription = "$title: $error" },
            )
        }
    }
}

/** iOS `LoadingButton`: 52 tall, radius 16, terracotta; a white spinner while working, gray when unavailable. */
@Composable
internal fun DebtLoadingButton(title: String, isLoading: Boolean, modifier: Modifier = Modifier, isDisabled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    val background = when {
        isLoading -> IosColors.spendOrange.copy(alpha = 0.72f)
        isDisabled -> IosColors.textPrimary.copy(alpha = 0.08f)
        else -> IosColors.spendOrange
    }
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(background)
            .clickable(enabled = !isLoading && !isDisabled, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (isLoading) "Saving" else if (isDisabled) "Unavailable" else "" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        Text(title, style = IosText.headline, color = if (isDisabled) IosColors.textTertiary else Color.White)
    }
}
