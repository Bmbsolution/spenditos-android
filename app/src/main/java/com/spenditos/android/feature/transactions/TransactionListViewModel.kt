package com.spenditos.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.ui.components.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

data class TransactionRowUi(
    val id: String,
    val title: String,
    val categoryId: String = "",
    val categoryName: String,
    val categoryColor: String?,
    val categoryIcon: String? = null,
    val amount: Double,
    val isIncome: Boolean,
    val date: LocalDate,
    val syncStatus: SyncStatus,
    /** The row as the edit form opens it. */
    val edit: EditTarget? = null,
)

data class TransactionFilterState(
    val dateRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
    val customStart: LocalDate = LocalDate.now().minusMonths(1),
    val customEnd: LocalDate = LocalDate.now(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedType: EntryType? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val presets: List<FilterPreset> = emptyList(),
    /** Bumped when a preset is loaded or filters reset, so the sheet can resync amount fields. */
    val presetEpoch: Int = 0,
)

data class TransactionListUiState(
    val month: YearMonth = YearMonth.now(),
    val currency: String = "USD",
    val days: List<Pair<LocalDate, List<TransactionRowUi>>> = emptyList(),
    val moneyIn: BigDecimal = BigDecimal.ZERO,
    val moneyOut: BigDecimal = BigDecimal.ZERO,
    val waitingCount: Int = 0,
    val failedCount: Int = 0,
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    /** A list delete that failed; shown until dismissed. */
    val actionError: String? = null,
    val filters: TransactionFilterState = TransactionFilterState(),
    val categories: List<Category> = emptyList(),
    /** True when the loaded range itself has no rows (before search and client filters). */
    val sourceEmpty: Boolean = true,
) {
    val isEmpty: Boolean get() = days.isEmpty()
    val hasActiveFilters: Boolean get() = hasActiveTransactionFilters(filters)
    val dateRange: DateRangeFilter get() = filters.dateRange
    val customStart: LocalDate get() = filters.customStart
    val customEnd: LocalDate get() = filters.customEnd
}

private data class RefreshState(val isRefreshing: Boolean = false, val error: String? = null, val actionError: String? = null)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    settings: UserSettingsRepository,
    connectivity: ConnectivityStatus,
    invalidation: FinanceInvalidationBus,
    private val presetStore: FilterPresetStore,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val refresh = MutableStateFlow(RefreshState())
    private val criteria = MutableStateFlow(TransactionFilterState())
    private val searchQuery = MutableStateFlow("")

    private val range = combine(month, criteria) { m, filters ->
        if (filters.dateRange == DateRangeFilter.CUSTOM) filters.customStart to filters.customEnd
        else m.atDay(1) to m.atEndOfMonth()
    }.distinctUntilChanged()

    private val rows = range.flatMapLatest { (start, end) -> transactions.observeRange(start, end) }

    val state: StateFlow<TransactionListUiState> = combine(
        combine(month, rows, categories.observe(), settings.currency()) { m, t, c, cur -> Quad(m, t, c, cur) },
        combine(criteria, searchQuery) { filters, query -> filters to query },
        transactions.observeQueue(),
        connectivity.isOnline,
        refresh,
    ) { (m, txns, cats, currency), (filters, query), queue, online, refreshState ->
        val byId = cats.associateBy(Category::id)
        val ui = txns.map { it.toRow(byId[it.categoryId]) }
        val filtered = filterTransactionRows(
            rows = ui,
            query = query,
            categoryIds = filters.selectedCategoryIds,
            type = filters.selectedType,
            minAmount = filters.minAmount,
            maxAmount = filters.maxAmount,
        )
        TransactionListUiState(
            month = m,
            currency = currency,
            days = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it }).toList(),
            moneyIn = filtered.filter { it.isIncome }.sumOf { BigDecimal.valueOf(it.amount) },
            moneyOut = filtered.filterNot { it.isIncome }.sumOf { BigDecimal.valueOf(it.amount) },
            waitingCount = queue.count { it.status != MutationStatus.FAILED },
            failedCount = queue.count { it.status == MutationStatus.FAILED },
            isOnline = online,
            isRefreshing = refreshState.isRefreshing,
            loadError = refreshState.error,
            actionError = refreshState.actionError,
            filters = filters,
            categories = cats,
            sourceEmpty = ui.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListUiState())

    init {
        val stored = presetStore.load()
        if (stored.isNotEmpty()) criteria.update { it.copy(presets = stored) }
        refresh(includeCategories = true)
        range.drop(1).onEach { refresh() }.launchIn(viewModelScope)
        invalidation.events
            .filter { Aggregate.TRANSACTION in it || Aggregate.CATEGORY in it }
            .debounce(250)
            .onEach { refresh(includeCategories = Aggregate.CATEGORY in it) }
            .launchIn(viewModelScope)
        connectivity.isOnline.filter { it }.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun previousMonth() = changeMonth(month.value.minusMonths(1))

    fun nextMonth() = changeMonth(month.value.plusMonths(1))

    /** Month arrows keep browsing months. Leaving a This/Last Month preset clears that date filter. */
    private fun changeMonth(target: YearMonth) {
        if (criteria.value.dateRange == DateRangeFilter.CUSTOM) return
        month.value = target
        if (criteria.value.dateRange != DateRangeFilter.THIS_MONTH) {
            criteria.update { it.copy(dateRange = DateRangeFilter.THIS_MONTH) }
        }
    }

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    fun selectDateRange(range: DateRangeFilter) {
        criteria.update { it.copy(dateRange = range) }
        when (range) {
            DateRangeFilter.THIS_MONTH -> month.value = YearMonth.now()
            DateRangeFilter.LAST_MONTH -> month.value = YearMonth.now().minusMonths(1)
            DateRangeFilter.CUSTOM -> Unit
        }
    }

    /** A start after the end moves the end with it (and the reverse), so the range is never empty by accident. */
    fun setCustomStart(date: LocalDate) = criteria.update { it.copy(customStart = date, customEnd = maxOf(it.customEnd, date)) }

    fun setCustomEnd(date: LocalDate) = criteria.update { it.copy(customEnd = date, customStart = minOf(it.customStart, date)) }

    fun applyQuickToday() {
        val today = LocalDate.now()
        criteria.update { it.copy(dateRange = DateRangeFilter.CUSTOM, customStart = today, customEnd = today) }
    }

    fun applyQuickThisWeek(locale: Locale) {
        val today = LocalDate.now()
        val start = today.with(java.time.temporal.WeekFields.of(locale).dayOfWeek(), 1)
        criteria.update { it.copy(dateRange = DateRangeFilter.CUSTOM, customStart = start, customEnd = today) }
    }

    fun applyQuickThisMonth() {
        val today = LocalDate.now()
        criteria.update {
            it.copy(
                dateRange = DateRangeFilter.THIS_MONTH,
                customStart = YearMonth.from(today).atDay(1),
                customEnd = today,
            )
        }
        month.value = YearMonth.from(today)
    }

    fun applyQuickLastDays(days: Long) {
        val end = LocalDate.now()
        criteria.update {
            it.copy(dateRange = DateRangeFilter.CUSTOM, customStart = end.minusDays(days), customEnd = end)
        }
    }

    fun setType(type: EntryType?) = criteria.update { it.copy(selectedType = type) }

    fun toggleCategory(id: String) = criteria.update { current ->
        val next = current.selectedCategoryIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        current.copy(selectedCategoryIds = next)
    }

    fun clearCategories() = criteria.update { it.copy(selectedCategoryIds = emptySet()) }

    fun setMinAmount(amount: Double?) = criteria.update { it.copy(minAmount = amount) }

    fun setMaxAmount(amount: Double?) = criteria.update { it.copy(maxAmount = amount) }

    fun savePreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = criteria.value
        val preset = FilterPreset(
            name = trimmed,
            dateRange = current.dateRange,
            customStartDate = current.customStart.toString(),
            customEndDate = current.customEnd.toString(),
            selectedCategories = current.selectedCategoryIds.toList(),
            selectedType = current.selectedType?.name,
            minAmount = current.minAmount,
            maxAmount = current.maxAmount,
        )
        val updated = listOf(preset) + current.presets
        criteria.update { it.copy(presets = updated) }
        presetStore.save(updated)
    }

    fun deletePreset(index: Int) {
        val current = criteria.value.presets
        if (index !in current.indices) return
        val updated = current.filterIndexed { i, _ -> i != index }
        criteria.update { it.copy(presets = updated) }
        presetStore.save(updated)
    }

    fun loadPreset(preset: FilterPreset) {
        val start = preset.startDate() ?: LocalDate.now().minusMonths(1)
        val end = preset.endDate() ?: LocalDate.now()
        criteria.update {
            it.copy(
                dateRange = preset.dateRange,
                customStart = start,
                customEnd = end,
                selectedCategoryIds = preset.selectedCategories.toSet(),
                selectedType = preset.entryType(),
                minAmount = preset.minAmount,
                maxAmount = preset.maxAmount,
                presetEpoch = it.presetEpoch + 1,
            )
        }
        when (preset.dateRange) {
            DateRangeFilter.THIS_MONTH -> month.value = YearMonth.now()
            DateRangeFilter.LAST_MONTH -> month.value = YearMonth.now().minusMonths(1)
            DateRangeFilter.CUSTOM -> Unit
        }
    }

    fun removeDateFilter() {
        criteria.update { it.copy(dateRange = DateRangeFilter.THIS_MONTH) }
        month.value = YearMonth.now()
    }

    fun removeTypeFilter() = criteria.update { it.copy(selectedType = null) }

    fun removeCategoryFilter() = clearCategories()

    fun removeAmountFilter() = criteria.update { it.copy(minAmount = null, maxAmount = null, presetEpoch = it.presetEpoch + 1) }

    fun resetFilters() {
        month.value = YearMonth.now()
        criteria.update {
            it.copy(
                dateRange = DateRangeFilter.THIS_MONTH,
                customStart = LocalDate.now().minusMonths(1),
                customEnd = LocalDate.now(),
                selectedCategoryIds = emptySet(),
                selectedType = null,
                minAmount = null,
                maxAmount = null,
                presetEpoch = it.presetEpoch + 1,
            )
        }
    }

    /** The long-press Delete (iOS row context menu), after the user confirmed. */
    fun delete(id: String) {
        viewModelScope.launch {
            try {
                transactions.delete(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is com.spenditos.android.data.transactions.TransactionBusyException -> e.message.orEmpty()
                    is ApiError.Network -> "You're offline. Check your connection and try again."
                    is ApiError.Unauthorized -> "Sign in again to delete."
                    else -> "Couldn't delete. Please try again."
                }
                refresh.update { it.copy(actionError = message) }
            }
        }
    }

    fun dismissActionError() = refresh.update { it.copy(actionError = null) }

    fun refresh(includeCategories: Boolean = false) {
        val (start, end) = currentRange()
        viewModelScope.launch {
            refresh.update { it.copy(isRefreshing = true) }
            val error = runCatching {
                if (includeCategories) categories.refresh()
                transactions.refreshRange(start, end)
            }.exceptionOrNull()
            refresh.update { it.copy(isRefreshing = false, error = error?.let(::messageFor)) }
        }
    }

    private fun currentRange(): Pair<LocalDate, LocalDate> {
        val filters = criteria.value
        return if (filters.dateRange == DateRangeFilter.CUSTOM) {
            filters.customStart to filters.customEnd
        } else {
            val m = month.value
            m.atDay(1) to m.atEndOfMonth()
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is ApiError.Network -> "You're offline. Showing what's saved on this device."
        else -> "Couldn't refresh transactions. Pull down to try again."
    }

    private fun TransactionEntity.toRow(category: Category?) = TransactionRowUi(
        id = id,
        title = description.ifBlank { category?.name ?: "Transaction" },
        categoryId = categoryId,
        categoryName = category?.name ?: "Uncategorized",
        categoryColor = category?.color,
        categoryIcon = category?.icon,
        amount = amount,
        isIncome = category?.isIncome == true,
        date = LocalDate.parse(date),
        syncStatus = syncStatus,
        edit = toEditTarget(),
    )
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

internal fun hasActiveTransactionFilters(filters: TransactionFilterState): Boolean =
    filters.dateRange != DateRangeFilter.THIS_MONTH ||
        filters.selectedCategoryIds.isNotEmpty() ||
        filters.selectedType != null ||
        filters.minAmount != null ||
        filters.maxAmount != null

/** Client-side search, category, type (income = category.isIncome) and amount filters. */
internal fun filterTransactionRows(
    rows: List<TransactionRowUi>,
    query: String,
    categoryIds: Set<String>,
    type: EntryType?,
    minAmount: Double?,
    maxAmount: Double?,
): List<TransactionRowUi> = rows.filter { row ->
    val matchesSearch = query.isBlank() ||
        row.title.contains(query, ignoreCase = true) ||
        row.categoryName.contains(query, ignoreCase = true)
    val matchesCategory = categoryIds.isEmpty() || row.categoryId in categoryIds
    val matchesType = when (type) {
        null -> true
        EntryType.INCOME -> row.isIncome
        EntryType.EXPENSE -> !row.isIncome
    }
    val matchesMin = minAmount == null || row.amount >= minAmount
    val matchesMax = maxAmount == null || row.amount <= maxAmount
    matchesSearch && matchesCategory && matchesType && matchesMin && matchesMax
}

/** iOS `activePillItems` labels, in the same order. */
internal fun activeFilterPillLabels(
    filters: TransactionFilterState,
    categoryNames: Map<String, String>,
    currency: String,
    locale: Locale,
): List<String> {
    val pills = mutableListOf<String>()
    if (filters.dateRange != DateRangeFilter.THIS_MONTH) {
        val label = when (filters.dateRange) {
            DateRangeFilter.LAST_MONTH -> "Date: Last Month"
            DateRangeFilter.CUSTOM -> {
                val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
                "Date: ${filters.customStart.format(fmt)} - ${filters.customEnd.format(fmt)}"
            }
            DateRangeFilter.THIS_MONTH -> "Date: ${filters.dateRange.label}"
        }
        pills += label
    }
    filters.selectedType?.let { type ->
        val name = when (type) {
            EntryType.INCOME -> "Income"
            EntryType.EXPENSE -> "Expense"
        }
        pills += "Type: $name"
    }
    if (filters.selectedCategoryIds.isNotEmpty()) {
        val count = filters.selectedCategoryIds.size
        pills += if (count == 1) {
            val name = categoryNames[filters.selectedCategoryIds.first()] ?: ""
            "Category: $name"
        } else {
            "Categories: $count"
        }
    }
    if (filters.minAmount != null || filters.maxAmount != null) {
        val minStr = filters.minAmount?.let { "Min: ${Money.format(it, currency, locale)}" }.orEmpty()
        val maxStr = filters.maxAmount?.let { "Max: ${Money.format(it, currency, locale)}" }.orEmpty()
        pills += listOf(minStr, maxStr).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    return pills
}

internal fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered
    return filtered.substring(0, dot + 1) + filtered.substring(dot + 1).filter { it != '.' }
}
