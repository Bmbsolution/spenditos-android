package com.spenditos.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.transactions.BillCandidate
import com.spenditos.android.data.transactions.BillMatching
import com.spenditos.android.data.transactions.CreateResult
import com.spenditos.android.data.transactions.LastUsedCategories
import com.spenditos.android.data.transactions.NewTransaction
import com.spenditos.android.data.transactions.RecurrencePreset
import com.spenditos.android.data.transactions.TransactionBusyException
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

enum class EntryType { EXPENSE, INCOME }

/** The transaction being edited, as it was when the form opened. */
data class EditTarget(
    val id: String,
    val amount: Double,
    val categoryId: String,
    val description: String,
    val date: LocalDate,
    val mood: TransactionMood?,
)

fun TransactionEntity.toEditTarget() = EditTarget(
    id = id,
    amount = amount,
    categoryId = categoryId,
    description = description,
    date = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
    mood = mood?.let { name -> TransactionMood.entries.firstOrNull { it.name == name } },
)

/** A payment saved with "Save and add another", offered for Undo for a few seconds. */
data class UndoTarget(val transactionId: String, val description: String, val amount: Double)

data class AddTransactionUiState(
    val type: EntryType = EntryType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val mood: TransactionMood? = null,
    val categories: List<Category> = emptyList(),
    val currency: String = "USD",
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Set by Save when something is missing; the form then marks the missing fields. */
    val showValidation: Boolean = false,
    /** "Last used category" / "From your rule" when the category was suggested, not picked. */
    val categorySuggestionSource: String? = null,
    val suggestedCategoryId: String? = null,
    val lastUsedCategoryId: String? = null,
    /** "Receipt scan" when values came from an assisted flow. */
    val assistedEntrySource: String? = null,
    val editing: EditTarget? = null,
    // Make it recurring
    val makeRecurring: Boolean = false,
    val recurrencePreset: RecurrencePreset = RecurrencePreset.MONTHLY,
    val nextDue: LocalDate = RecurrencePreset.MONTHLY.next(LocalDate.now()),
    val nextDueTouched: Boolean = false,
    // Save and add another
    val undo: UndoTarget? = null,
    val addedCount: Int = 0,
    // Bill checks: asked before a create; empty/null when there is nothing to ask.
    val billChoices: List<BillCandidate> = emptyList(),
    val existingBill: RecurringTemplate? = null,
    /** The payment is saved and only its repeat is waiting: amount and description are locked. */
    val paymentSaved: Boolean = false,
) {
    val isEditMode: Boolean get() = editing != null

    /** Income vs expense is the category's `isIncome` (iOS TransactionType is UI-only). */
    val visibleCategories: List<Category> get() = categories.filter { it.isIncome == (type == EntryType.INCOME) }
    val amount: BigDecimal? get() = parseAmount(amountText)
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == categoryId }
    val isValid: Boolean get() = (amount?.signum() ?: 0) > 0 && categoryId != null

    /** Save stays enabled and says what's missing (decisions.md §3); only an in-flight save blocks it. */
    val canSave: Boolean get() = !isSaving
    val amountMissing: Boolean get() = showValidation && (amount?.signum() ?: 0) <= 0
    val categoryMissing: Boolean get() = showValidation && categoryId == null

    /** Up to 5: the selected category, the last used one, then the rest in order (iOS `quickCategoryChoices`). */
    val quickCategoryChoices: List<Category>
        get() {
            val visible = visibleCategories
            val choices = mutableListOf<Category>()
            fun add(category: Category?) {
                if (category != null && visible.any { it.id == category.id } && choices.none { it.id == category.id }) {
                    choices += category
                }
            }
            add(selectedCategory)
            add(visible.firstOrNull { it.id == lastUsedCategoryId })
            for (category in visible) {
                if (choices.size >= 5) break
                add(category)
            }
            return choices
        }

    val hasUnsavedChanges: Boolean
        get() {
            editing?.let { e ->
                return amountText != "%.2f".format(java.util.Locale.US, e.amount) || description != e.description ||
                    categoryId != e.categoryId || date != e.date || mood != e.mood
            }
            // A suggested category (last used, merchant rule) isn't the user's change.
            val pickedCategory = categoryId != null && categoryId != suggestedCategoryId
            return amountText.isNotEmpty() || description.isNotEmpty() || pickedCategory || mood != null
        }

    /** Subtitle under "Make it recurring". */
    val recurringSubtitle: String
        get() {
            if (makeRecurring) return "${recurrencePreset.label}, next due ${shortDate(nextDue)}"
            val name = selectedCategory?.name?.lowercase().orEmpty()
            if (USUALLY_REPEATS.any { name.contains(it) }) return "${selectedCategory?.name ?: "This"} usually repeats. Turn on to track it."
            return "Adds it to your bills and income"
        }

    val recurringHint: String
        get() = "This payment is logged on ${shortDate(date)}. Then ${shortDate(nextDue)}, " +
            "${shortDate(recurrencePreset.next(nextDue))}, and so on. Due on a different day than you paid? Change Next due."

    private companion object {
        val USUALLY_REPEATS = listOf("rent", "utilities", "subscriptions", "salary", "insurance", "phone", "internet")
    }
}

/** "Oct 7": the iOS `.dateTime.month(.abbreviated).day()` format. */
internal fun shortDate(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))

/** Accepts "12.50" and "12,50"; at most two decimals; null when not a number. */
internal fun parseAmount(text: String): BigDecimal? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    return normalized.toBigDecimalOrNull()?.takeIf { it.scale() <= 2 }
}

/**
 * Values to start the form with, e.g. from a receipt scan. Everything is optional and is only
 * applied when valid; the user always reviews and saves (nothing is created by a scan itself).
 */
data class AddTransactionPrefill(
    val amount: Double? = null,
    val description: String? = null,
    /** `YYYY-MM-DD`; ignored when it doesn't parse. */
    val date: String? = null,
    /** A category id from the server; ignored when the user has no such category. */
    val categoryId: String? = null,
    /** A category name hint (case-insensitive match), used when [categoryId] doesn't resolve. */
    val categoryHint: String? = null,
    /** Shown as "Suggested from …" above the form; null for a plain manual open. */
    val source: String? = null,
)

sealed interface AddTransactionEvent {
    /** The sheet should close and show [message]. */
    data class Done(val message: String) : AddTransactionEvent

    /** "Save and add another" saved; the form was cleared for the next entry. */
    data object SavedAndContinue : AddTransactionEvent
}

/**
 * The add and edit form (iOS `AddTransactionViewModel`, decisions.md §3): one form for both so
 * they can't drift. Creates go through the offline queue; edits, bill payments and repeats need
 * the server.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    settings: UserSettingsRepository,
    private val recurring: RecurringActions,
    private val merchantMappings: MerchantMappingActions,
    private val lastUsed: LastUsedCategories,
    private val connectivity: ConnectivityStatus,
) : ViewModel() {
    private val form = MutableStateFlow(AddTransactionUiState())
    private val _events = Channel<AddTransactionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<AddTransactionUiState> = combine(form, categories.observe(), settings.currency()) { f, cats, currency ->
        f.copy(categories = cats, currency = currency)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AddTransactionUiState())

    private var mappings: List<MerchantMapping> = emptyList()
    private var templates: List<RecurringTemplate> = emptyList()

    /** "Use existing bill": the payment is saved linked to this bill. */
    private var linkedTemplate: RecurringTemplate? = null

    /** When set, Save pays this generated bill entry instead of creating a second transaction. */
    private var billEntryToReplace: BillCandidate? = null

    /** The payment this entry created; a retry after a failed repeat setup only retries the repeat. */
    private var createdTransactionId: String? = null
    private var createdWithRepeat = false
    private var continueAfterSave = false
    private var undoJob: Job? = null

    init {
        viewModelScope.launch { loadLastUsed(suggest = true) }
        // Best effort: rules and bills only improve suggestions and the duplicate check.
        viewModelScope.launch {
            mappings = runCatching { merchantMappings.list() }.getOrDefault(emptyList())
            applyMerchantRule()
        }
        viewModelScope.launch { recurring.observe().collect { templates = it } }
    }

    /** Opens the form on an existing transaction (edit mode). Call once, before the user types. */
    fun startEdit(target: EditTarget) {
        viewModelScope.launch {
            val categories = categoriesLoaded()
            val category = categories.firstOrNull { it.id == target.categoryId }
            form.update {
                it.copy(
                    editing = target,
                    type = if (category?.isIncome == true) EntryType.INCOME else EntryType.EXPENSE,
                    amountText = "%.2f".format(java.util.Locale.US, target.amount),
                    description = target.description,
                    categoryId = target.categoryId,
                    date = target.date,
                    mood = target.mood,
                    categorySuggestionSource = null,
                    suggestedCategoryId = null,
                    assistedEntrySource = null,
                )
            }
        }
    }

    /** Applies [prefill] once the user's categories are known; safe to call repeatedly for the same value. */
    fun applyPrefill(prefill: AddTransactionPrefill) {
        val categories = state.value.categories
        val category = categories.firstOrNull { it.id == prefill.categoryId }
            ?: prefill.categoryHint?.trim()?.takeIf { it.isNotEmpty() }?.let { hint ->
                categories.firstOrNull { it.name.equals(hint, ignoreCase = true) }
            }
        val parsedDate = prefill.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        form.update { f ->
            f.copy(
                type = category?.let { if (it.isIncome) EntryType.INCOME else EntryType.EXPENSE } ?: f.type,
                amountText = prefill.amount?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).toPlainString() } ?: f.amountText,
                categoryId = category?.id ?: f.categoryId,
                categorySuggestionSource = if (category != null) null else f.categorySuggestionSource,
                suggestedCategoryId = if (category != null) null else f.suggestedCategoryId,
                description = prefill.description?.take(200) ?: f.description,
                date = parsedDate ?: f.date,
                assistedEntrySource = prefill.source ?: f.assistedEntrySource,
                error = null,
            )
        }
    }

    /** Changing the kind clears the category and suggests that kind's last used one. */
    fun onTypeChange(type: EntryType) {
        if (form.value.type == type) return
        form.update {
            it.copy(
                type = type, categoryId = null, categorySuggestionSource = null, suggestedCategoryId = null,
                mood = if (type == EntryType.INCOME) null else it.mood, error = null,
            )
        }
        viewModelScope.launch { loadLastUsed(suggest = true) }
    }

    fun onAmountChange(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null) }

    fun onCategorySelect(id: String) = form.update { it.copy(categoryId = id, error = null) }

    fun onDescriptionChange(text: String) {
        form.update { it.copy(description = text.take(200), error = null) }
        applyMerchantRule()
    }

    fun onDateChange(date: LocalDate) = form.update {
        val nextDue = if (it.nextDueTouched) maxOf(it.nextDue, date) else it.recurrencePreset.next(date)
        it.copy(date = date, nextDue = nextDue)
    }

    fun onMoodChange(mood: TransactionMood?) = form.update { it.copy(mood = mood) }

    fun onMakeRecurringChange(on: Boolean) = form.update { it.copy(makeRecurring = on, error = null) }

    fun onRecurrencePresetChange(preset: RecurrencePreset) = form.update {
        it.copy(recurrencePreset = preset, nextDue = if (it.nextDueTouched) it.nextDue else preset.next(it.date))
    }

    fun onNextDueChange(date: LocalDate) = form.update { it.copy(nextDue = maxOf(date, it.date), nextDueTouched = true) }

    fun dismissError() = form.update { it.copy(error = null) }

    /**
     * Save (iOS `attemptSave`). Asks the bill questions first for a new entry; the skip flags are
     * per attempt (set only by the dialog that answered), so later edits are checked again.
     */
    fun save(andContinue: Boolean = false, skipExistingBill: Boolean = false, skipDuplicate: Boolean = false) {
        val current = current()
        if (current.isSaving) return
        continueAfterSave = andContinue && !current.isEditMode
        if (!current.isValid) {
            form.update { it.copy(showValidation = true) }
            return
        }
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                if (createdTransactionId == null && !current.isEditMode) {
                    if (!skipExistingBill) {
                        existingBillForRepeat(current)?.let { bill ->
                            form.update { it.copy(existingBill = bill, isSaving = false) }
                            return@launch
                        }
                    }
                    if (!skipDuplicate && billEntryToReplace == null && linkedTemplate == null) {
                        val candidates = billCandidates(current)
                        if (candidates.isNotEmpty()) {
                            form.update { it.copy(billChoices = candidates, isSaving = false) }
                            return@launch
                        }
                    }
                }
                commit()
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }

    /** "Use this one" / a picked bill from the duplicate question: pays that entry. */
    fun chooseBill(candidate: BillCandidate) {
        billEntryToReplace = candidate
        form.update { it.copy(billChoices = emptyList()) }
        runCommit()
    }

    /** "Save as separate": skips both bill questions for this attempt. */
    fun saveSeparate() {
        form.update { it.copy(billChoices = emptyList()) }
        save(andContinue = continueAfterSave, skipExistingBill = true, skipDuplicate = true)
    }

    /** "Use existing bill": the payment pays that bill's nearest generated entry, or is linked to it. */
    fun useExistingBill() {
        val bill = form.value.existingBill ?: return
        form.update { it.copy(existingBill = null, makeRecurring = false, isSaving = true) }
        linkedTemplate = bill
        viewModelScope.launch {
            try {
                val date = form.value.date
                val entries = runCatching {
                    transactions.fetchRange(date.minusDays(BillMatching.DATE_WINDOW_DAYS.toLong()), date.plusDays(40))
                }.getOrDefault(emptyList())
                // Only entries from 4 days before the payment onward: an earlier one is most
                // likely an already-paid month and must not be overwritten.
                entries.filter { it.templateId == bill.id }.minByOrNull { it.date }?.let {
                    billEntryToReplace = BillCandidate(it, bill.description, 0.0)
                }
                commit()
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }

    /** "Create a new bill": keeps Make it recurring and skips the existing-bill question. */
    fun createNewBill() {
        form.update { it.copy(existingBill = null) }
        save(andContinue = continueAfterSave, skipExistingBill = true)
    }

    fun dismissBillQuestion() = form.update { it.copy(billChoices = emptyList(), existingBill = null) }

    /** Undo for the last "Save and add another" entry. */
    fun undo() {
        val target = form.value.undo ?: return
        undoJob?.cancel()
        form.update { it.copy(undo = null) }
        viewModelScope.launch {
            try {
                transactions.delete(target.transactionId)
                form.update { it.copy(addedCount = (it.addedCount - 1).coerceAtLeast(0)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(error = deleteErrorMessage(e)) }
            }
        }
    }

    /** Edit mode: deletes the transaction, then closes the form. */
    fun delete() {
        val target = form.value.editing ?: return
        if (form.value.isSaving) return
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                transactions.delete(target.id)
                _events.send(AddTransactionEvent.Done("Transaction deleted"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(error = deleteErrorMessage(e)) }
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun runCommit() {
        if (form.value.isSaving) return
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                commit()
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }

    /** iOS `save()`: writes the entry after the bill questions are answered. */
    private suspend fun commit() {
        val current = current()
        val amount = current.amount?.toDouble() ?: return
        val categoryId = current.categoryId ?: return
        val draft = NewTransaction(
            amount = amount,
            categoryId = categoryId,
            description = current.description,
            date = current.date,
            mood = current.mood,
            templateId = linkedTemplate?.id,
        )
        try {
            // The payment already exists; only its repeat is pending. Never create it again.
            if (current.editing == null && createdTransactionId != null) {
                if (current.makeRecurring) {
                    if (!setUpRepeat(current)) return
                    createdWithRepeat = true
                }
                finishSave(current, offline = false)
                return
            }

            if (current.editing != null) {
                if (current.editing.id.startsWith("local-")) {
                    form.update { it.copy(error = "This one hasn't synced yet. Edit it once it's synced.") }
                    return
                }
                transactions.update(current.editing.id, draft)
                _events.send(AddTransactionEvent.Done("Transaction updated"))
                return
            }

            val needsServer = current.makeRecurring || linkedTemplate != null || billEntryToReplace != null
            if (needsServer && !connectivity.isOnline.value) {
                // Offline saves are queued as plain transactions; a repeat or a link to a bill
                // needs the server, so don't drop that intent silently.
                form.update { it.copy(error = OFFLINE_REPEAT) }
                resetBillLinks()
                return
            }

            billEntryToReplace?.let { candidate ->
                transactions.payBillEntry(candidate.entry, draft)
                createdTransactionId = candidate.entry.id
                createdWithRepeat = false
                rememberCategory(current)
                // A paid bill entry existed before: Undo must not delete it.
                finishSave(current, offline = false, undoId = null)
                return
            }

            when (val result = transactions.create(draft)) {
                is CreateResult.Rejected -> {
                    form.update { it.copy(error = result.message) }
                    resetBillLinks()
                }
                CreateResult.SavedOffline -> {
                    rememberCategory(current)
                    finishSave(current, offline = true)
                }
                is CreateResult.Saved -> {
                    createdTransactionId = result.transactionId ?: ""
                    createdWithRepeat = false
                    form.update { it.copy(paymentSaved = true) }
                    rememberCategory(current)
                    if (current.makeRecurring && linkedTemplate == null) {
                        // The payment is saved; on failure the form stays open so the repeat can be retried.
                        if (!setUpRepeat(current)) return
                        createdWithRepeat = true
                    }
                    finishSave(current, offline = false, undoId = result.transactionId.takeUnless { createdWithRepeat })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            form.update { it.copy(error = saveErrorMessage(e)) }
            if (createdTransactionId == null) resetBillLinks()
        }
    }

    /** Creates the bill starting at Next due; false (with the error shown) when it failed. */
    private suspend fun setUpRepeat(current: AddTransactionUiState): Boolean {
        val amount = current.amount?.toDouble() ?: return false
        val categoryId = current.categoryId ?: return false
        val name = current.description.trim().ifEmpty { current.selectedCategory?.name ?: "Bill" }
        return try {
            recurring.create(
                CreateRecurringTemplateRequest(
                    categoryId = categoryId,
                    description = name,
                    amount = amount,
                    type = "fixed",
                    frequency = current.recurrencePreset.frequency,
                    firstOccurrenceDate = current.nextDue.toString(),
                    occurrencesRemaining = JsonNull,
                    isActive = true,
                    secondOccurrenceDay = if (current.recurrencePreset == RecurrencePreset.SEMI_MONTHLY) {
                        JsonPrimitive(RecurrencePreset.secondDay(current.nextDue.dayOfMonth))
                    } else {
                        JsonNull
                    },
                ),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            form.update { it.copy(error = REPEAT_FAILED) }
            false
        }
    }

    private suspend fun finishSave(current: AddTransactionUiState, offline: Boolean, undoId: String? = null) {
        val message = if (offline) "Saved on this device. It will sync when you're back online." else "Saved"
        if (!continueAfterSave) {
            _events.send(AddTransactionEvent.Done(message))
            return
        }
        continueAfterSave = false
        // "Save and add another": clear the entry but keep the kind and date.
        val undo = undoId?.takeIf { it.isNotEmpty() }?.let { UndoTarget(it, current.description, current.amount?.toDouble() ?: 0.0) }
        form.update {
            AddTransactionUiState(
                type = it.type, date = it.date, categories = it.categories, currency = it.currency,
                nextDue = RecurrencePreset.MONTHLY.next(it.date),
                undo = undo, addedCount = it.addedCount + 1,
            )
        }
        resetBillLinks()
        createdTransactionId = null
        createdWithRepeat = false
        loadLastUsed(suggest = true)
        undoJob?.cancel()
        if (undo != null) {
            undoJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                form.update { if (it.undo == undo) it.copy(undo = null) else it }
            }
        }
        _events.send(AddTransactionEvent.SavedAndContinue)
    }

    private fun resetBillLinks() {
        billEntryToReplace = null
        linkedTemplate = null
    }

    /** iOS `existingBillForRepeat`: the bill that already covers this merchant. */
    private fun existingBillForRepeat(current: AddTransactionUiState): RecurringTemplate? {
        if (!current.makeRecurring || linkedTemplate != null) return null
        val amount = current.amount?.toDouble() ?: return null
        return BillMatching.existingTemplate(current.description, current.categoryId ?: "", amount, templates, mappings)
    }

    /** iOS `billCandidates`: generated entries this payment may be paying. Any failure means none. */
    private suspend fun billCandidates(current: AddTransactionUiState): List<BillCandidate> {
        if (current.makeRecurring || !connectivity.isOnline.value) return emptyList()
        val amount = current.amount?.toDouble()?.takeIf { it > 0 } ?: return emptyList()
        val window = BillMatching.DATE_WINDOW_DAYS.toLong()
        val entries = try {
            transactions.fetchRange(current.date.minusDays(window), current.date.plusDays(window))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        // Only entries of the same kind: an income refund never pays an expense bill.
        val wantIncome = current.type == EntryType.INCOME
        val sameKind = entries.filter { entry -> current.categories.firstOrNull { it.id == entry.categoryId }?.isIncome == wantIncome }
        return BillMatching.candidates(amount, current.description, current.categoryId ?: "", current.date, sameKind, templates, mappings)
    }

    /** Picks the category from the user's merchant rules as they type; never overrides a hand pick. */
    private fun applyMerchantRule() {
        val current = form.value
        if (current.isEditMode) return
        val isSuggestion = current.categoryId == null || current.categoryId == current.suggestedCategoryId
        if (!isSuggestion) return
        val categories = state.value.categories
        val wantIncome = current.type == EntryType.INCOME
        val rule = BillMatching.rule(current.description, mappings)
        val category = rule?.categoryId?.let { id -> categories.firstOrNull { it.id == id && it.isIncome == wantIncome } }
        if (category != null) {
            if (current.categoryId != category.id || current.categorySuggestionSource != RULE_SOURCE) {
                form.update { it.copy(categoryId = category.id, suggestedCategoryId = category.id, categorySuggestionSource = RULE_SOURCE) }
            }
        } else if (current.categorySuggestionSource == RULE_SOURCE) {
            form.update { it.copy(categoryId = null, suggestedCategoryId = null, categorySuggestionSource = null) }
            viewModelScope.launch { loadLastUsed(suggest = true) }
        }
    }

    /** Reads the kind's last used category; with [suggest], selects it when nothing is picked yet. */
    private suspend fun loadLastUsed(suggest: Boolean) {
        val type = form.value.type
        val id = runCatching { lastUsed.get(type == EntryType.INCOME) }.getOrNull()
        val categories = categoriesLoaded()
        form.update { f ->
            if (f.type != type) return@update f
            val category = categories.firstOrNull { it.id == id && it.isIncome == (type == EntryType.INCOME) }
            val canSuggest = suggest && !f.isEditMode && f.categoryId == null && f.assistedEntrySource == null
            if (category != null && canSuggest) {
                f.copy(lastUsedCategoryId = id, categoryId = category.id, suggestedCategoryId = category.id, categorySuggestionSource = LAST_USED_SOURCE)
            } else {
                f.copy(lastUsedCategoryId = id)
            }
        }
    }

    /**
     * The form as of now. [state] is combined on the main dispatcher, so right after an input it can
     * still hold the previous values; saves read the form itself.
     */
    private fun current(): AddTransactionUiState =
        state.value.let { form.value.copy(categories = it.categories, currency = it.currency) }

    private suspend fun categoriesLoaded(): List<Category> =
        state.value.categories.ifEmpty { state.first { it.categories.isNotEmpty() }.categories }

    private suspend fun rememberCategory(current: AddTransactionUiState) {
        val id = current.categoryId ?: return
        runCatching { lastUsed.set(current.type == EntryType.INCOME, id) }
    }

    private fun saveErrorMessage(e: Exception): String = when (e) {
        is ApiError.Unauthorized -> "Sign in again to save."
        is ApiError.Network -> "You're offline. Check your connection and try again."
        else -> "Couldn't save. Please try again."
    }

    private fun deleteErrorMessage(e: Exception): String = when (e) {
        is TransactionBusyException -> e.message.orEmpty()
        is ApiError.Unauthorized -> "Sign in again to delete."
        is ApiError.Network -> "You're offline. Check your connection and try again."
        else -> "Couldn't delete. Please try again."
    }

    companion object {
        const val RULE_SOURCE = "From your rule"
        const val LAST_USED_SOURCE = "Last used category"
        const val OFFLINE_REPEAT = "You're offline. Turn off Make it recurring to save this now, or try again when you're back online."
        const val REPEAT_FAILED = "Your payment is saved, but the repeat couldn't be set up. Tap Save to try again."
        private const val UNDO_WINDOW_MS = 6_000L
    }
}
