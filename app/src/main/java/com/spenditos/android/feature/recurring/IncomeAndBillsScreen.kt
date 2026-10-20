package com.spenditos.android.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.feature.debts.DebtAddSheetHost
import com.spenditos.android.feature.debts.DebtListViewModel
import com.spenditos.android.feature.debts.debtsSection
import com.spenditos.android.ui.components.FloatingTabBarHeight
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.tabBarBottomPadding
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.YearMonth

/** Saves the generate-month picker selection across recreation as "yyyy-MM". */
internal val YearMonthSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = { saved -> runCatching { YearMonth.parse(saved) }.getOrNull() },
)

@Composable
fun IncomeAndBillsScreen(
    viewModel: IncomeAndBillsViewModel = hiltViewModel(),
    debtsViewModel: DebtListViewModel = hiltViewModel(),
    onOpenDebt: (debtId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val debts by debtsViewModel.state.collectAsStateWithLifecycle()
    var showAddDebt by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    // Recreations keep only ids; the templates are resolved from the current state below and
    // the sheet/dialog closes when an id no longer resolves (deleted or refreshed away).
    var templateToEditId by rememberSaveable { mutableStateOf<String?>(null) }
    var templateToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGenerate by rememberSaveable { mutableStateOf(false) }
    var generateMonth by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var addPresetIncome by rememberSaveable { mutableStateOf(false) }
    val templateToEdit = templateToEditId?.let { id -> state.templates.firstOrNull { it.id == id } }
    val templateToDelete = templateToDeleteId?.let { id -> state.templates.firstOrNull { it.id == id } }

    LaunchedEffect(templateToEditId, templateToDeleteId, showEditor, state.isLoading, state.templates) {
        if (!state.isLoading) {
            if (showEditor && templateToEditId != null && templateToEdit == null) {
                showEditor = false
                templateToEditId = null
            }
            if (templateToDeleteId != null && templateToDelete == null) templateToDeleteId = null
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                IncomeAndBillsEvent.Saved -> { showEditor = false; templateToEditId = null }
                IncomeAndBillsEvent.Deleted -> templateToDeleteId = null
                is IncomeAndBillsEvent.Generated -> {
                    if (event.result.errors == 0) showGenerate = false
                    snackbar.showSnackbar("Generated ${event.result.generated} transactions. ${event.result.skipped} skipped, ${event.result.errors} errors.")
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("recurring.list"),
                contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
            if (state.isLoading && state.templates.isEmpty() && !debts.hasDebts) {
                item("loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            CircularProgressIndicator(Modifier.size(30.dp).testTag("recurring.loading"), color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { templateToEditId = null; showEditor = true }, modifier = Modifier.testTag("recurring.add")) { Text("Add") }
                        }
                    }
                }
            }
            if (state.isLoading && state.templates.isNotEmpty()) {
                item("refreshing") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("recurring.refreshing"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        Text("Updating…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error?.let { error ->
                item("error") {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen).testTag("recurring.error")) {
                        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            if (state.isOffline) Text(if (state.templates.isEmpty()) "No saved list yet." else "Showing saved data.", modifier = Modifier.testTag("recurring.offline"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = viewModel::refresh, modifier = Modifier.testTag("recurring.retry")) { Text("Try again") }
                        }
                    }
                }
            }
            if (state.isEmpty && !debts.hasDebts) {
                item("empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.fillMaxWidth().testTag("recurring.empty").padding(vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(SfSymbols.icon("arrow.triangle.2.circlepath"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(56.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No income or bills yet", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                                Text(
                                    "Everything that comes in or goes out on a schedule. Each one is logged on its date.",
                                    style = IosText.callout,
                                    color = IosColors.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)
                                    .clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange)
                                    .clickable(role = Role.Button) { addPresetIncome = true; templateToEditId = null; showEditor = true }
                                    .testTag("recurring.empty.add").padding(horizontal = Spacing.l, vertical = Spacing.m),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(SfSymbols.icon("plus.circle.fill"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(20.dp))
                                Text("Add income", style = IosText.headline, color = IosColors.textPrimary)
                            }
                            Row(
                                Modifier.clickable(role = Role.Button) { showAddDebt = true }.testTag("debts.empty.add"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(SfSymbols.icon("creditcard"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(19.2.dp))
                                Text("Add a debt", style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.spendOrange)
                            }
                        }
                    }
                }
            } else {
                item("tools") { RecurringToolsCard(onGenerate = { showGenerate = true }) }

                item("bills.header") {
                    Row(Modifier.fillMaxWidth().padding(end = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                        RecurringSectionHeader("Bills", Modifier.weight(1f).testTag("recurring.section.bills"))
                        TextButton(onClick = { addPresetIncome = false; templateToEditId = null; showEditor = true }, modifier = Modifier.padding(end = Spacing.screen).testTag("recurring.add")) { Text("Add") }
                    }
                }
                if (!state.isLoading && state.billTemplates.isEmpty() && (state.templates.isNotEmpty() || debts.hasDebts)) {
                    item("bills.empty") { SectionEmptyMessage("No bills yet.", Modifier.testTag("recurring.bills.empty")) }
                }
                items(state.billTemplates, key = RecurringTemplate::id) { template ->
                    val category = state.categories.firstOrNull { it.id == template.categoryId }
                    IncomeBillRow(
                        template = template,
                        categoryName = category?.name,
                        categoryColorHex = category?.color,
                        categoryIcon = category?.icon,
                        isIncome = false,
                        mainIncomeTemplateId = state.mainIncomeTemplateId,
                        currency = state.currency,
                        onEdit = { templateToEditId = template.id; showEditor = true },
                        onDelete = { templateToDeleteId = template.id },
                        onMainIncome = {},
                    )
                }

                debtsSection(debts, onOpen = onOpenDebt, onAdd = { showAddDebt = true }, onRetry = debtsViewModel::retry)

                item("income.header") { RecurringSectionHeader("Income", Modifier.testTag("recurring.section.income")) }
                if (!state.isLoading && state.incomeTemplates.isEmpty() && (state.templates.isNotEmpty() || debts.hasDebts)) {
                    item("income.empty") { SectionEmptyMessage("No income yet.", Modifier.testTag("recurring.income.empty")) }
                }
                items(state.incomeTemplates, key = RecurringTemplate::id) { template ->
                    val category = state.categories.firstOrNull { it.id == template.categoryId }
                    IncomeBillRow(
                        template = template,
                        categoryName = category?.name,
                        categoryColorHex = category?.color,
                        categoryIcon = category?.icon,
                        isIncome = true,
                        mainIncomeTemplateId = state.mainIncomeTemplateId,
                        currency = state.currency,
                        onEdit = { templateToEditId = template.id; showEditor = true },
                        onDelete = { templateToDeleteId = template.id },
                        onMainIncome = { viewModel.setMainIncome(if (state.mainIncomeTemplateId == template.id) null else template.id) },
                    )
                }
            }
            }
            if (state.templates.isNotEmpty() || debts.hasDebts) {
                // Same spot as the Home add button: 10dp above the floating tab bar.
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(
                        end = Spacing.screen,
                        bottom = tabBarBottomPadding() + FloatingTabBarHeight + 10.dp,
                    ),
                ) {
                    Box(
                        Modifier.size(56.dp)
                            .dropShadow(CircleShape, Shadow(radius = 16.dp, color = IosColors.spendOrange, offset = DpOffset(0.dp, 4.dp), alpha = 0.4f))
                            .clip(CircleShape).background(IosColors.spendOrange)
                            .clickable(role = Role.Button) { showAddMenu = true }
                            .testTag("recurring.fab"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SfSymbols.icon("plus"), contentDescription = "Add income, bill or debt", tint = IosColors.textPrimary, modifier = Modifier.size(24.dp))
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }, offset = DpOffset(0.dp, (-8).dp)) {
                        DropdownMenuItem(
                            text = { Text("Add income", style = IosText.subheadline) },
                            leadingIcon = { Icon(SfSymbols.icon("arrow.down.circle"), contentDescription = null) },
                            onClick = { showAddMenu = false; addPresetIncome = true; templateToEditId = null; showEditor = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add bill", style = IosText.subheadline) },
                            leadingIcon = { Icon(SfSymbols.icon("arrow.up.circle"), contentDescription = null) },
                            onClick = { showAddMenu = false; addPresetIncome = false; templateToEditId = null; showEditor = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add a debt", style = IosText.subheadline) },
                            leadingIcon = { Icon(SfSymbols.icon("creditcard"), contentDescription = null) },
                            onClick = { showAddMenu = false; showAddDebt = true },
                            modifier = Modifier.testTag("recurring.fab.addDebt"),
                        )
                    }
                }
            }
        }
    }

    if (showAddDebt) {
        // AddDebtSheet scopes its own ViewModelStore, so the form never reopens with the last attempt's input.
        DebtAddSheetHost(
            onDismiss = { showAddDebt = false },
            onCreated = { debtId -> showAddDebt = false; onOpenDebt(debtId) },
        )
    }

    if (showEditor) {
        AddEditRecurringSheet(
            template = templateToEdit,
            categories = state.categories,
            currency = state.currency,
            initialIsIncome = addPresetIncome,
            isSaving = state.isWriting,
            error = state.error,
            onDismiss = { showEditor = false; templateToEditId = null },
            onSave = { draft ->
                if (templateToEdit == null) viewModel.create(draft.toCreateRequest())
                else viewModel.update(requireNotNull(templateToEdit).id, draft.toUpdateRequest())
            },
        )
    }

    templateToDelete?.let { template ->
        val deletingIncome = state.categories.firstOrNull { it.id == template.categoryId }?.isIncome == true
        AlertDialog(
            onDismissRequest = { templateToDeleteId = null },
            title = { Text(if (deletingIncome) "Delete this income?" else "Delete this bill?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Are you sure you want to delete this income or bill? This action cannot be undone.")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurring.delete.error")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(template.id) }, enabled = !state.isWriting, modifier = Modifier.testTag("recurring.delete.confirm")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { templateToDeleteId = null }, modifier = Modifier.testTag("recurring.delete.cancel")) { Text("Cancel") }
            },
        )
    }

    if (showGenerate) {
        GenerateMonthSheet(
            templates = state.templates,
            currency = state.currency,
            month = generateMonth,
            isGenerating = state.isWriting,
            error = state.error,
            onMonthChange = { generateMonth = it },
            onDismiss = { showGenerate = false },
            onGenerate = viewModel::generate,
        )
    }
}

@Composable
private fun RecurringToolsCard(onGenerate: () -> Unit) {
    GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.s)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecurringToolPill(
                icon = SfSymbols.icon("calendar.badge.plus"),
                text = "Generate Month",
                onClick = onGenerate,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.open"),
                filled = true,
                enabled = true,
            )
            RecurringToolPill(
                icon = SfSymbols.icon("banknote.fill"),
                text = "Pay periods",
                onClick = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
                filled = false,
                enabled = false,
            )
        }
    }
}

@Composable
private fun RecurringToolPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(Radius.m)
    val background = IosColors.spendOrange
    val foreground = if (filled) androidx.compose.ui.graphics.Color.White else IosColors.textPrimary
    Row(
        modifier.then(if (filled) Modifier.clip(shape).background(background) else subtleGlassModifier(shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(17.dp))
        Text(text, modifier = Modifier.padding(start = 6.dp, end = 12.dp), style = IosText.subheadline.w(FontWeight.SemiBold), color = foreground, maxLines = 1)
    }
}

@Composable
private fun RecurringSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = IosText.headline,
        color = IosColors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.s),
    )
}

@Composable
private fun SectionEmptyMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = IosText.subheadline,
        color = IosColors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
    )
}

@Composable
private fun IncomeBillRow(
    template: RecurringTemplate,
    categoryName: String?,
    categoryColorHex: String?,
    categoryIcon: String?,
    isIncome: Boolean,
    mainIncomeTemplateId: String?,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMainIncome: () -> Unit,
) {
    val finance = LocalFinanceColors.current
    val tint = categoryColor(categoryColorHex)
    val amount = Money.format(template.amount, currency)
    val canEdit = template.type == "fixed"
    val isMain = template.id == mainIncomeTemplateId

    var showActions by remember(template.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                // Old estimates cannot be edited but must stay deletable: their tap opens the actions menu.
                .combinedClickable(
                    role = Role.Button,
                    onClick = { if (canEdit) onEdit() else showActions = true },
                    onLongClickLabel = "More actions",
                    onLongClick = { showActions = true },
                )
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .testTag("recurring.row.${template.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    CategoryIcons.symbol(categoryIcon, categoryName),
                    contentDescription = null,
                    tint = IosColors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(template.description, style = IosText.body.w(FontWeight.Medium), color = IosColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        "${if (template.amountVaries) "≈ " else ""}$amount",
                        color = if (isIncome) finance.income else finance.expense,
                        style = IosText.body.w(FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(frequencyText(template), style = IosText.caption.w(FontWeight.Medium), color = IosColors.spendOrange)
                    Text("·", style = IosText.caption, color = IosColors.neutral)
                    if (!canEdit) {
                        Text(
                            "Old estimate · not logged",
                            Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            style = IosText.caption2.w(FontWeight.SemiBold),
                            color = IosColors.textSecondary,
                        )
                    }
                    if (isMain) {
                        Text(
                            "Main Income",
                            Modifier.testTag("recurring.mainIncome.badge.${template.id}")
                                .background(finance.income.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            color = finance.income,
                            style = IosText.caption2.w(FontWeight.SemiBold),
                        )
                    }
                    if (!template.isActive) {
                        Text(
                            "Paused",
                            Modifier.testTag("recurring.paused.${template.id}")
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            color = IosColors.textSecondary,
                            style = IosText.caption2.w(FontWeight.Medium),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isIncome && (isMain || canEdit)) {
                        Box(
                            // 24dp in the layout like iOS, but a 48dp touch target that overflows it.
                            Modifier.size(24.dp).wrapContentSize(unbounded = true).requiredSize(48.dp)
                                .testTag("recurring.mainIncome.${template.id}")
                                .clickable(enabled = isMain || (canEdit && template.isActive), role = Role.Button, onClick = onMainIncome),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                SfSymbols.icon(if (isMain) "star.fill" else "star"),
                                contentDescription = if (isMain) "Main income" else "Set as main income",
                                tint = if (isMain) IosColors.spendGold else IosColors.neutral,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            // Android has no iOS swipe actions; a visible button keeps Edit/Delete discoverable.
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "More actions") { showActions = true }
                    .semantics { contentDescription = "More actions for ${template.description}" }
                    .testTag("recurring.more.${template.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfSymbols.icon("ellipsis"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text("Edit", style = IosText.subheadline) },
                    leadingIcon = { Icon(SfSymbols.icon("pencil"), contentDescription = null) },
                    onClick = { showActions = false; onEdit() },
                    modifier = Modifier.testTag("recurring.edit.${template.id}"),
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", style = IosText.subheadline, color = IosColors.expense) },
                leadingIcon = { Icon(SfSymbols.icon("trash"), contentDescription = null, tint = IosColors.expense) },
                onClick = { showActions = false; onDelete() },
                modifier = Modifier.testTag("recurring.delete.${template.id}"),
            )
        }
    }
}

private fun frequencyText(template: RecurringTemplate): String {
    return when (template.frequency) {
        "bi-weekly" -> "Bi-weekly"
        "semi-monthly" -> "Semi-monthly"
        "semi-annually" -> "Semi-annually"
        else -> template.frequency.replaceFirstChar(Char::uppercase)
    }
}

private fun RecurringDraft.toCreateRequest() = CreateRecurringTemplateRequest(
    categoryId = requireNotNull(categoryId),
    description = description.trim(),
    amount = requireNotNull(amount).toDouble(),
    frequency = frequency,
    firstOccurrenceDate = firstOccurrenceDate.toString(),
    occurrencesRemaining = occurrencesRemaining?.let { JsonPrimitive(it) } ?: JsonNull,
    secondOccurrenceDay = if (frequency == "semi-monthly") secondOccurrenceDay?.let { JsonPrimitive(it) } ?: JsonNull else JsonNull,
    repeatInterval = requireNotNull(repeatInterval),
    amountVaries = amountVaries,
    isActive = isActive,
)

private fun RecurringDraft.toUpdateRequest() = UpdateRecurringTemplateRequest(
    categoryId = requireNotNull(categoryId),
    description = description.trim(),
    amount = requireNotNull(amount).toDouble(),
    type = "fixed",
    frequency = frequency,
    firstOccurrenceDate = firstOccurrenceDate.toString(),
    // The Never value must be JSON null on update so the backend clears the old cap.
    occurrencesRemaining = occurrencesRemaining?.let { JsonPrimitive(it) } ?: JsonNull,
    isActive = isActive,
    secondOccurrenceDay = if (frequency == "semi-monthly") secondOccurrenceDay?.let { JsonPrimitive(it) } ?: JsonNull else JsonNull,
    repeatInterval = requireNotNull(repeatInterval),
    pausedAt = if (isActive) null else Instant.now().toString(),
    amountVaries = amountVaries,
)
