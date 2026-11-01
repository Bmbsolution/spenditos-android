package com.spenditos.android.feature.transactions

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.ui.components.CategoryIcon
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.FloatingTabBarHeight
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.components.tabBarBottomPadding
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Home's Bills left asks the list for the expenses between two days. */
data class UpcomingExpensesRequest(val start: java.time.LocalDate, val end: java.time.LocalDate)

@Composable
fun TransactionListScreen(
    onOpenNotSynced: () -> Unit,
    request: UpcomingExpensesRequest? = null,
    onRequestHandled: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (EditTarget) -> Unit = {},
    onDuplicate: (AddTransactionPrefill) -> Unit = {},
    onEditModeChange: (Boolean) -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<TransactionRowUi?>(null) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(request) {
        if (request != null) {
            viewModel.showExpensesBetween(request.start, request.end)
            onRequestHandled()
        }
    }
    androidx.compose.runtime.LaunchedEffect(isEditMode) { onEditModeChange(isEditMode) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onEditModeChange(false) } }
    var selectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteAlert by rememberSaveable { mutableStateOf(false) }
    val rowActions = RowActions(
        onEdit = { row -> row.edit?.let(onEdit) },
        onDuplicate = { row ->
            row.edit?.let { e ->
                onDuplicate(AddTransactionPrefill(amount = e.amount, description = e.description, date = e.date.toString(), categoryId = e.categoryId))
            }
        },
        onDelete = { row -> deleteTarget = row },
    )
    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.transactions_delete_transaction_3)) },
            text = { Text(stringResource(R.string.transactions_are_you_sure_you_want)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(row.id); deleteTarget = null }, modifier = Modifier.testTag("transaction.delete.confirm")) {
                    Text(stringResource(R.string.common_delete), color = IosColors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (showBulkDeleteAlert) {
        val selectedCount = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteAlert = false },
            title = { Text(Strings.plural(R.plurals.transactions_delete_transactions_n, selectedCount)) },
            text = {
                Text(
                    if (selectedCount == 1) {
                        stringResource(R.string.transactions_are_you_sure_you_want)
                    } else {
                        stringResource(R.string.transactions_are_you_sure_you_want_2, selectedCount)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteAlert = false
                        viewModel.bulkDelete(selectedIds) {
                            isEditMode = false
                            selectedIds = emptySet()
                        }
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = IosColors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteAlert = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    state.actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissActionError,
            title = { Text(stringResource(R.string.transactions_couldn_t_delete)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissActionError) { Text(stringResource(R.string.common_ok)) } },
        )
    }
    val locale = LocalConfiguration.current.locales[0]
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchText) { viewModel.setSearch(searchText) }
    val visibleDays = state.days
    val today = LocalDate.now()
    val upcomingDays = visibleDays.filter { (day, _) -> day.isAfter(today) }.sortedBy { it.first }
    val historyDays = visibleDays.filter { (day, _) -> !day.isAfter(today) }
    val upcomingCount = upcomingDays.sumOf { (_, rows) -> rows.size }
    val historyCount = historyDays.sumOf { (_, rows) -> rows.size }

    DismissKeyboardWhenInactive()
    DismissKeyboardWhen(showFilterSheet)
    IosScreen {
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val showScrollToTop by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > with(density) { 100.dp.toPx() }
            }
        }
        val scope = rememberCoroutineScope()
        // iOS pins the scroll-to-top button in the navigation bar toolbar (TransactionListView.swift 124-132),
        // so it stays on screen instead of scrolling away with the list. Overlay it outside the PullToRefreshBox.
        Box(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh(includeCategories = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                    .testTag("transactions.list"),
            ) {
                item(key = "title") {
                    Box {
                        LargeTitle(
                            stringResource(R.string.common_transactions),
                            trailing = if (isEditMode) null else ({
                                TransactionsToolbarCapsule(
                                    isSearchActive = isSearchActive,
                                    filtersActive = state.hasActiveFilters,
                                    onToggleSearch = {
                                        // Closing search also clears it, so no hidden filter stays on the list.
                                        if (isSearchActive) searchText = ""
                                        isSearchActive = !isSearchActive
                                    },
                                    onOpenFilter = { showFilterSheet = true },
                                )
                            }),
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = Spacing.screen, top = 8.dp)
                                .height(44.dp)
                                .then(glassCardModifier(CircleShape, shadowAlpha = 0.08f))
                                .clickable(role = Role.Button) {
                                    if (isEditMode) {
                                        isEditMode = false
                                        selectedIds = emptySet()
                                    } else {
                                        isEditMode = true
                                    }
                                }
                                .testTag("transaction-edit")
                                .semantics { contentDescription = if (isEditMode) Strings.get(R.string.transactions_cancel_selection) else Strings.get(R.string.transactions_edit_transactions) }
                                .padding(horizontal = Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isEditMode) stringResource(R.string.common_cancel) else stringResource(R.string.common_edit),
                                style = IosText.body.w(FontWeight.SemiBold),
                                color = IosColors.spendOrange,
                            )
                        }
                        // Keep the existing selector stable after removing the month switcher UI.
                        Box(Modifier.size(0.dp).testTag("transactions.month"))
                    }
                }

                if (isSearchActive) {
                    item(key = "search") {
                        SearchBar(
                            query = searchText,
                            onQueryChange = { searchText = it },
                            onClear = { searchText = "" },
                        )
                    }
                }

                if (state.hasActiveFilters) {
                    item(key = "filters") {
                        val filters = state.filters
                        val removals = buildList {
                            if (filters.dateRange != DateRangeFilter.THIS_MONTH) add(viewModel::removeDateFilter)
                            if (filters.selectedType != null) add(viewModel::removeTypeFilter)
                            if (filters.selectedCategoryIds.isNotEmpty()) add(viewModel::removeCategoryFilter)
                            if (filters.minAmount != null || filters.maxAmount != null) add(viewModel::removeAmountFilter)
                        }
                        ActiveFilterPills(
                            labels = activeFilterPillLabels(
                                filters = filters,
                                categoryNames = state.categories.associate { it.id to it.name },
                                currency = state.currency,
                                locale = locale,
                            ),
                            onRemove = { index ->
                                removals.getOrNull(index)?.invoke()
                                viewModel.refresh()
                            },
                        )
                    }
                }

                if (!state.isOnline || state.waitingCount > 0 || state.failedCount > 0 || state.loadError != null) {
                    item(key = "sync") {
                        SyncBanner(
                            state = state,
                            onOpenNotSynced = onOpenNotSynced,
                            modifier = Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s),
                        )
                    }
                }

                if (visibleDays.isNotEmpty()) {
                    if (upcomingDays.isNotEmpty()) {
                        stickyHeader(key = "upcoming") {
                            TransactionSectionHeader(
                                title = stringResource(R.string.transactions_upcoming),
                                icon = "calendar.badge.clock",
                                color = IosColors.spendOrange,
                                count = upcomingCount,
                                modifier = Modifier.testTag("transactions.summary"),
                            )
                        }
                        item(key = "upcoming-days") {
                            TransactionDaysCard(
                                days = upcomingDays,
                                currency = state.currency,
                                locale = locale,
                                upcoming = true,
                                actions = rowActions,
                                isEditMode = isEditMode,
                                selectedIds = selectedIds,
                                onToggleSelection = { id ->
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                },
                            )
                        }
                    }

                    if (historyDays.isNotEmpty()) {
                        stickyHeader(key = "history") {
                            TransactionSectionHeader(
                                title = stringResource(R.string.common_history),
                                icon = "clock.arrow.circlepath",
                                color = IosColors.textSecondary,
                                count = historyCount,
                                modifier = if (upcomingDays.isEmpty()) Modifier.testTag("transactions.summary") else Modifier,
                            )
                        }
                        item(key = "history-days") {
                            TransactionDaysCard(
                                days = historyDays,
                                currency = state.currency,
                                locale = locale,
                                upcoming = false,
                                actions = rowActions,
                                isEditMode = isEditMode,
                                selectedIds = selectedIds,
                                onToggleSelection = { id ->
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                },
                            )
                        }
                    }
                } else if (state.sourceEmpty && state.isRefreshing && searchText.isBlank()) {
                    item(key = "loading") {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 320.dp).testTag("transactions.summary"),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingState()
                        }
                    }
                } else {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp)
                                .then(if (searchText.isBlank()) Modifier.testTag("transactions.summary") else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(onAdd = onAdd)
                        }
                    }
                }
            }

            if (isEditMode && selectedIds.isNotEmpty()) {
                BulkDeleteButton(
                    selectedCount = selectedIds.size,
                    onClick = { showBulkDeleteAlert = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = tabBarBottomPadding() + FloatingTabBarHeight + 10.dp),
                )
            }
            }
            // Pinned where the iOS navigation bar toolbar sits; hidden while the list is at the top,
            // so it never covers the search/filter capsule.
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = Spacing.screen),
            ) {
                Box(
                    // 40dp visual like iOS; the 48dp touch target overflows it without changing the layout.
                    modifier = Modifier
                        .size(40.dp)
                        .wrapContentSize(Alignment.Center, unbounded = true)
                        .requiredSize(48.dp)
                        .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(0) } }
                        .testTag("scrollToTop"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .then(glassCardModifier(CircleShape, shadowAlpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            SfSymbols.icon("arrow.up.circle"),
                            contentDescription = stringResource(R.string.transactions_scroll_to_top),
                            tint = IosColors.textPrimary,
                            modifier = Modifier.size(20.4.dp),
                        )
                    }
                }
            }
        }
    }
    if (showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onSelectDateRange = viewModel::selectDateRange,
            onCustomStart = viewModel::setCustomStart,
            onCustomEnd = viewModel::setCustomEnd,
            onQuickToday = viewModel::applyQuickToday,
            onQuickThisWeek = { viewModel.applyQuickThisWeek(locale) },
            onQuickThisMonth = viewModel::applyQuickThisMonth,
            onQuickLastDays = viewModel::applyQuickLastDays,
            onType = viewModel::setType,
            onToggleCategory = viewModel::toggleCategory,
            onClearCategories = viewModel::clearCategories,
            onMinAmount = viewModel::setMinAmount,
            onMaxAmount = viewModel::setMaxAmount,
            onSavePreset = viewModel::savePreset,
            onLoadPreset = viewModel::loadPreset,
            onDeletePreset = viewModel::deletePreset,
            onApply = {
                viewModel.refresh()
                showFilterSheet = false
            },
            onReset = {
                viewModel.resetFilters()
                viewModel.refresh()
                showFilterSheet = false
            },
        )
    }
}

@Composable
private fun TransactionsToolbarCapsule(
    isSearchActive: Boolean,
    filtersActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val capsule = CircleShape
    Row(
        modifier = Modifier
            .clip(capsule),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            // 40dp visual like iOS; the 48dp touch target overflows it without changing the layout.
            modifier = Modifier
                .size(40.dp)
                .wrapContentSize(Alignment.Center, unbounded = true)
                .requiredSize(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onToggleSearch)
                .testTag("transaction-search"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    SfSymbols.icon(if (isSearchActive) "xmark.circle.fill" else "magnifyingglass"),
                    contentDescription = if (isSearchActive) stringResource(R.string.transactions_close_search) else stringResource(R.string.transactions_search_transactions),
                    tint = IosColors.textPrimary,
                    modifier = Modifier.size(20.4.dp),
                )
            }
        }
        Box(
            // 40dp visual like iOS; the 48dp touch target overflows it without changing the layout.
            modifier = Modifier
                .size(40.dp)
                .wrapContentSize(Alignment.Center, unbounded = true)
                .requiredSize(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenFilter)
                .testTag("transaction-filter")
                .semantics { if (filtersActive) stateDescription = Strings.get(R.string.transactions_filters_active) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    SfSymbols.icon("line.3.horizontal.decrease.circle"),
                    contentDescription = stringResource(R.string.transactions_filter_transactions),
                    tint = IosColors.textPrimary,
                    modifier = Modifier.size(20.4.dp),
                )
                if (filtersActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(8.dp)
                            .background(IosColors.spendOrange, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterPills(labels: List<String>, onRemove: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = Spacing.s)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.l))
                    .background(IosColors.spendOrange.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(label, style = IosText.caption.w(FontWeight.Medium), color = IosColors.spendOrange)
                Box(
                    // 14.4dp visual like iOS; the 48dp touch target overflows it without changing the layout.
                    modifier = Modifier
                        .size(14.4.dp)
                        .wrapContentSize(Alignment.Center, unbounded = true)
                        .requiredSize(48.dp)
                        .clickable(role = Role.Button) { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SfSymbols.icon("xmark.circle.fill"),
                        contentDescription = stringResource(R.string.transactions_remove_filter),
                        tint = IosColors.spendOrange.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.s)
            .then(subtleGlassModifier(RoundedCornerShape(Radius.l)))
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            SfSymbols.icon("magnifyingglass"),
            contentDescription = null,
            tint = IosColors.textSecondary,
            modifier = Modifier.size(19.2.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = rememberKeyboardDoneActions(),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.transactions_search_transactions_2), style = IosText.callout, color = IosColors.textSecondary)
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                SfSymbols.icon("xmark.circle.fill"),
                contentDescription = stringResource(R.string.common_clear_search),
                tint = IosColors.textSecondary,
                modifier = Modifier
                    .size(20.4.dp)
                    .clickable(role = Role.Button, onClick = onClear),
            )
        }
    }
}

@Composable
private fun BulkDeleteButton(selectedCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.m)
    Row(
        modifier = modifier
            .dropShadow(shape, Shadow(radius = 16.dp, color = IosColors.expense, offset = DpOffset(0.dp, 4.dp), alpha = 0.4f))
            .clip(shape)
            .background(IosColors.expense)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("transaction-bulk-delete"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(SfSymbols.icon("trash"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
        Text(stringResource(R.string.transactions_delete, selectedCount), style = IosText.body.w(FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun TransactionSectionHeader(
    title: String,
    icon: String,
    color: Color,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val material = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    Row(
        modifier
            .fillMaxWidth()
            .background(material)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
            .padding(horizontal = Spacing.screen, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            SfSymbols.icon(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.4.dp),
        )
        Text(title, style = IosText.headline, color = color)
        Spacer(Modifier.weight(1f))
        Text(
            Strings.plural(R.plurals.count_transaction, count),
            style = IosText.caption,
            color = IosColors.textTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = Spacing.s, vertical = 4.dp),
        )
    }
}

@Composable
private fun TransactionDaysCard(
    days: List<Pair<LocalDate, List<TransactionRowUi>>>,
    currency: String,
    locale: java.util.Locale,
    upcoming: Boolean,
    actions: RowActions,
    isEditMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    GroupedCard(modifier = Modifier.padding(bottom = Spacing.m)) {
        days.forEachIndexed { groupIndex, (day, rows) ->
            androidx.compose.runtime.key("day-$day") {
            DayHeader(day = day, count = rows.size, locale = locale)
            rows.forEachIndexed { rowIndex, row ->
                TransactionRow(
                    row = row,
                    currency = currency,
                    locale = locale,
                    actions = actions,
                    isEditMode = isEditMode,
                    isSelected = row.id in selectedIds,
                    onToggleSelection = onToggleSelection,
                )
                if (rowIndex < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = IosColors.separator,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            if (groupIndex < days.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.s),
                    color = if (upcoming) IosColors.spendOrange.copy(alpha = 0.3f) else IosColors.separator,
                )
            }
            }
        }
    }
}

@Composable
private fun SyncBanner(state: TransactionListUiState, onOpenNotSynced: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, message, tint) = when {
        state.failedCount > 0 -> Triple(SfSymbols.icon("exclamationmark.circle"), Strings.plural(R.plurals.transactions_n_not_synced_tap_to_review, state.failedCount), IosColors.expense)
        !state.isOnline && state.waitingCount > 0 -> Triple(SfSymbols.icon("icloud.slash"), Strings.plural(R.plurals.transactions_offline_n_waiting_to_sync, state.waitingCount), IosColors.spendOrange)
        !state.isOnline -> Triple(SfSymbols.icon("icloud.slash"), stringResource(R.string.transactions_offline_showing_saved), IosColors.spendOrange)
        state.waitingCount > 0 -> Triple(SfSymbols.icon("clock"), Strings.plural(R.plurals.transactions_n_waiting_to_sync, state.waitingCount), IosColors.spendOrange)
        else -> Triple(SfSymbols.icon("icloud.slash"), state.loadError.orEmpty(), IosColors.expense)
    }
    GlassCard(
        modifier = modifier
            .clickable(
                enabled = state.waitingCount + state.failedCount > 0,
                role = Role.Button,
                onClick = onOpenNotSynced,
            )
            .testTag("transactions.syncBanner"),
        shape = RoundedCornerShape(Radius.l),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(Spacing.s))
            Text(message, style = IosText.callout, color = IosColors.textPrimary)
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, count: Int, locale: java.util.Locale) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> stringResource(R.string.common_today)
        today.minusDays(1) -> stringResource(R.string.common_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val countBackground = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Row(
        Modifier.fillMaxWidth().background(background).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = IosText.headline, color = IosColors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            count.toString(),
            style = IosText.caption.w(FontWeight.SemiBold),
            color = IosColors.textTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .background(countBackground)
                .padding(horizontal = Spacing.s, vertical = 4.dp),
        )
    }
}

@Composable
private fun TransactionRow(
    row: TransactionRowUi,
    currency: String,
    locale: java.util.Locale,
    actions: RowActions,
    isEditMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val tint = categoryColor(row.categoryColor)
    val hasCategory = row.categoryName != "Uncategorized" || row.categoryColor != null
    val displayTitle = if (row.title == "Transaction" && !hasCategory) stringResource(R.string.transactions_untitled) else row.title
    val dateLabel = when (row.date) {
        LocalDate.now() -> stringResource(R.string.common_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.common_yesterday)
        else -> row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    val subtitle = buildList {
        if (hasCategory) add(row.categoryName)
        add(dateLabel)
        when (row.syncStatus) {
            SyncStatus.PENDING -> add(stringResource(R.string.transactions_waiting_to_sync))
            SyncStatus.FAILED -> add(stringResource(R.string.transactions_not_synced))
            SyncStatus.SYNCED -> Unit
        }
    }.joinToString(" • ")
    val signedAmount = if (row.isIncome) row.amount else -row.amount
    val amount = Money.format(signedAmount, currency, locale)

    // iOS: tap edits; the context menu (long press) has Edit, Duplicate and Delete.
    Box {
        if (!isEditMode) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_edit)) },
                    leadingIcon = { Icon(SfSymbols.icon("pencil"), contentDescription = null) },
                    onClick = { showMenu = false; actions.onEdit(row) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transactions_duplicate)) },
                    leadingIcon = { Icon(SfSymbols.icon("doc.on.doc"), contentDescription = null) },
                    onClick = { showMenu = false; actions.onDuplicate(row) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = IosColors.expense) },
                    leadingIcon = { Icon(SfSymbols.icon("trash"), contentDescription = null, tint = IosColors.expense) },
                    onClick = { showMenu = false; actions.onDelete(row) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (isEditMode) stringResource(R.string.transactions_select_transaction) else stringResource(R.string.common_edit),
                    onLongClickLabel = if (isEditMode) null else stringResource(R.string.common_more_actions),
                    onClick = { if (isEditMode) onToggleSelection(row.id) else actions.onEdit(row) },
                    onLongClick = if (isEditMode) null else ({ showMenu = true }),
                )
                .semantics {
                    if (isEditMode) {
                        selected = isSelected
                        role = Role.Checkbox
                    }
                }
                .padding(horizontal = Spacing.m, vertical = Spacing.m)
                .testTag("transaction.row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (isEditMode) {
                Box(
                    Modifier.size(24.dp).testTag("transaction-select.${row.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SfSymbols.icon(if (isSelected) "checkmark.circle.fill" else "circle"),
                        contentDescription = null,
                        tint = IosColors.spendOrange,
                        modifier = Modifier.size(20.4.dp),
                    )
                }
            } else {
                Box(Modifier.size(12.dp).background(tint, CircleShape))
            }
            RowCategoryBadge(icon = row.categoryIcon, name = row.categoryName, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    displayTitle,
                    style = IosText.callout.w(FontWeight.SemiBold),
                    color = IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = IosText.subheadline,
                    color = if (row.syncStatus == SyncStatus.FAILED) IosColors.expense else IosColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                amount,
                style = IosText.rounded(17, FontWeight.Medium),
                color = if (signedAmount >= 0) IosColors.income else IosColors.expense,
                maxLines = 1,
            )
        }
    }
}

private class RowActions(
    val onEdit: (TransactionRowUi) -> Unit,
    val onDuplicate: (TransactionRowUi) -> Unit,
    val onDelete: (TransactionRowUi) -> Unit,
)

@Composable
private fun RowCategoryBadge(icon: String?, name: String, tint: Color) {
    val shape = RoundedCornerShape(Radius.s)
    Box(
        Modifier.size(44.dp).clip(shape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(
            icon,
            name,
            tint = IosColors.textPrimary,
            modifier = Modifier.size(19.2.dp),
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(
            SfSymbols.icon("tray"),
            contentDescription = null,
            tint = IosColors.textTertiary,
            modifier = Modifier.size(56.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.transactions_no_transactions_yet), style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(
                stringResource(R.string.transactions_start_tracking_your_spending_by),
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.m))
                .background(IosColors.spendOrange)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                SfSymbols.icon("plus.circle.fill"),
                contentDescription = null,
                tint = IosColors.textPrimary,
                modifier = Modifier.size(20.4.dp),
            )
            Text(
                stringResource(R.string.transactions_add_your_first_transaction),
                style = IosText.headline,
                color = IosColors.textPrimary,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    CircularProgressIndicator(
        color = IosColors.spendOrange,
        modifier = Modifier.scale(1.2f),
    )
}
