package com.spenditos.android.feature.recurring

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlinx.coroutines.CancellationException
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * iOS `GenerateMonthSheet` (#403), opened from the overflow menu: pick a month with chips, see
 * exactly what will be logged, leave items out. Entries already logged are greyed. A future month
 * belongs to Month plan, so it offers that instead of generating. The container is opaque: the
 * iOS bug was an unreadable picker on a translucent sheet.
 */
@Composable
fun GenerateMonthSheet(
    templates: List<RecurringTemplate>,
    isIncome: (RecurringTemplate) -> Boolean,
    currency: String,
    /** This month's entries, if the screen already has them (saves a request for the default chip). */
    monthEntries: List<Transaction>?,
    isGenerating: Boolean,
    loadEntries: suspend (YearMonth) -> List<Transaction>,
    onDismiss: () -> Unit,
    onGenerate: (month: YearMonth, templateIds: List<String>) -> Unit,
    /** Opens Month plan on a month; null where Month plan isn't reachable. */
    onPlanMonth: ((YearMonth) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    val current = remember { YearMonth.now() }
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(current) }
    var logged by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoadingMonth by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf(emptySet<String>()) }
    val isFuture = month.isAfter(current)

    LaunchedEffect(month) {
        excluded = emptySet()
        if (month.isAfter(current)) return@LaunchedEffect
        if (month == current && monthEntries != null) {
            logged = monthEntries
            loadFailed = false
            return@LaunchedEffect
        }
        isLoadingMonth = true
        try {
            logged = loadEntries(month)
            loadFailed = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            logged = emptyList()
            loadFailed = true
        } finally {
            isLoadingMonth = false
        }
    }

    val items = remember(templates, month, logged) { generateItems(templates, isIncome, month, logged) }
    val selected = items.filter { !it.isFullyLogged && it.id !in excluded }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.common_cancel), Modifier.clickable(role = Role.Button, onClick = onDismiss).testTag("recurring.generate.cancel"), style = IosText.body, color = IosColors.textPrimary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_generate_a_month), style = IosText.headline, color = IosColors.textPrimary)
                }
                Spacer(Modifier.size(52.dp))
            }

            MonthChips(
                month = month,
                current = current,
                locale = locale,
                onSelect = { month = it },
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
            )

            when {
                isFuture -> FutureMonth(month, locale, onPlanMonth)
                isLoadingMonth -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = IosColors.spendOrange, modifier = Modifier.testTag("recurring.generate.loading"))
                }
                items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.recurring_nothing_falls_in, BillText.monthName(month, locale)), style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.testTag("recurring.generate.empty"))
                }
                else -> {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.list")) {
                        if (loadFailed) {
                            item("failed") {
                                Text(
                                    stringResource(R.string.recurring_couldn_t_check_what_s),
                                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).testTag("recurring.generate.error"),
                                    style = IosText.caption,
                                    color = IosColors.textSecondary,
                                )
                            }
                        }
                        items(items, key = GenerateItem::id) { item ->
                            val isOn = !item.isFullyLogged && item.id !in excluded
                            GenerateRow(
                                item = item,
                                isOn = isOn,
                                currency = currency,
                                locale = locale,
                                onToggle = { excluded = if (item.id in excluded) excluded - item.id else excluded + item.id },
                            )
                        }
                    }
                    GenerateFooter(
                        selected = selected,
                        month = month,
                        currency = currency,
                        locale = locale,
                        isGenerating = isGenerating,
                        onLog = { onGenerate(month, selected.map { it.id }) },
                    )
                }
            }
        }
    }
}

/** Sep · Oct · Nov · Earlier: short month names so all four fit; TalkBack still says "Last month, September". */
@Composable
private fun MonthChips(
    month: YearMonth,
    current: YearMonth,
    locale: java.util.Locale,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = current.minusMonths(1)
    val next = current.plusMonths(1)
    val earlier = (2L..6L).map { current.minusMonths(it) }
    var showEarlier by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        MonthChip(BillText.shortMonthName(last, locale), month == last, stringResource(R.string.recurring_last_month, BillText.monthName(last, locale)), "recurring.generate.month.last", Modifier.weight(1f)) { onSelect(last) }
        MonthChip(BillText.shortMonthName(current, locale), month == current, stringResource(R.string.recurring_this_month, BillText.monthName(current, locale)), "recurring.generate.month.this", Modifier.weight(1f)) { onSelect(current) }
        MonthChip(BillText.shortMonthName(next, locale), month == next, stringResource(R.string.recurring_next_month, BillText.monthName(next, locale)), "recurring.generate.month.next", Modifier.weight(1f)) { onSelect(next) }
        Box(Modifier.weight(1f)) {
            val isEarlier = month in earlier
            MonthChip(
                if (isEarlier) BillText.shortMonthName(month, locale) else stringResource(R.string.recurring_earlier),
                isEarlier,
                stringResource(R.string.recurring_earlier_months),
                "recurring.generate.month.earlier",
                Modifier.fillMaxWidth(),
            ) { showEarlier = true }
            DropdownMenu(expanded = showEarlier, onDismissRequest = { showEarlier = false }) {
                earlier.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), style = IosText.subheadline) },
                        onClick = { showEarlier = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthChip(title: String, isSelected: Boolean, spoken: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (isSelected) IosColors.spendOrange else Color.Black.copy(alpha = 0.06f))
            .semantics(mergeDescendants = true) { contentDescription = spoken; selected = isSelected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = IosText.subheadline.w(FontWeight.SemiBold),
            color = if (isSelected) Color.White else IosColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FutureMonth(
    month: YearMonth,
    locale: java.util.Locale,
    onPlanMonth: ((YearMonth) -> Unit)?,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.future"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(48.dp))
        Text("${BillText.monthName(month, locale)} is still ahead", style = IosText.headline, color = IosColors.textPrimary)
        Text(
            stringResource(R.string.recurring_plan_it_in_month_plan),
            Modifier.padding(horizontal = Spacing.xl),
            style = IosText.callout,
            color = IosColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onPlanMonth != null) {
            Row(
                Modifier.clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange)
                    .clickable(role = Role.Button) { onPlanMonth(month) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.m)
                    .testTag("recurring.generate.planMonth"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.recurring_plan, BillText.monthName(month, locale)), style = IosText.callout.w(FontWeight.SemiBold), color = Color.White)
                Icon(SfSymbols.icon("arrow.right"), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun GenerateRow(item: GenerateItem, isOn: Boolean, currency: String, locale: java.util.Locale, onToggle: () -> Unit) {
    val finance = com.spenditos.android.ui.theme.LocalFinanceColors.current
    val amount = if (item.isFullyLogged) {
        item.template.amount * item.dates.size
    } else {
        item.amountToLog
    }
    val amountText = (if (item.template.amountVaries) "≈ " else "") + Money.format(amount, currency)
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .semantics(mergeDescendants = true) { selected = isOn }
                .clickable(enabled = !item.isFullyLogged, role = Role.Checkbox, onClick = onToggle)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .testTag("recurring.generate.item.${item.id}"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                SfSymbols.icon(if (item.isFullyLogged) "checkmark.circle" else if (isOn) "checkmark.circle.fill" else "circle"),
                contentDescription = null,
                tint = if (item.isFullyLogged) IosColors.textTertiary else if (isOn) IosColors.spendOrange else IosColors.neutral,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.template.description,
                    style = IosText.callout.w(FontWeight.Medium),
                    color = if (item.isFullyLogged) IosColors.textTertiary else IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (item.isFullyLogged) stringResource(R.string.recurring_already_logged) else BillText.generateDates(item, locale),
                    style = IosText.caption,
                    color = IosColors.textSecondary,
                )
            }
            Text(
                amountText,
                style = IosText.callout.w(FontWeight.SemiBold),
                color = if (item.isFullyLogged) IosColors.textTertiary else if (item.isIncome) finance.income else finance.expense,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GenerateFooter(
    selected: List<GenerateItem>,
    month: YearMonth,
    currency: String,
    locale: java.util.Locale,
    isGenerating: Boolean,
    onLog: () -> Unit,
) {
    val entryCount = selected.sumOf { it.dates.size - it.loggedCount }
    val enabled = entryCount > 0 && !isGenerating
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                .background(if (entryCount == 0) IosColors.neutral else IosColors.spendOrange)
                .clickable(enabled = enabled, role = Role.Button, onClick = onLog)
                .padding(vertical = Spacing.m)
                .testTag("recurring.generate.confirm"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (entryCount == 0) stringResource(R.string.recurring_nothing_to_log) else "Log ${counted(entryCount, "entry", "entries")}",
                style = IosText.callout.w(FontWeight.SemiBold),
                color = Color.White,
            )
        }
        if (entryCount > 0) {
            Text(
                BillText.net(selected, month, currency, locale),
                Modifier.testTag("recurring.generate.net"),
                style = IosText.caption,
                color = IosColors.textSecondary,
            )
        }
    }
}
