package com.spenditos.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.ui.components.CategoryIcons
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.categoryColor
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionListScreen(
    onOpenNotSynced: () -> Unit,
    onOpenExport: () -> Unit = {},
    onAdd: () -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val visibleDays = remember(state.days, searchText) {
        state.days.mapNotNull { (day, rows) ->
            val matchingRows = if (searchText.isBlank()) rows else rows.filter { row ->
                row.title.contains(searchText, ignoreCase = true) ||
                    row.categoryName.contains(searchText, ignoreCase = true)
            }
            matchingRows.takeIf { it.isNotEmpty() }?.let { day to it }
        }
    }
    val today = LocalDate.now()
    val upcomingDays = visibleDays.filter { (day, _) -> day.isAfter(today) }.sortedBy { it.first }
    val historyDays = visibleDays.filter { (day, _) -> !day.isAfter(today) }
    val upcomingCount = upcomingDays.sumOf { (_, rows) -> rows.size }
    val historyCount = historyDays.sumOf { (_, rows) -> rows.size }

    IosScreen {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(includeCategories = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize().testTag("transactions.list"),
            ) {
                item(key = "title") {
                    LargeTitle("Transactions", trailing = {
                        TransactionsToolbarCapsule(
                            isSearchActive = isSearchActive,
                            onToggleSearch = { isSearchActive = !isSearchActive },
                            onOpenExport = onOpenExport,
                        )
                    })
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

                item(key = "month") {
                    MonthNavigator(
                        monthLabel = monthLabel,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        modifier = Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.s),
                    )
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
                                title = "Upcoming",
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
                            )
                        }
                    }

                    if (historyDays.isNotEmpty()) {
                        stickyHeader(key = "history") {
                            TransactionSectionHeader(
                                title = "History",
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
                            )
                        }
                    }
                } else if (state.isEmpty && state.isRefreshing && searchText.isBlank()) {
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
        }
    }
}

@Composable
private fun TransactionsToolbarCapsule(
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenExport: () -> Unit,
) {
    val capsule = CircleShape
    Row(
        modifier = Modifier
            .clip(capsule)
            .then(subtleGlassModifier(capsule))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier
                .clip(capsule)
                .clickable(role = Role.Button, onClick = onOpenExport)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                SfSymbols.icon("square.and.arrow.up"),
                contentDescription = null,
                tint = IosColors.spendOrange,
                modifier = Modifier.size(19.2.dp),
            )
            Text("Export", style = IosText.callout, color = IosColors.spendOrange)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onToggleSearch)
                .testTag("transaction-search"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SfSymbols.icon(if (isSearchActive) "xmark.circle.fill" else "magnifyingglass"),
                contentDescription = if (isSearchActive) "Close search" else "Search transactions",
                tint = IosColors.textPrimary,
                modifier = Modifier.size(20.4.dp),
            )
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
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search transactions...", style = IosText.callout, color = IosColors.textSecondary)
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                SfSymbols.icon("xmark.circle.fill"),
                contentDescription = "Clear search",
                tint = IosColors.textSecondary,
                modifier = Modifier
                    .size(20.4.dp)
                    .clickable(role = Role.Button, onClick = onClear),
            )
        }
    }
}

@Composable
private fun MonthNavigator(
    monthLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MonthNavigationButton("chevron.left", "Previous month", onPrevious)
            Text(
                monthLabel,
                modifier = Modifier.weight(1f).testTag("transactions.month"),
                style = IosText.system(16, FontWeight.SemiBold),
                color = IosColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            MonthNavigationButton("chevron.right", "Next month", onNext)
        }
    }
}

@Composable
private fun MonthNavigationButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.05f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SfSymbols.icon(symbol),
            contentDescription = description,
            tint = IosColors.textSecondary,
            modifier = Modifier.size(16.8.dp),
        )
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
            "$count ${if (count == 1) "transaction" else "transactions"}",
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
) {
    GroupedCard(modifier = Modifier.padding(bottom = Spacing.m)) {
        days.forEachIndexed { groupIndex, (day, rows) ->
            androidx.compose.runtime.key("day-$day") {
            DayHeader(day = day, count = rows.size, locale = locale)
            rows.forEachIndexed { rowIndex, row ->
                TransactionRow(row = row, currency = currency, locale = locale)
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
        state.failedCount > 0 -> Triple(Icons.Outlined.ErrorOutline, "${state.failedCount} not synced. Tap to review.", IosColors.expense)
        !state.isOnline && state.waitingCount > 0 -> Triple(Icons.Outlined.CloudOff, "Offline. ${state.waitingCount} waiting to sync.", IosColors.spendOrange)
        !state.isOnline -> Triple(Icons.Outlined.CloudOff, "Offline. Showing what's saved on this device.", IosColors.spendOrange)
        state.waitingCount > 0 -> Triple(Icons.Outlined.Schedule, "${state.waitingCount} waiting to sync.", IosColors.spendOrange)
        else -> Triple(Icons.Outlined.CloudOff, state.loadError.orEmpty(), IosColors.expense)
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
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
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
private fun TransactionRow(row: TransactionRowUi, currency: String, locale: java.util.Locale) {
    val tint = categoryColor(row.categoryColor)
    val hasCategory = row.categoryName != "Uncategorized" || row.categoryColor != null
    val displayTitle = if (row.title == "Transaction" && !hasCategory) "Untitled" else row.title
    val dateLabel = when (row.date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    }
    val subtitle = buildList {
        if (hasCategory) add(row.categoryName)
        add(dateLabel)
        when (row.syncStatus) {
            SyncStatus.PENDING -> add("Waiting to sync")
            SyncStatus.FAILED -> add("Not synced")
            SyncStatus.SYNCED -> Unit
        }
    }.joinToString(" • ")
    val signedAmount = if (row.isIncome) row.amount else -row.amount
    val amount = Money.format(signedAmount, currency, locale)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m).testTag("transaction.row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(12.dp).background(tint, CircleShape))
        RowCategoryBadge(name = row.categoryName, tint = tint)
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

@Composable
private fun RowCategoryBadge(name: String, tint: Color) {
    val shape = RoundedCornerShape(Radius.s)
    Box(
        Modifier.size(44.dp).clip(shape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.symbol(null, name),
            contentDescription = null,
            tint = IosColors.textPrimary,
            modifier = Modifier.size(19.8.dp),
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
            Icons.Outlined.Inbox,
            contentDescription = null,
            tint = IosColors.textTertiary,
            modifier = Modifier.size(56.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("No transactions yet", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary)
            Text(
                "Start tracking your spending by adding your first transaction.",
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
                Icons.Rounded.AddCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.4.dp),
            )
            Text(
                "Add your first transaction",
                style = IosText.headline,
                color = Color.White,
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
