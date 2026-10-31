package com.spenditos.android.feature.plan

import com.spenditos.android.core.i18n.LocalizedDate
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MonthlyPlanDraftEntry
import com.spenditos.android.data.model.MonthlyPlanLogOverride
import com.spenditos.android.data.model.MonthlyPlanPendingEdit
import com.spenditos.android.data.model.MonthlyPlanPendingLog
import com.spenditos.android.data.model.MonthlyPlanPendingSkip
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.planning.MonthPlanApplier
import com.spenditos.android.data.planning.MonthPlanApplyResult
import com.spenditos.android.data.planning.MonthPlanRepository
import com.spenditos.android.data.planning.MonthPlanSnapshot
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class MonthPlanRecurringRow(
    val templateId: String,
    val description: String,
    val categoryId: String,
    val isIncome: Boolean,
    val dates: List<LocalDate>,
    val amounts: List<BigDecimal>,
    val usualAmount: BigDecimal,
    val amountVaries: Boolean,
    val isSkipped: Boolean,
    val hasDraftChange: Boolean,
    val isLogged: Boolean,
    val hasAmountChange: Boolean,
    /** Set when this payment belongs to a debt (shown with a "Debt" tag). */
    val debtId: String? = null,
) {
    /** The month's amount: every occurrence's own amount (a debt's last payment is smaller than the rest). */
    val total: BigDecimal get() = amounts.fold(BigDecimal.ZERO, BigDecimal::add).moneyScale()
    val occurrenceCount: Int get() = dates.size
    val isDebtPayment: Boolean get() = debtId != null

    /** True when every occurrence has the same amount (the usual "3 × $50"). */
    val hasUniformAmounts: Boolean get() = amounts.map { it.setScale(2, java.math.RoundingMode.HALF_UP) }.toSet().size <= 1
}

data class MonthPlanSummary(
    val recordsIncome: BigDecimal,
    val recordsBills: BigDecimal,
    val recordsLeft: BigDecimal,
    val draftIncome: BigDecimal,
    val draftBills: BigDecimal,
    val draftOneTime: BigDecimal,
    val draftLeft: BigDecimal,
) {
    val delta: BigDecimal get() = draftLeft.subtract(recordsLeft).moneyScale()
}

data class MonthPlanTransactionRow(val transaction: Transaction, val isIncome: Boolean, val isDeleted: Boolean)

data class MonthPlanUiState(
    val month: YearMonth = YearMonth.now().plusMonths(1),
    val snapshot: MonthPlanSnapshot? = null,
    val draft: MonthPlanDraftState = MonthPlanDraftState(),
    val currency: String = "USD",
    val isLoading: Boolean = false,
    val isSavingDraft: Boolean = false,
    val isApplying: Boolean = false,
    /**
     * A previous Apply did not finish. One-time entries are frozen until it succeeds or the draft is
     * discarded: the server derives each entry's id from its details, so changing or removing one
     * that may already be saved could leave a second copy.
     */
    val applyIncomplete: Boolean = false,
    val isOnline: Boolean = true,
    val error: String? = null,
    val draftMessage: String? = null,
    val applyMessage: String? = null,
    /** iOS "More tools" > Plan checkup; null until loaded (best effort, never blocks the plan). */
    val checkup: com.spenditos.android.data.model.MonthlyPlanCheckupData? = null,
    val dismissedSuggestions: Set<String> = emptySet(),
) {
    val visibleSuggestions: List<com.spenditos.android.data.model.MonthlyPlanCheckupSuggestion>
        get() = checkup?.takeIf { it.month == month.toString() }?.suggestions.orEmpty().filterNot { it.id in dismissedSuggestions }
    val isPast: Boolean get() = month < YearMonth.now()
    val canGoToPreviousMonth: Boolean get() = month > YearMonth.now()
    val canGoToNextMonth: Boolean get() = month < YearMonth.now().plusMonths(3)
    val draftChangeCount: Int get() = draft.changeCount
}

@HiltViewModel
class MonthPlanViewModel @Inject constructor(
    private val repository: MonthPlanRepository,
    private val applier: MonthPlanApplier,
    connectivity: ConnectivityStatus,
    settings: UserSettingsActions,
    invalidations: FinanceInvalidationBus,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MonthPlanUiState())
    val state: StateFlow<MonthPlanUiState> = mutableState.asStateFlow()

    private var savedDraft: com.spenditos.android.data.model.MonthlyPlanDraftData? = null
    private var hydratedMonth: YearMonth? = null
    private var autosaveEnabled = false
    private var debounceJob: Job? = null
    private var autosaveInFlight: Job? = null
    private val autosaveMutex = Mutex()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> mutableState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            settings.currency().collect { currency -> mutableState.update { it.copy(currency = currency) } }
        }
        observeInvalidations(invalidations)
    }

    @OptIn(FlowPreview::class)
    private fun observeInvalidations(invalidations: FinanceInvalidationBus) {
        viewModelScope.launch {
            invalidations.events
                .filter { events -> Aggregate.TRANSACTION in events || Aggregate.CATEGORY in events || Aggregate.RECURRING in events }
                .debounce(250)
                .collect { load() }
        }
    }

    suspend fun load() {
        val requestedMonth = mutableState.value.month
        mutableState.update { it.copy(isLoading = true, error = null) }
        try {
            val loaded = repository.load(requestedMonth)
            if (mutableState.value.month != requestedMonth) return
            mutableState.update { current ->
                if (hydratedMonth == requestedMonth) {
                    current.copy(snapshot = loaded, isLoading = false, error = null)
                } else {
                    savedDraft = loaded.draft
                    hydratedMonth = requestedMonth
                    autosaveEnabled = true
                    current.copy(
                        snapshot = loaded,
                        draft = loaded.draft.toDraftState(),
                        isLoading = false,
                        error = null,
                        draftMessage = null,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (mutableState.value.month == requestedMonth) {
                mutableState.update {
                    it.copy(isLoading = false, error = loadError(error, it.isOnline))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Plan checkup for the shown month, sending the current drafts so they aren't suggested again. */
    fun loadCheckup() {
        val month = mutableState.value.month
        val drafts = mutableState.value.draft.entries.map {
            com.spenditos.android.data.model.MonthlyPlanCheckupDraftItem(it.categoryId, it.description)
        }
        viewModelScope.launch {
            val result = try {
                repository.checkup(month, drafts)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (result != null && mutableState.value.month == month) mutableState.update { it.copy(checkup = result) }
        }
    }

    /** iOS "Hide": this session only, like iOS. */
    fun dismissSuggestion(id: String) = mutableState.update { it.copy(dismissedSuggestions = it.dismissedSuggestions + id) }

    fun applyAllFromScreen() {
        viewModelScope.launch { applyAll() }
    }

    fun discardFromScreen() {
        viewModelScope.launch { discardAllChanges() }
    }

    fun moveMonth(offset: Int) {
        val current = mutableState.value
        if (current.isApplying) return
        val target = current.month.plusMonths(offset.toLong())
        if ((offset < 0 && !current.canGoToPreviousMonth) || (offset > 0 && !current.canGoToNextMonth)) return
        viewModelScope.launch { selectMonth(target) }
    }

    suspend fun selectMonth(month: YearMonth) {
        if (mutableState.value.isApplying) return
        if (!flushAutosave()) return
        mutableState.update {
            it.copy(
                month = month,
                snapshot = null,
                draft = MonthPlanDraftState(),
                isLoading = false,
                error = null,
                draftMessage = null,
                applyMessage = null,
            )
        }
        savedDraft = null
        hydratedMonth = null
        autosaveEnabled = false
        load()
    }

    fun stageSkip(templateId: String, dates: List<LocalDate>, skipped: Boolean) {
        val current = mutableState.value
        if (current.isPast || dates.isEmpty()) return
        val month = current.month.toString()
        val skipRows = current.draft.pendingSkips.filterNot { it.templateId == templateId }
        val logs = current.draft.pendingLogs.filterNot { it.templateId == templateId && it.month == month }
        val next = current.draft.copy(
            pendingSkips = if (skipped) skipRows + MonthlyPlanPendingSkip(templateId, month, dates.map(LocalDate::toString)) else skipRows,
            pendingLogs = if (skipped) logs else current.draft.pendingLogs,
        )
        updateDraft(next)
    }

    fun stageLog(templateId: String, dates: List<LocalDate>, logged: Boolean = true) {
        val current = mutableState.value
        if (current.isPast || dates.isEmpty()) return
        val month = current.month.toString()
        val existing = current.draft.pendingLogs.firstOrNull { it.templateId == templateId && it.month == month }
        val nextLog = existing ?: MonthlyPlanPendingLog(templateId, month)
        updateDraft(
            current.draft.copy(
                pendingSkips = current.draft.pendingSkips.filterNot { it.templateId == templateId && it.month == month },
                pendingLogs = if (logged) {
                    current.draft.pendingLogs.filterNot { it.templateId == templateId && it.month == month } + nextLog
                } else {
                    current.draft.pendingLogs.filterNot { it.templateId == templateId && it.month == month }
                },
            ),
        )
    }

    fun stageAmountChange(templateId: String, dates: List<LocalDate>, amount: BigDecimal) {
        val current = mutableState.value
        if (current.isPast || dates.isEmpty() || dates.size > 10 || amount.signum() <= 0) return
        val month = current.month.toString()
        val rounded = amount.setScale(2, RoundingMode.HALF_UP).toDouble()
        val log = MonthlyPlanPendingLog(
            templateId = templateId,
            month = month,
            overrides = dates.map { MonthlyPlanLogOverride(it.toString(), amount = rounded) },
        )
        updateDraft(
            current.draft.copy(
                pendingSkips = current.draft.pendingSkips.filterNot { it.templateId == templateId && it.month == month },
                pendingLogs = current.draft.pendingLogs.filterNot { it.templateId == templateId && it.month == month } + log,
            ),
        )
    }

    fun resetAmountChange(templateId: String) {
        val current = mutableState.value
        if (current.isPast) return
        updateDraft(current.draft.copy(pendingLogs = current.draft.pendingLogs.filterNot {
            it.templateId == templateId && it.month == current.month.toString()
        }))
    }

    fun addOneTime(description: String, amount: BigDecimal, categoryId: String, date: LocalDate): Boolean {
        val current = mutableState.value
        if (current.isPast || current.isApplying) return false
        if (current.applyIncomplete) {
            mutableState.update { it.copy(draftMessage = Strings.get(R.string.plan_finish_or_discard_the_plan)) }
            return false
        }
        val cleanDescription = description.trim()
        val categories = current.snapshot?.categories.orEmpty()
        val month = current.month
        if (cleanDescription.isEmpty() || cleanDescription.length > 200 || amount.signum() <= 0 ||
            amount > BigDecimal("1000000000") || categoryId.isBlank() || categories.none { it.id == categoryId } ||
            YearMonth.from(date) != month || current.draft.entries.size >= 100) {
            mutableState.update { it.copy(draftMessage = Strings.get(R.string.plan_enter_a_description_amount_category)) }
            return false
        }
        val id = UUID.randomUUID().toString().lowercase()
        val entry = MonthlyPlanDraftEntry(
            id = id,
            idempotencyKeyId = "planning-$id",
            date = date.toString(),
            description = cleanDescription,
            amount = amount.setScale(2, RoundingMode.HALF_UP).toDouble(),
            categoryId = categoryId,
            recurring = JsonNull,
        )
        updateDraft(current.draft.copy(entries = current.draft.entries + entry))
        return true
    }

    fun removeOneTime(id: String) {
        val current = mutableState.value
        if (current.isPast) return
        if (current.applyIncomplete) {
            mutableState.update { it.copy(draftMessage = Strings.get(R.string.plan_finish_or_discard_the_plan_2)) }
            return
        }
        updateDraft(current.draft.copy(entries = current.draft.entries.filterNot { it.id == id }))
    }

    /** Testable hook for staged edits and deletes used by existing transaction rows. */
    fun stageTransactionEdit(edit: MonthlyPlanPendingEdit) {
        val current = mutableState.value
        if (current.isPast) return
        val edits = current.draft.pendingEdits.filterNot { it.transactionId == edit.transactionId } + edit
        updateDraft(current.draft.copy(pendingDeletes = current.draft.pendingDeletes.filterNot { it.transactionId == edit.transactionId }, pendingEdits = edits))
    }

    fun stageTransactionDelete(transactionId: String) {
        val current = mutableState.value
        if (current.isPast || transactionId.isBlank()) return
        updateDraft(current.draft.copy(
            pendingEdits = current.draft.pendingEdits.filterNot { it.transactionId == transactionId },
            pendingDeletes = current.draft.pendingDeletes.filterNot { it.transactionId == transactionId } + com.spenditos.android.data.model.MonthlyPlanPendingDelete(transactionId),
        ))
    }

    fun undoTransactionDelete(transactionId: String) {
        val current = mutableState.value
        if (current.isPast) return
        updateDraft(current.draft.copy(pendingDeletes = current.draft.pendingDeletes.filterNot { it.transactionId == transactionId }))
    }

    private fun updateDraft(draft: MonthPlanDraftState) {
        if (mutableState.value.isApplying) return
        mutableState.update { it.copy(draft = draft, draftMessage = null, applyMessage = null) }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        if (!autosaveEnabled) return
        val month = mutableState.value.month
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            val saveJob = viewModelScope.launch { persistDraft(month) }
            autosaveInFlight = saveJob
            saveJob.join()
            if (autosaveInFlight == saveJob) autosaveInFlight = null
        }
    }

    suspend fun flushAutosave(): Boolean {
        debounceJob?.cancel()
        debounceJob = null
        autosaveInFlight?.join()
        return persistDraft(mutableState.value.month)
    }

    private suspend fun persistDraft(month: YearMonth): Boolean = autosaveMutex.withLock {
        if (mutableState.value.month != month) return@withLock false
        val draft = mutableState.value.draft
        val validation = draft.validationError(month)
        if (validation != null) {
            mutableState.update { it.copy(draftMessage = validation) }
            return@withLock false
        }
        mutableState.update { it.copy(isSavingDraft = true, draftMessage = null) }
        try {
            if (draft.isEmpty) {
                if (savedDraft != null) repository.deleteDraft(month)
                savedDraft = null
            } else if (savedDraft?.toDraftState() != draft) {
                savedDraft = repository.saveDraft(draft.toSaveRequest(month))
            }
            mutableState.update { it.copy(isSavingDraft = false, draftMessage = null) }
            true
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(isSavingDraft = false) }
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(isSavingDraft = false, draftMessage = Strings.get(R.string.plan_the_draft_could_not_be)) }
            false
        }
    }

    suspend fun applyAll() {
        val current = mutableState.value
        if (current.draftChangeCount == 0 || current.isApplying || current.isPast) return
        if (!current.isOnline) {
            mutableState.update { it.copy(applyMessage = Strings.get(R.string.plan_you_re_offline_your_draft)) }
            return
        }
        val validation = current.draft.validationError(current.month)
        if (validation != null) {
            mutableState.update { it.copy(draftMessage = validation) }
            return
        }
        mutableState.update { it.copy(isApplying = true, applyMessage = null, draftMessage = null) }
        try {
            if (!flushAutosave()) {
                mutableState.update { it.copy(applyMessage = Strings.get(R.string.plan_save_the_draft_before_applying)) }
                return
            }
            val month = mutableState.value.month
            when (applier.apply(month, mutableState.value.draft.toData(month))) {
                MonthPlanApplyResult.Applied -> {
                    savedDraft = null
                    mutableState.update {
                        it.copy(draft = MonthPlanDraftState(), applyIncomplete = false, applyMessage = Strings.get(R.string.plan_month_plan_applied, month.format(MONTH_LABEL)))
                    }
                    refreshSnapshot(month)
                }
                is MonthPlanApplyResult.Failed -> mutableState.update {
                    it.copy(applyIncomplete = true, applyMessage = Strings.get(R.string.plan_couldn_t_finish_the_plan))
                }
            }
        } finally {
            mutableState.update { it.copy(isApplying = false) }
        }
    }

    suspend fun discardAllChanges() {
        val current = mutableState.value
        if (current.isApplying || current.draftChangeCount == 0) return
        if (!current.isOnline) {
            mutableState.update { it.copy(applyMessage = Strings.get(R.string.plan_you_re_offline_your_draft)) }
            return
        }
        val month = current.month
        try {
            debounceJob?.cancel()
            debounceJob = null
            autosaveInFlight?.join()
            repository.deleteDraft(month)
            savedDraft = null
            mutableState.update { it.copy(draft = MonthPlanDraftState(), applyIncomplete = false, applyMessage = Strings.get(R.string.plan_draft_discarded), draftMessage = null) }
            refreshSnapshot(month)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(applyMessage = Strings.get(R.string.plan_couldn_t_discard_the_draft)) }
        }
    }

    private suspend fun refreshSnapshot(month: YearMonth) {
        try {
            val loaded = repository.load(month)
            if (mutableState.value.month == month) mutableState.update { it.copy(snapshot = loaded, error = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (mutableState.value.month == month) mutableState.update { it.copy(error = loadError(error, it.isOnline)) }
        }
    }

    /** [current] defaults to the live state; screens pass the state they composed from so rows match it. */
    fun rows(current: MonthPlanUiState = mutableState.value): List<MonthPlanRecurringRow> {
        val snapshot = current.snapshot ?: return emptyList()
        val categoryTypes = categoryTypes(snapshot)
        val byTemplate = snapshot.plan.scheduled
            .filter { categoryTypes.containsKey(it.categoryId) }
            .groupBy { it.templateId }
        return byTemplate.mapNotNull { (templateId, occurrences) ->
            val sorted = occurrences.sortedBy { it.date }
            val first = sorted.firstOrNull() ?: return@mapNotNull null
            val isIncome = categoryTypes[first.categoryId] == true
            val skip = current.draft.pendingSkips.firstOrNull { it.templateId == templateId && it.month == current.month.toString() }
            val log = current.draft.pendingLogs.firstOrNull { it.templateId == templateId && it.month == current.month.toString() }
            val amounts = sorted.map { occurrence ->
                val override = log?.overrides?.firstOrNull { it.occurrenceDate == occurrence.date }
                BigDecimal.valueOf(override?.amount ?: occurrence.amount).moneyScale()
            }
            MonthPlanRecurringRow(
                templateId = templateId,
                description = first.description,
                categoryId = first.categoryId,
                isIncome = isIncome,
                dates = sorted.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() },
                amounts = amounts,
                usualAmount = BigDecimal.valueOf(first.amount).moneyScale(),
                amountVaries = sorted.any { it.type != "fixed" } || snapshot.recurring.firstOrNull { it.id == templateId }?.amountVaries == true,
                isSkipped = skip != null,
                hasDraftChange = skip != null || log != null,
                isLogged = log != null,
                hasAmountChange = log?.overrides?.isNotEmpty() == true,
                debtId = sorted.firstNotNullOfOrNull { it.debtId },
            )
        }.sortedWith(compareBy<MonthPlanRecurringRow> { !it.isIncome }.thenBy { it.dates.firstOrNull() }.thenBy { it.description })
    }

    fun transactionRows(isIncome: Boolean, current: MonthPlanUiState = mutableState.value): List<MonthPlanTransactionRow> {
        val snapshot = current.snapshot ?: return emptyList()
        val categories = categoryTypes(snapshot)
        return snapshot.transactions
            .filter { it.templateId != null && categories[it.categoryId] == isIncome }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.description })
            .map { transaction ->
                MonthPlanTransactionRow(
                    transaction = transaction,
                    isIncome = isIncome,
                    isDeleted = current.draft.pendingDeletes.any { it.transactionId == transaction.id },
                )
            }
    }

    /** One-time entries already saved for the month; a bill's own entries are counted through its schedule. */
    fun savedEntries(current: MonthPlanUiState = mutableState.value): List<MonthPlanTransactionRow> {
        val snapshot = current.snapshot ?: return emptyList()
        val categories = categoryTypes(snapshot)
        return snapshot.transactions
            .filter { it.templateId == null }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.description })
            .map { MonthPlanTransactionRow(it, isIncome = categories[it.categoryId] ?: false, isDeleted = false) }
    }

    fun summary(current: MonthPlanUiState = mutableState.value): MonthPlanSummary? {
        val snapshot = current.snapshot ?: return null
        val categories = categoryTypes(snapshot)
        var recordsIncome = BigDecimal.ZERO
        var recordsBills = BigDecimal.ZERO
        var draftIncome = BigDecimal.ZERO
        var draftBills = BigDecimal.ZERO
        var oneTime = BigDecimal.ZERO
        for (occurrence in snapshot.plan.scheduled) {
            val isIncome = categories[occurrence.categoryId] ?: continue
            val baseAmount = BigDecimal.valueOf(occurrence.amount)
            if (isIncome) recordsIncome = recordsIncome.add(baseAmount)
            else recordsBills = recordsBills.add(baseAmount)

            val skip = current.draft.pendingSkips.any { it.templateId == occurrence.templateId && it.month == current.month.toString() && occurrence.date in it.dates }
            if (skip) continue
            val override = current.draft.pendingLogs
                .firstOrNull { it.templateId == occurrence.templateId && it.month == current.month.toString() }
                ?.overrides?.firstOrNull { it.occurrenceDate == occurrence.date }
            val draftAmount = BigDecimal.valueOf(override?.amount ?: occurrence.amount)
            if (isIncome) draftIncome = draftIncome.add(draftAmount) else draftBills = draftBills.add(draftAmount)
        }
        val knownCategories = snapshot.categories.associateBy(Category::id)
        for (entry in current.draft.entries) {
            val isIncome = knownCategories[entry.categoryId]?.isIncome ?: categories[entry.categoryId] ?: false
            if (isIncome) draftIncome = draftIncome.add(BigDecimal.valueOf(entry.amount))
            else oneTime = oneTime.add(BigDecimal.valueOf(entry.amount))
        }
        // Saved one-time entries are part of the records and of the draft alike.
        var savedOneTime = BigDecimal.ZERO
        for (row in savedEntries(current)) {
            val amount = BigDecimal.valueOf(row.transaction.amount)
            if (row.isIncome) {
                recordsIncome = recordsIncome.add(amount)
                draftIncome = draftIncome.add(amount)
            } else {
                savedOneTime = savedOneTime.add(amount)
                oneTime = oneTime.add(amount)
            }
        }
        recordsIncome = recordsIncome.moneyScale()
        recordsBills = recordsBills.moneyScale()
        draftIncome = draftIncome.moneyScale()
        draftBills = draftBills.moneyScale()
        oneTime = oneTime.moneyScale()
        savedOneTime = savedOneTime.moneyScale()
        return MonthPlanSummary(
            recordsIncome = recordsIncome,
            recordsBills = recordsBills,
            recordsLeft = recordsIncome.subtract(recordsBills).subtract(savedOneTime).moneyScale(),
            draftIncome = draftIncome,
            draftBills = draftBills,
            draftOneTime = oneTime,
            draftLeft = draftIncome.subtract(draftBills).subtract(oneTime).moneyScale(),
        )
    }

    private fun categoryTypes(snapshot: MonthPlanSnapshot): Map<String, Boolean> {
        val fromCategories = snapshot.categories.associate { it.id to it.isIncome }
        return snapshot.plan.categories.associate { it.categoryId to it.isIncome } + fromCategories
    }

    private fun loadError(error: Exception, online: Boolean): String = when {
        error is ApiError.Network || !online -> Strings.get(R.string.plan_you_re_offline_the_plan)
        else -> Strings.get(R.string.plan_couldn_t_load_this_month)
    }

    private fun com.spenditos.android.data.model.MonthlyPlanDraftData?.toDraftState() = this?.let {
        MonthPlanDraftState(it.entries, it.pendingEdits, it.pendingLogs, it.pendingDeletes, it.pendingSkips)
    } ?: MonthPlanDraftState()

    companion object {
        const val AUTOSAVE_DELAY_MS = 900L
        private val MONTH_LABEL: DateTimeFormatter get() = LocalizedDate.formatter("MMMM yyyy")
    }
}

internal fun BigDecimal.moneyScale(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
