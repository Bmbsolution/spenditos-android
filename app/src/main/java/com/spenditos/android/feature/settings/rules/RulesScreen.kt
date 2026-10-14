package com.spenditos.android.feature.settings.rules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.merchants.normalizeMerchantKey
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.IosSwitch
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/**
 * iOS `RulesListView` + `RuleEditView` (Settings > Rules). Rules are learned from the Scan review ("Always file Tim
 * Hortons under Dining") and applied to every scan and import. Tap a rule to open it; changes save as you make them.
 */
@Composable
fun RulesScreen(onBack: () -> Unit, viewModel: RulesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The edit screen is a pushed page on iOS; here it replaces the list and Back returns to it.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = editingId != null) { editingId = null }

    // A deleted rule (here or on another device after a refresh) closes its edit page.
    val editing = editingId?.let { state.rule(it) }
    LaunchedEffect(editingId, editing == null, state.isLoading) {
        if (editingId != null && editing == null && !state.isLoading) editingId = null
    }

    if (editing != null) {
        RuleEditPage(
            rule = editing,
            state = state,
            onBack = { editingId = null },
            onUpdate = { action, categoryId -> viewModel.update(editing.id, action, categoryId) },
            onDelete = { viewModel.delete(editing.id) { editingId = null } },
            onDismissError = viewModel::dismissError,
        )
    } else {
        RulesListPage(state, viewModel, onBack = onBack, onOpen = { editingId = it })
    }
}

// region List

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesListPage(state: RulesUiState, viewModel: RulesViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var showAdd by rememberSaveable { mutableStateOf(false) }

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            RulesTopBar(title = "Rules", onBack = onBack, backTag = "rules.back") {
                IconButton(onClick = { showAdd = true }, modifier = Modifier.size(44.dp).testTag("rules.add")) {
                    Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = "Add rule", tint = IosColors.spendOrange, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.fillMaxSize().padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                state.error?.let {
                    ErrorBanner(it, Modifier.padding(horizontal = Spacing.screen).testTag("rules.error"), onDismiss = viewModel::dismissError)
                }
                when {
                    state.isLoading && state.rules.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("rules.loading"), color = IosColors.spendOrange)
                    }
                    state.rules.isEmpty() -> PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                            EmptyState(
                                icon = "arrow.triangle.branch",
                                title = "No rules yet",
                                subtitle = "Rules appear when you choose 'Always' while reviewing a scan.",
                                modifier = Modifier.testTag("rules-empty"),
                            )
                        }
                    }
                    else -> PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).testTag("rules.list"),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Text(
                                "Learned from your reviews. Spenditos applies them to every scan and import.",
                                style = IosText.subheadline,
                                color = IosColors.textSecondary,
                                modifier = Modifier.padding(horizontal = Spacing.xs).padding(bottom = Spacing.xs),
                            )
                            state.rules.forEach { rule ->
                                RuleRow(rule, RulesViewModel.actionText(rule, state.categories), onClick = { onOpen(rule.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRuleSheet(
            state = state,
            onDismiss = { showAdd = false; viewModel.dismissAddError() },
            onSave = { merchant, skip, categoryId -> viewModel.add(merchant, skip, categoryId) { showAdd = false } },
            onDismissError = viewModel::dismissAddError,
        )
    }
}

/** iOS inline navigation bar: back chevron, 17pt semibold title, trailing item. */
@Composable
private fun RulesTopBar(title: String, onBack: () -> Unit, backTag: String, trailing: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag(backTag)) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text(title, style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** iOS `EmptyStateView` without a button. */
@Composable
private fun EmptyState(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(SfSymbols.icon(icon), contentDescription = null, tint = IosColors.spendOrange.copy(alpha = 0.6f), modifier = Modifier.size(57.6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text(subtitle, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

@Composable
private fun RuleRow(rule: MerchantMapping, actionText: String, onClick: () -> Unit) {
    GlassCard(Modifier.testTag("rule-row"), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    rule.displayName,
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    actionText,
                    style = IosText.subheadline,
                    color = if (rule.ruleAction == RuleAction.SKIP) IosColors.textSecondary else IosColors.spendOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.4.dp))
        }
    }
}

// endregion

// region Edit

/** Edit one rule: pick the category it files under, or switch on "Skip it". Changes save as you make them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditPage(
    rule: MerchantMapping,
    state: RulesUiState,
    onBack: () -> Unit,
    onUpdate: (RuleAction, String?) -> Unit,
    onDelete: () -> Unit,
    onDismissError: () -> Unit,
) {
    // The switch and the category to return to are local, like iOS: turning Skip off with no category to go back to
    // leaves a skip rule until one is picked.
    var skipIt by rememberSaveable(rule.id) { mutableStateOf(rule.ruleAction == RuleAction.SKIP) }
    var lastCategoryId by rememberSaveable(rule.id) { mutableStateOf(rule.categoryId) }
    var confirmDelete by rememberSaveable(rule.id) { mutableStateOf(false) }

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            RulesTopBar(title = "Rule", onBack = onBack, backTag = "rules.edit.back")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen).padding(top = Spacing.m, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                state.error?.let { ErrorBanner(it, Modifier.testTag("rules.error"), onDismiss = onDismissError) }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("When the merchant is", style = IosText.subheadline, color = IosColors.textSecondary)
                    Text(
                        rule.displayName,
                        style = IosText.title3.w(FontWeight.SemiBold),
                        color = IosColors.textPrimary,
                        modifier = Modifier.testTag("rule-edit-merchant"),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("File it under", style = IosText.subheadline, color = IosColors.textSecondary)
                    FlowRow(
                        Modifier.fillMaxWidth().alpha(if (skipIt) 0.4f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        state.categories.forEach { category ->
                            CategoryChip(category, selected = !skipIt && rule.categoryId == category.id) {
                                if (skipIt) return@CategoryChip
                                lastCategoryId = category.id
                                onUpdate(RuleAction.CATEGORIZE, category.id)
                            }
                        }
                    }
                }

                SkipCard(
                    skipIt = skipIt,
                    onChange = { on ->
                        skipIt = on
                        val back = lastCategoryId
                        if (on) onUpdate(RuleAction.SKIP, null) else if (back != null) onUpdate(RuleAction.CATEGORIZE, back)
                        // With no category to go back to, the rule stays a skip rule until the user picks one.
                    },
                    toggleTag = "rule-edit-skip",
                    footer = if (!skipIt && rule.ruleAction == RuleAction.SKIP) "Pick a category to finish." else null,
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PillButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("rule-edit-done"))
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clickable(enabled = !state.isDeleting, role = Role.Button) { confirmDelete = true }
                            .testTag("rule-edit-delete"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Delete rule", style = IosText.body, color = IosColors.expense)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete rule?", style = IosText.headline) },
            text = { Text("Spenditos will stop applying the rule for \"${rule.displayName}\".", style = IosText.callout) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag("rule-edit-delete-confirm")) {
                    Text("Delete", color = IosColors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** iOS `chip(...)`: capsule, 36pt minimum height; filled terracotta when selected. */
@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        Modifier.heightIn(min = 36.dp).clip(shape)
            .background(if (selected) IosColors.spendOrange else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) IosColors.spendOrange else IosColors.separator, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .testTag("rule-edit-category"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            CategoryIcons.symbol(category.icon, category.name),
            contentDescription = null,
            tint = if (selected) Color.White else IosColors.textPrimary,
            modifier = Modifier.size(14.4.dp),
        )
        Text(
            category.name,
            style = IosText.callout,
            color = if (selected) Color.White else IosColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The "Skip it" switch with its explanation, in a glass card (shared by the edit page and the add sheet). */
@Composable
private fun SkipCard(skipIt: Boolean, onChange: (Boolean) -> Unit, toggleTag: String, footer: String?) {
    Column(
        Modifier.fillMaxWidth().then(glassCardModifier()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Skip it", style = IosText.body.w(FontWeight.SemiBold), color = IosColors.textPrimary, modifier = Modifier.weight(1f))
            IosSwitch(checked = skipIt, onCheckedChange = onChange, modifier = Modifier.testTag(toggleTag), tint = IosColors.spendOrange)
        }
        Text("Card payments and transfers that aren't spending", style = IosText.subheadline, color = IosColors.textSecondary)
        footer?.let { Text(it, style = IosText.subheadline, color = IosColors.spendOrange) }
    }
}

// endregion

// region Add

/** iOS `AddRuleSheet`: a merchant name and what to do with it. It is an upsert, so an existing merchant's rule is updated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleSheet(
    state: RulesUiState,
    onDismiss: () -> Unit,
    onSave: (merchant: String, skip: Boolean, categoryId: String?) -> Unit,
    onDismissError: () -> Unit,
) {
    var merchant by rememberSaveable { mutableStateOf("") }
    var skipIt by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val canSave = normalizeMerchantKey(merchant.trim()).isNotEmpty() && (skipIt || categoryId != null) && !state.isAdding

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().testTag("rules.add.sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.width(80.dp)) {
                    Text("Cancel", style = IosText.body, color = IosColors.textSecondary)
                }
                Text("New Rule", style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(Modifier.width(80.dp))
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen).padding(top = Spacing.m, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                state.addError?.let { ErrorBanner(it, Modifier.testTag("rules.add.error"), onDismiss = onDismissError) }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("Merchant", style = IosText.subheadline, color = IosColors.textSecondary)
                    BasicTextField(
                        value = merchant,
                        onValueChange = { merchant = it.take(200) },
                        singleLine = true,
                        textStyle = IosText.body.copy(color = IosColors.textPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(IosColors.spendOrange),
                        modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag("rule-add-merchant"),
                        decorationBox = { inner ->
                            Box {
                                if (merchant.isEmpty()) Text("e.g. Tim Hortons, Uber, Amazon", style = IosText.body, color = IosColors.textTertiary)
                                inner()
                            }
                        },
                    )
                    Text(
                        "Ignores case and punctuation. Matches transactions from this merchant.",
                        style = IosText.caption,
                        color = IosColors.textTertiary,
                    )
                }

                SkipCard(skipIt = skipIt, onChange = { skipIt = it }, toggleTag = "rule-add-skip", footer = null)

                if (!skipIt) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("File it under", style = IosText.subheadline, color = IosColors.textSecondary)
                        state.categories.filter { !it.isIncome }.takeIf { it.isNotEmpty() }?.let { rows ->
                            CategorySection("Expense", rows, categoryId) { categoryId = it }
                        }
                        state.categories.filter { it.isIncome }.takeIf { it.isNotEmpty() }?.let { rows ->
                            CategorySection("Income", rows, categoryId) { categoryId = it }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    PillButton(
                        text = "Create Rule",
                        onClick = { onSave(merchant, skipIt, categoryId) },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth().testTag("rule-add-save"),
                    )
                    if (state.isAdding) {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(title: String, categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title.uppercase(), style = IosText.caption, color = IosColors.textTertiary, modifier = Modifier.padding(start = Spacing.xs))
        GlassCard {
            categories.forEachIndexed { index, category ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(category.id) }
                        .semantics { this.selected = selectedId == category.id }
                        .padding(horizontal = Spacing.m, vertical = Spacing.s)
                        .testTag("rule-add-category.${category.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category.color)))
                    Icon(CategoryIcons.symbol(category.icon, category.name), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(24.dp))
                    Text(category.name, style = IosText.callout, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
                    if (selectedId == category.id) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = IosColors.spendOrange)
                }
                if (index < categories.lastIndex) HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))
            }
        }
    }
}

// endregion
