package com.spenditos.android.feature.recurring

import com.spenditos.android.core.i18n.Strings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.ui.components.CategoryIcon
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.IosSegmented
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val EndedFilters = listOf(Strings.get(R.string.common_all), Strings.get(R.string.common_income), Strings.get(R.string.common_bills))

/**
 * iOS `EndedBillsView` (#403): income and bills that stopped for good, grouped by the year they
 * ended, with what each one cost or brought in over its life. From here an item can start again
 * or be deleted for good; past payments always stay in Transactions.
 */
@Composable
fun EndedBillsScreen(onBack: () -> Unit, viewModel: IncomeAndBillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    var filter by rememberSaveable { mutableStateOf(0) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val ended = state.endedTemplates
    val openTemplate = openId?.let { id -> state.templates.firstOrNull { it.id == id } }

    val endedIds = ended.map { it.id }
    LaunchedEffect(endedIds) { viewModel.loadEndedHistory() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                IncomeAndBillsEvent.Deleted -> openId = null
                is IncomeAndBillsEvent.Lifecycle -> {
                    openId = null
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(event.text)
                    }
                }
                else -> Unit
            }
        }
    }
    // The open item moved away (started again or deleted): close its detail.
    LaunchedEffect(openId, state.templates, state.isLoading) {
        if (openId != null && !state.isLoading && openTemplate == null) openId = null
    }

    val filtered = ended.filter { template ->
        when (filter) {
            1 -> state.isIncome(template)
            2 -> !state.isIncome(template)
            else -> true
        }
    }
    // Newest year first: "Ended in 2026", "Ended in 2025".
    val groups = filtered.groupBy { endedYear(it) }.toSortedMap(compareByDescending { it }).toList()

    IosScreen {
        Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Box {
                    LargeTitle(stringResource(R.string.common_ended))
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp, start = Spacing.s).size(44.dp).testTag("ended.back")) {
                        Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
                    }
                }
                if (ended.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().testTag("ended.empty"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                    ) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(44.dp))
                        Text(stringResource(R.string.recurring_nothing_has_ended), style = IosText.headline, color = IosColors.textPrimary)
                        Text(
                            stringResource(R.string.recurring_income_and_bills_you_end),
                            Modifier.padding(horizontal = Spacing.xl),
                            style = IosText.callout,
                            color = IosColors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ended.list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)) {
                        item("filter") {
                            IosSegmented(
                                options = EndedFilters,
                                selectedIndex = filter,
                                onSelect = { filter = it },
                                modifier = Modifier.padding(bottom = Spacing.s),
                                tagPrefix = "ended.filter",
                            )
                        }
                        groups.forEach { (year, items) ->
                            item("header-$year") {
                                Text(
                                    BillText.endedGroupTitle(year),
                                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.s),
                                    style = IosText.headline,
                                    color = IosColors.textSecondary,
                                )
                            }
                            items(items, key = RecurringTemplate::id) { template ->
                                EndedRow(
                                    template = template,
                                    state = state,
                                    locale = locale,
                                    onClick = { openId = template.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    openTemplate?.let { template ->
        EndedBillDetailSheet(
            template = template,
            isIncome = state.isIncome(template),
            currency = state.currency,
            cachedHistory = state.endedHistory[template.id],
            isWriting = state.isWriting,
            error = state.error,
            fetchHistory = viewModel::fetchHistory,
            onStartAgain = { date -> viewModel.startAgain(template, date) },
            onDelete = { viewModel.delete(template.id) },
            onDismiss = { openId = null },
        )
    }
}

@Composable
private fun EndedRow(template: RecurringTemplate, state: IncomeAndBillsUiState, locale: java.util.Locale, onClick: () -> Unit) {
    val category = state.categories.firstOrNull { it.id == template.categoryId }
    val tint = categoryColor(category?.color)
    val isIncome = state.isIncome(template)
    val subtitle = remember(template, state.endedHistory[template.id], state.currency, locale) {
        BillText.endedSubtitle(template, state.endedHistory[template.id], isIncome, state.currency, locale = locale)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .testTag("ended.row.${template.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                CategoryIcon(category?.icon, category?.name, tint = IosColors.textSecondary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(template.description, style = IosText.body.w(FontWeight.Medium), color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = IosText.caption, color = IosColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

/** iOS `EndedBillDetailSheet`: amount, span, Start again, Delete for good and the payments list. */
@Composable
private fun EndedBillDetailSheet(
    template: RecurringTemplate,
    isIncome: Boolean,
    currency: String,
    cachedHistory: BillHistorySummary?,
    isWriting: Boolean,
    error: String?,
    fetchHistory: suspend (String) -> BillHistorySummary,
    onStartAgain: (LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    var history by remember { mutableStateOf<BillHistorySummary?>(null) }
    var historyFailed by remember { mutableStateOf(false) }
    var showStartAgain by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val noun = if (isIncome) "income" else "bill"

    LaunchedEffect(template.id) {
        // If the history call fails, leave the totals out and say so under Payments.
        try {
            history = fetchHistory(template.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            history = cachedHistory
            historyFailed = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().testTag("ended.detail")) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(52.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(template.description, style = IosText.headline, color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(R.string.common_done), Modifier.clickable(role = Role.Button, onClick = onDismiss).testTag("ended.detail.done"), style = IosText.body.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "${Money.format(template.amount, currency)} · ${frequencyLabel(template.frequency)}",
                        style = IosText.callout,
                        color = IosColors.textSecondary,
                    )
                    Text(
                        BillText.endedSubtitle(template, history, isIncome, currency, locale = locale),
                        style = IosText.callout.w(FontWeight.Medium),
                        color = IosColors.textPrimary,
                        modifier = Modifier.testTag("ended.detail.subtitle"),
                    )
                    history?.let { BillText.endedSpan(it, locale) }?.let { span ->
                        Text(span, style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.testTag("ended.detail.span"))
                    }
                }
                error?.let {
                    Text(it, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs).testTag("ended.detail.error"), style = IosText.caption, color = IosColors.expense)
                }
                HorizontalDivider(color = IosColors.separator)
                DetailAction(
                    icon = SfSymbols.icon("arrow.clockwise"),
                    text = stringResource(R.string.recurring_start_again),
                    color = IosColors.spendOrange,
                    enabled = !isWriting,
                    tag = "ended.startAgain",
                ) { showStartAgain = true }
                HorizontalDivider(color = IosColors.separator, modifier = Modifier.padding(start = Spacing.screen))
                DetailAction(
                    icon = SfSymbols.icon("trash"),
                    text = stringResource(R.string.recurring_delete_for_good),
                    color = IosColors.expense,
                    enabled = !isWriting,
                    tag = "ended.delete",
                ) { showDeleteConfirm = true }
                HorizontalDivider(color = IosColors.separator)

                Text(
                    if (isIncome) stringResource(R.string.recurring_payments_received) else stringResource(R.string.recurring_payments),
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.m, bottom = Spacing.s),
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = IosColors.textSecondary,
                )
                val entries = history?.entries.orEmpty()
                when {
                    entries.isNotEmpty() -> entries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("ended.entry.${entry.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                            Text(
                                date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) ?: entry.date,
                                Modifier.weight(1f),
                                style = IosText.callout,
                                color = IosColors.textPrimary,
                            )
                            Text(
                                Money.format(entry.amount, currency),
                                style = IosText.callout.w(FontWeight.Medium),
                                color = if (isIncome) LocalFinanceColors.current.income else LocalFinanceColors.current.expense,
                            )
                        }
                    }
                    historyFailed -> Text(stringResource(R.string.recurring_couldn_t_load_payments_right), Modifier.padding(horizontal = Spacing.screen).testTag("ended.detail.failed"), style = IosText.subheadline, color = IosColors.textSecondary)
                    history != null -> Text(stringResource(R.string.recurring_no_payments_were_logged), Modifier.padding(horizontal = Spacing.screen).testTag("ended.detail.none"), style = IosText.subheadline, color = IosColors.textSecondary)
                    else -> Box(Modifier.fillMaxWidth().padding(Spacing.m), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IosColors.spendOrange, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    if (showStartAgain) {
        val today = LocalDate.now()
        val initial = remember(template) { BillSchedule.nextDate(template, today) ?: today }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showStartAgain = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: initial
                        showStartAgain = false
                        onStartAgain(picked)
                    },
                    modifier = Modifier.testTag("ended.startAgain.confirm"),
                ) { Text(stringResource(R.string.recurring_start_again_2, noun)) }
            },
            dismissButton = { TextButton(onClick = { showStartAgain = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Column {
                Text(
                    stringResource(R.string.recurring_when_is_the_next_payment),
                    Modifier.padding(horizontal = Spacing.l).padding(top = Spacing.m),
                    style = IosText.callout,
                    color = IosColors.textSecondary,
                )
                DatePicker(state = pickerState)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.recurring_delete_for_good_2)) },
            text = { Text(stringResource(R.string.recurring_past_payments_stay_in_your)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }, modifier = Modifier.testTag("ended.delete.confirm")) {
                    Text(stringResource(R.string.common_delete), color = IosColors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }, modifier = Modifier.testTag("ended.delete.cancel")) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.screen).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = IosText.body, color = color)
    }
}

private fun frequencyLabel(frequency: String): String = when (frequency) {
    "bi-weekly" -> "Bi-Weekly"
    "semi-monthly" -> "Semi-Monthly"
    "semi-annually" -> "Semi-Annually"
    else -> frequency.replaceFirstChar(Char::uppercase)
}
