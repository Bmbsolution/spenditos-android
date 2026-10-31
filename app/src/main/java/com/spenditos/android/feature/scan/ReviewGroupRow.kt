package com.spenditos.android.feature.scan

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.feature.capture.CaptureReviewItem
import com.spenditos.android.feature.capture.reviewFlagLabels
import com.spenditos.android.feature.capture.SKIPPED_BY_RULE_LABEL
import com.spenditos.android.feature.capture.YOUR_RULE_LABEL
import com.spenditos.android.ui.components.CategoryIcon
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * iOS `ReviewGroupRow`: one merchant, several rows, in a scan review: "Tim Hortons ×4", the total, one include
 * toggle and one category control for the whole group. Expands to the individual dates and amounts; a line can still
 * be opened for editing or switched off on its own.
 */
@Composable
internal fun ReviewGroupRow(
    group: ReviewGroup,
    categories: List<Category>,
    currency: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onPickCategory: () -> Unit,
    onToggleLine: (CaptureReviewItem) -> Unit,
    onOpenLine: (CaptureReviewItem) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("review-group-${group.merchantKey}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        GroupHeader(group, categories, currency, isExpanded, locale, onToggle, onExpand, onPickCategory)
        if (isExpanded) GroupLines(group, currency, locale, onToggleLine, onOpenLine)
    }
}

@Composable
private fun GroupHeader(
    group: ReviewGroup,
    categories: List<Category>,
    currency: String,
    isExpanded: Boolean,
    locale: Locale,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onPickCategory: () -> Unit,
) {
    val state = group.includeState
    val selected = group.sharedCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
    val needsCategory = group.items.any { it.categoryId.isNullOrEmpty() }
    val categoryTitle = selected?.name ?: if (group.hasMixedCategories) stringResource(R.string.scan_mixed_categories) else stringResource(R.string.scan_choose_category)
    val badge = when {
        group.allSkippedByRule -> SKIPPED_BY_RULE_LABEL
        group.allRuleFiled -> YOUR_RULE_LABEL
        group.flaggedCount > 0 -> "${group.flaggedCount} not included"
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().alpha(if (state == ReviewGroup.IncludeState.NONE) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(48.dp)
                .toggleable(value = state == ReviewGroup.IncludeState.ALL, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { contentDescription = if (state == ReviewGroup.IncludeState.ALL) Strings.get(R.string.scan_exclude, group.merchant) else Strings.get(R.string.scan_include, group.merchant) }
                .testTag("review-group-toggle"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SfSymbols.icon(
                    when (state) {
                        ReviewGroup.IncludeState.ALL -> "checkmark.circle.fill"
                        ReviewGroup.IncludeState.NONE -> "circle"
                        ReviewGroup.IncludeState.MIXED -> "minus.circle.fill"
                    },
                ),
                contentDescription = null,
                tint = if (state == ReviewGroup.IncludeState.NONE) IosColors.textTertiary else IosColors.spendOrange,
                modifier = Modifier.size(19.dp),
            )
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(Radius.s)).background(IosColors.spendOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            CategoryIcon(selected?.icon, categoryTitle, tint = IosColors.textPrimary, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // The merchant name wins space over the ×N badge.
                Text(
                    group.merchant,
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    Modifier.clip(CircleShape).background(surfaceTertiary())
                        .clickable(role = Role.Button, onClick = onExpand)
                        .semantics { contentDescription = "${group.count} transactions, ${if (isExpanded) "collapse" else "expand"}" }
                        .padding(horizontal = Spacing.s, vertical = 3.dp)
                        .testTag("review-group-expand"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("×${group.count}", style = IosText.caption.w(FontWeight.SemiBold), color = IosColors.textSecondary, maxLines = 1)
                    Icon(
                        SfSymbols.icon(if (isExpanded) "chevron.up" else "chevron.down"),
                        contentDescription = null,
                        tint = IosColors.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val shape = CircleShape
                Text(
                    categoryTitle,
                    style = IosText.subheadline,
                    color = if (needsCategory) IosColors.spendOrange else IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(shape)
                        .background(if (needsCategory) IosColors.spendOrange.copy(alpha = 0.1f) else Color.Transparent)
                        .border(1.dp, if (needsCategory) IosColors.spendOrange.copy(alpha = 0.5f) else IosColors.separator, shape)
                        .clickable(role = Role.Button, onClick = onPickCategory)
                        .semantics { contentDescription = Strings.get(R.string.scan_category_for, group.merchant, categoryTitle) }
                        .padding(horizontal = Spacing.s + 2.dp, vertical = 4.dp)
                        .testTag("review-group-category"),
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = IosText.caption.w(FontWeight.SemiBold),
                        color = IosColors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.testTag("review-group-badge"),
                    )
                }
            }
        }
        val total = group.signedTotal
        Text(
            groupTotalText(group, currency, locale),
            style = IosText.rounded(17, FontWeight.Medium),
            color = if (total.signum() < 0) IosColors.expense else IosColors.income,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("review-group-total"),
        )
    }
}

@Composable
private fun GroupLines(
    group: ReviewGroup,
    currency: String,
    locale: Locale,
    onToggleLine: (CaptureReviewItem) -> Unit,
    onOpenLine: (CaptureReviewItem) -> Unit,
) {
    // Lines start under the merchant's include toggle: its width (iOS 24) plus the header spacing.
    Column(Modifier.fillMaxWidth().padding(start = 24.dp + Spacing.m)) {
        group.items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().alpha(if (item.include) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Box(
                    // 24 x 32 visual like iOS; the 48dp touch target overflows it without changing the layout.
                    Modifier.size(width = 24.dp, height = 32.dp).wrapContentSize(Alignment.Center, unbounded = true).requiredSize(48.dp)
                        .toggleable(value = item.include, enabled = !item.isFrozen, role = Role.Checkbox, onValueChange = { onToggleLine(item) })
                        .semantics { contentDescription = if (item.include) Strings.get(R.string.scan_exclude_this_one) else Strings.get(R.string.scan_include_this_one) }
                        .testTag("review-group-line-toggle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SfSymbols.icon(if (item.include) "checkmark.circle.fill" else "circle"),
                        contentDescription = null,
                        tint = if (item.include) IosColors.spendOrange else IosColors.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    Modifier.weight(1f).heightIn(min = 32.dp)
                        .clickable(enabled = !item.isFrozen, role = Role.Button) { onOpenLine(item) }
                        .testTag("review-group-line"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(groupDateText(item.date, locale), style = IosText.subheadline, color = IosColors.textPrimary)
                        lineFlag(item, currency, locale)?.let {
                            Text(it, style = IosText.caption, color = IosColors.textSecondary)
                        }
                    }
                    Text(lineAmountText(item, currency, locale), style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1)
                    Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/** Why the row starts unchecked, and whether it is still pending (the rule badge belongs to the group). */
private fun lineFlag(item: CaptureReviewItem, currency: String, locale: Locale): String? =
    reviewFlagLabels(item, currency, locale).filter { it != YOUR_RULE_LABEL }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun surfaceTertiary(): Color = if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

/** "+$12.00" / "−$48.20", or "−—" when no row has an amount (iOS `totalText`). */
internal fun groupTotalText(group: ReviewGroup, currency: String, locale: Locale): String {
    val total = group.signedTotal
    val sign = if (total.signum() < 0) "−" else "+"
    if (group.allAmountsMissing) return "$sign—"
    // Like iOS, the group is shown in its first row's own currency when that row could not be converted.
    return sign + Money.format(total.abs().toDouble(), group.items.firstOrNull()?.reviewCurrency ?: currency, locale)
}

internal fun lineAmountText(item: CaptureReviewItem, currency: String, locale: Locale): String {
    val sign = if (item.isIncomeRow()) "+" else "−"
    val amount: BigDecimal = item.editableAmount() ?: return "$sign—"
    return sign + Money.format(amount.toDouble(), item.reviewCurrency ?: currency, locale)
}

/** "Oct 8" for "2026-10-08", "No date" for none, the text itself when it does not parse. */
internal fun groupDateText(iso: String?, locale: Locale): String {
    if (iso.isNullOrBlank()) return Strings.get(R.string.scan_no_date)
    return runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d", locale)) }.getOrDefault(iso)
}
