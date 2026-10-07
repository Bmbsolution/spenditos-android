package com.spenditos.android.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.YearMonth

@Composable
fun IncomeAndBillsScreen(viewModel: IncomeAndBillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var templateToEdit by remember { mutableStateOf<RecurringTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<RecurringTemplate?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var generateMonth by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                IncomeAndBillsEvent.Saved -> { showEditor = false; templateToEdit = null }
                IncomeAndBillsEvent.Deleted -> templateToDelete = null
                is IncomeAndBillsEvent.Generated -> {
                    if (event.result.errors == 0) showGenerate = false
                    snackbar.showSnackbar("Generated ${event.result.generated} transactions. ${event.result.skipped} skipped, ${event.result.errors} errors.")
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("recurring.list"),
            contentPadding = PaddingValues(top = Spacing.s, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.isLoading && state.templates.isEmpty()) {
                item("loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            CircularProgressIndicator(Modifier.size(30.dp).testTag("recurring.loading"), color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { templateToEdit = null; showEditor = true }, modifier = Modifier.testTag("recurring.add")) { Text("Add") }
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
            if (state.isEmpty) {
                item("empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen).testTag("recurring.empty")) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                            Text("No income or bills yet.", style = MaterialTheme.typography.titleMedium)
                            Text("Add a recurring item to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { templateToEdit = null; showEditor = true }, modifier = Modifier.testTag("recurring.add")) { Text("Add") }
                            PillButton(
                                "Add",
                                onClick = { templateToEdit = null; showEditor = true },
                                modifier = Modifier.fillMaxWidth().testTag("recurring.empty.add"),
                            )
                        }
                    }
                    }
                }
            } else {
                item("tools") { RecurringToolsCard(onGenerate = { showGenerate = true }) }

                item("bills.header") {
                    Row(Modifier.fillMaxWidth().padding(end = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Bills", Modifier.weight(1f).testTag("recurring.section.bills"))
                        TextButton(onClick = { templateToEdit = null; showEditor = true }, modifier = Modifier.testTag("recurring.add")) { Text("Add") }
                    }
                }
                if (!state.isLoading && state.billTemplates.isEmpty() && state.templates.isNotEmpty()) {
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
                        onEdit = { templateToEdit = template; showEditor = true },
                        onDelete = { templateToDelete = template },
                        onMainIncome = {},
                    )
                }

                item("income.header") { SectionHeader("Income", Modifier.testTag("recurring.section.income")) }
                if (!state.isLoading && state.incomeTemplates.isEmpty() && state.templates.isNotEmpty()) {
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
                        onEdit = { templateToEdit = template; showEditor = true },
                        onDelete = { templateToDelete = template },
                        onMainIncome = { viewModel.setMainIncome(if (state.mainIncomeTemplateId == template.id) null else template.id) },
                    )
                }
            }
        }
    }

    if (showEditor) {
        AddEditRecurringSheet(
            template = templateToEdit,
            categories = state.categories,
            currency = state.currency,
            isSaving = state.isWriting,
            error = state.error,
            onDismiss = { showEditor = false; templateToEdit = null },
            onSave = { draft ->
                if (templateToEdit == null) viewModel.create(draft.toCreateRequest())
                else viewModel.update(requireNotNull(templateToEdit).id, draft.toUpdateRequest())
            },
        )
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete recurring item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Delete ${template.description}?")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurring.delete.error")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(template.id) }, enabled = !state.isWriting, modifier = Modifier.testTag("recurring.delete.confirm")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { templateToDelete = null }, modifier = Modifier.testTag("recurring.delete.cancel")) { Text("Cancel") }
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
    GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecurringToolPill(
                icon = Icons.Filled.DateRange,
                text = "Generate Month",
                onClick = onGenerate,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("recurring.generate.open"),
                filled = true,
                enabled = true,
            )
            RecurringToolPill(
                icon = Icons.Filled.AccountBalance,
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
    val shape = RoundedCornerShape(26.dp)
    val background = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val foreground = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.height(52.dp).clip(shape).background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.padding(start = 6.dp, end = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 1)
    }
}

@Composable
private fun SectionEmptyMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.screen),
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

    GroupedCard(Modifier.testTag("recurring.row.${template.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconChip(recurringIcon(categoryIcon, categoryName, template.description), tint, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(template.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    if (isMain) Text("Main income", color = finance.income, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("recurring.mainIncome.badge.${template.id}"))
                    if (!template.isActive) Text("Paused", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("recurring.paused.${template.id}"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(frequencyText(template), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("  ·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "${if (template.amountVaries) "≈ " else ""}$amount",
                color = if (isIncome) finance.income else finance.expense,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 74.dp, end = Spacing.s, bottom = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isIncome && (isMain || canEdit)) {
                TextButton(
                    onClick = onMainIncome,
                    enabled = isMain || (canEdit && template.isActive),
                    modifier = Modifier.testTag("recurring.mainIncome.${template.id}"),
                ) { Text(if (isMain) "★ Main income" else "☆ Set as main", style = MaterialTheme.typography.labelSmall) }
            }
            if (canEdit) TextButton(onClick = onEdit, modifier = Modifier.testTag("recurring.edit.${template.id}")) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("recurring.delete.${template.id}")) { Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun recurringIcon(icon: String?, categoryName: String?, description: String) = run {
    val key = "${icon.orEmpty()} ${categoryName.orEmpty()} $description".lowercase()
    when {
        "🏠" in key || "home" in key || "rent" in key || "house" in key || "housing" in key -> Icons.Filled.Home
        "🚗" in key || "car" in key || "auto" in key || "vehicle" in key || "daycare" in key -> Icons.Filled.DirectionsCar
        "credit" in key || "card" in key || "loan" in key -> Icons.Filled.CreditCard
        "🌐" in key || "internet" in key || "wifi" in key || "web" in key -> Icons.Filled.Language
        "📱" in key || "phone" in key || "mobile" in key -> Icons.Filled.PhoneIphone
        "gym" in key || "fitness" in key -> Icons.Filled.FitnessCenter
        "🍽" in key || "food" in key || "dining" in key || "restaurant" in key || "grocer" in key -> Icons.Filled.Restaurant
        "🛒" in key || "shop" in key || "store" in key -> Icons.Filled.ShoppingCart
        "✈" in key || "travel" in key || "flight" in key -> Icons.Filled.Flight
        "🐾" in key || "pet" in key -> Icons.Filled.Pets
        "❤️" in key || "health" in key || "medical" in key -> Icons.Filled.Favorite
        "📚" in key || "school" in key || "education" in key -> Icons.Filled.School
        "💼" in key || "work" in key || "salary" in key || "job" in key -> Icons.Filled.Work
        "bank" in key || "saving" in key -> Icons.Filled.AccountBalance
        "🏷" in key || "🎁" in key || "tag" in key || "subscription" in key -> Icons.Filled.LocalOffer
        "💰" in key || "income" in key || "pay" in key -> Icons.Filled.AttachMoney
        else -> Icons.Filled.Receipt
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
