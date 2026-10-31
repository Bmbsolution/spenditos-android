package com.spenditos.android.feature.scan

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.scan.counted
import com.spenditos.android.feature.capture.CaptureReviewFooter
import com.spenditos.android.feature.capture.ReviewCategoryPickerSheet
import com.spenditos.android.feature.capture.ReviewItemEditorSheet
import com.spenditos.android.feature.capture.ReviewSummaryRow
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/**
 * iOS `TransactionReviewListView` for a scan: "Review transactions", a month-grouped list of the rows the scan
 * found (new rows checked; possible duplicates, card payments, transfers and rule skips unchecked and labelled),
 * tap a row to edit it, then save and a done screen.
 */
@Composable
fun ScanReviewScreen(onClose: () -> Unit, onScanMore: () -> Unit) {
    val viewModel: ScanReviewViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ScanReviewEvent.Close -> onClose()
                ScanReviewEvent.ScanMore -> onScanMore()
            }
        }
    }
    BackHandler {
        if (state.phase == ScanReviewPhase.DONE) viewModel.done() else viewModel.requestClose()
    }

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ReviewTopBar(
                title = if (state.phase == ScanReviewPhase.DONE) "" else stringResource(R.string.common_review_transactions),
                showCancel = state.phase != ScanReviewPhase.DONE,
                cancelEnabled = state.phase != ScanReviewPhase.SAVING,
                onCancel = viewModel::requestClose,
            )
            when (state.phase) {
                ScanReviewPhase.LOADING -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("scan.review.loading"), color = IosColors.spendOrange)
                }
                ScanReviewPhase.ERROR -> OpenError(
                    message = state.loadError.orEmpty(),
                    onRetry = viewModel::load,
                    onClose = onClose,
                    modifier = Modifier.weight(1f),
                )
                ScanReviewPhase.REVIEW, ScanReviewPhase.SAVING -> ReviewBody(state, viewModel, Modifier.weight(1f))
                ScanReviewPhase.DONE -> DoneContent(state, onDone = viewModel::done, onScanMore = viewModel::scanMore, modifier = Modifier.weight(1f))
            }
        }
    }

    if (state.showDiscardDialog) DiscardDialog(viewModel)
}

@Composable
private fun ReviewTopBar(title: String, showCancel: Boolean, cancelEnabled: Boolean, onCancel: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Spacing.s), contentAlignment = Alignment.Center) {
        if (title.isNotEmpty()) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        if (showCancel) {
            TextButton(onClick = onCancel, enabled = cancelEnabled, modifier = Modifier.align(Alignment.CenterStart).testTag("review-cancel")) {
                Text(stringResource(R.string.common_cancel), style = IosText.body, color = if (cancelEnabled) IosColors.spendOrange else IosColors.textTertiary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReviewBody(state: ScanReviewUiState, viewModel: ScanReviewViewModel, modifier: Modifier) {
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerSearch by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    // Month, then merchant: same-merchant rows of a month collapse into one group row (merchant keys are cached on open).
    val sections = androidx.compose.runtime.remember(state.items, state.merchantKeys, locale) {
        ScanReviewGrouping.monthSections(state.items, locale = locale, grouping = true) { state.merchantKeys[it.idempotencyKeyId].orEmpty() }
    }

    Column(modifier.fillMaxWidth()) {
        state.saveError?.let { message ->
            ErrorBanner(
                message = message,
                onRetry = viewModel::save,
                onDismiss = viewModel::dismissSaveError,
                modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).testTag("scan.review.saveError"),
            )
        }
        if (state.preparingCurrencies) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.s).testTag("scan.review.preparing"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = IosColors.spendOrange, strokeWidth = 2.dp)
                Text(stringResource(R.string.scan_preparing_categories_and_currencies), style = IosText.subheadline, color = IosColors.textSecondary)
            }
        }
        val missing = state.missingCategoryCount
        if (missing > 0) {
            Text(
                "${missing.counted("item")} will save as Uncategorized — pick a category to refine reports",
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = IosColors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("scan.review.uncategorized"),
            )
        }
        if (state.conversionUnavailableCount > 0) {
            Text(
                stringResource(R.string.scan_one_or_more_foreign_currency),
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = IosColors.warning,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("scan.review.conversion"),
            )
        }
        state.preparationMessage?.let { message ->
            Text(
                message,
                style = IosText.caption,
                color = IosColors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(bottom = Spacing.s).testTag("scan.review.preparationMessage"),
            )
        }
        if (state.incompleteCount > 0) {
            Text(
                "${state.incompleteCount.counted("item")} ${if (state.incompleteCount == 1) "needs" else "need"} an amount and a date before saving",
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = IosColors.warning,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("scan.review.incomplete"),
            )
        }
        // Scan lists run to thousands of rows: sections and rows are lazy.
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("review-list")) {
            item(key = "summary") { SummaryHeader(state) }
            sections.forEach { section ->
                stickyHeader(key = "month-${section.key}") {
                    Text(
                        section.title,
                        style = IosText.subheadline.w(FontWeight.SemiBold),
                        color = IosColors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = Spacing.m, vertical = 6.dp)
                            .semantics { heading() }
                            .testTag("scan.review.month.${section.key}"),
                    )
                }
                items(section.entries, key = { it.key }) { entry ->
                    Column {
                        when (entry) {
                            is ReviewEntry.Single -> ReviewSummaryRow(
                                item = entry.item,
                                categories = state.categories,
                                currency = state.currency,
                                onToggle = { viewModel.toggleInclude(entry.item.idempotencyKeyId) },
                                onOpen = { editingKey = entry.item.idempotencyKeyId },
                            )
                            is ReviewEntry.Group -> ReviewGroupRow(
                                group = entry.group,
                                categories = state.categories,
                                currency = state.currency,
                                isExpanded = entry.group.id in state.expandedGroups,
                                onToggle = { viewModel.toggleGroup(entry.group.itemKeys) },
                                onExpand = { viewModel.toggleGroupExpanded(entry.group.id) },
                                onPickCategory = { pickingGroupId = entry.group.id },
                                onToggleLine = { viewModel.toggleInclude(it.idempotencyKeyId) },
                                onOpenLine = { editingKey = it.idempotencyKeyId },
                            )
                        }
                        HorizontalDivider(Modifier.padding(start = Spacing.m), color = IosColors.separator)
                    }
                }
            }
        }
        RuleBanner(state.ruleMessage, onDismiss = viewModel::dismissRuleMessage)
        CaptureReviewFooter(
            items = state.includedItems,
            currency = state.currency,
            enabled = state.canSave,
            onSave = viewModel::save,
            buttonText = state.saveButtonText,
            saveTag = "scan.review.save",
        )
    }

    val editing = state.items.firstOrNull { it.idempotencyKeyId == editingKey }
    if (editing != null) {
        val key = editing.idempotencyKeyId
        ReviewItemEditorSheet(
            item = editing,
            categories = state.categories,
            currency = state.currency,
            onDismiss = { editingKey = null },
            onAmountChange = { viewModel.onAmountChange(key, it) },
            onDescriptionChange = { viewModel.onDescriptionChange(key, it) },
            onDateChange = { viewModel.onDateChange(key, it) },
            onCategoryChange = { viewModel.onCategoryChange(key, it) },
            onTypeChange = { viewModel.onTypeChange(key, it) },
            onExclude = {
                if (editing.include) viewModel.toggleInclude(key)
                editingKey = null
            },
        )
    }

    // A whole group's category (iOS `.groupCategory` sheet): one choice for every row of the merchant.
    val pickingGroup = pickingGroupId?.let { id ->
        sections.firstNotNullOfOrNull { section ->
            section.entries.firstNotNullOfOrNull { entry -> (entry as? ReviewEntry.Group)?.group?.takeIf { it.id == id } }
        }
    }
    if (pickingGroup != null) {
        ReviewCategoryPickerSheet(
            choices = state.categories.filter { it.isIncome == pickingGroup.isIncome },
            selectedId = pickingGroup.sharedCategoryId,
            searchText = pickerSearch,
            onSearchChange = { pickerSearch = it },
            onSelect = { id ->
                if (id != null) {
                    viewModel.onGroupCategoryChosen(pickingGroup.itemKeys, id)
                    pickerSearch = ""
                    pickingGroupId = null
                }
            },
            onDismiss = { pickingGroupId = null },
            tagKey = "group",
            allowNone = false,
        )
    }

    // "Always ...?" waits until the editor or picker that started it has closed (iOS queues it the same way).
    val ask = state.ruleAsk
    if (ask != null && editing == null && pickingGroup == null) {
        RuleAskSheet(
            ask = ask,
            pastCount = state.askPastCount,
            alsoUpdatePast = state.askAlsoUpdate,
            isSaving = state.isSavingRule,
            onToggleUpdatePast = viewModel::toggleAskUpdatePast,
            onAlways = viewModel::answerAlways,
            onOnce = viewModel::answerOnce,
        )
    }
}

/** "Found 32 transactions" with what starts unchecked and why (iOS `scanSummaryHeader`). */
@Composable
private fun SummaryHeader(state: ScanReviewUiState) {
    val flagged = state.flaggedCount
    val ruled = state.ruledCount
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            "Found ${state.foundCount.counted("transaction")}",
            style = IosText.headline,
            color = IosColors.textPrimary,
            modifier = Modifier.testTag("review-found"),
        )
        if (flagged > 0) {
            Text(
                "$flagged ${if (flagged == 1) "is" else "are"} already in Spenditos or ${if (flagged == 1) "isn't" else "aren't"} spending, " +
                    "so ${if (flagged == 1) "it starts" else "they start"} unchecked.",
                style = IosText.subheadline,
                color = IosColors.textSecondary,
            )
        }
        if (ruled > 0) {
            Text(
                "$ruled handled by your rules. Pick a category once and Spenditos can remember it.",
                style = IosText.subheadline,
                color = IosColors.textSecondary,
                modifier = Modifier.testTag("review-rules-summary"),
            )
        }
        if (state.failedDocuments > 0) {
            Text("${state.failedDocuments.counted("page")} couldn't be read.", style = IosText.subheadline, color = IosColors.warning)
        }
    }
}

/** iOS `successView`. */
@Composable
private fun DoneContent(state: ScanReviewUiState, onDone: () -> Unit, onScanMore: () -> Unit, modifier: Modifier) {
    val lines = buildList {
        if (state.skippedCount > 0) add("${state.skippedCount} skipped: already in Spenditos or not spending")
        if (state.failedCount > 0) add("${state.failedCount} couldn't be saved")
    }
    Column(
        modifier.fillMaxWidth().padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(67.2.dp))
        Text(
            "${state.addedCount.counted("transaction")} added",
            style = IosText.title3.w(FontWeight.SemiBold),
            color = IosColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("review-done-title"),
        )
        if (lines.isNotEmpty()) {
            Text(
                lines.joinToString("\n"),
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("review-done-subtitle"),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PillButton(stringResource(R.string.common_done), onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("review-done"))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                    .clickable(role = Role.Button, onClick = onScanMore)
                    .testTag("review-scan-more"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.scan_scan_more), style = IosText.headline, color = IosColors.textPrimary)
            }
        }
    }
}

@Composable
private fun OpenError(message: String, onRetry: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        Icon(SfSymbols.icon("exclamationmark.triangle.fill"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.size(48.dp))
        Text(message, style = IosText.body, color = IosColors.neutral, textAlign = TextAlign.Center, modifier = Modifier.testTag("scan.review.error"))
        PillButton(stringResource(R.string.common_try_again_2), onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("scan.review.retry"))
        PillButton(stringResource(R.string.common_close), onClick = onClose, filled = false, modifier = Modifier.fillMaxWidth().testTag("scan.review.close"))
    }
}

/** iOS confirmationDialog "Discard this scan?": Discard scan / Review later / Keep reviewing. */
@Composable
private fun DiscardDialog(viewModel: ScanReviewViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::keepReviewing,
        title = { Text(stringResource(R.string.scan_discard_this_scan), style = IosText.headline) },
        text = { Text(stringResource(R.string.scan_these_transactions_won_t_be), style = IosText.callout, color = IosColors.textSecondary) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = viewModel::discardScan, modifier = Modifier.testTag("review-discard-scan")) {
                    Text(stringResource(R.string.scan_discard_scan), style = IosText.body, color = IosColors.expense)
                }
                TextButton(onClick = viewModel::reviewLater, modifier = Modifier.testTag("review-later")) {
                    Text(stringResource(R.string.scan_review_later), style = IosText.body, color = IosColors.spendOrange)
                }
                TextButton(onClick = viewModel::keepReviewing, modifier = Modifier.testTag("review-keep")) {
                    Text(stringResource(R.string.scan_keep_reviewing), style = IosText.body, color = IosColors.spendOrange)
                }
            }
        },
    )
}
