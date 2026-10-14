package com.spenditos.android.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.merchants.MerchantMatcher
import com.spenditos.android.data.merchants.RuleAsk
import com.spenditos.android.data.merchants.RuleLearner
import com.spenditos.android.data.merchants.normalizeMerchantKey
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.scan.ScanActions
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanFailure
import com.spenditos.android.data.scan.ScanRepository
import com.spenditos.android.data.scan.ScanSaveFailure
import com.spenditos.android.data.scan.ScanSaveItem
import com.spenditos.android.data.scan.resolveForOpening
import com.spenditos.android.feature.capture.CaptureReviewItem
import com.spenditos.android.feature.capture.ReviewItemEditing
import com.spenditos.android.feature.capture.parseCaptureAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ScanReviewPhase { LOADING, ERROR, REVIEW, SAVING, DONE }

data class ScanReviewUiState(
    val phase: ScanReviewPhase = ScanReviewPhase.LOADING,
    /** Why the scan could not be opened (phase ERROR). */
    val loadError: String? = null,
    val items: List<CaptureReviewItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currency: String = "USD",
    /** Rows the scan found, flagged ones (already added / not spending) among them: "Found 32 transactions". */
    val foundCount: Int = 0,
    val flaggedCount: Int = 0,
    val ruledCount: Int = 0,
    val failedDocuments: Int = 0,
    val saveError: String? = null,
    val saveProgress: Pair<Int, Int>? = null,
    val showDiscardDialog: Boolean = false,
    val addedCount: Int = 0,
    val failedCount: Int = 0,
    /** Normalized merchant key of every row (by scan item id), computed once on open. */
    val merchantKeys: Map<String, String> = emptyMap(),
    /** Groups the user opened to see their individual rows (by [ReviewGroup.id]). */
    val expandedGroups: Set<String> = emptySet(),
    /** The user's own rules, to tell whether a choice already is one. Empty when they could not be loaded. */
    val userRules: List<MerchantMapping> = emptyList(),
    /** "Always file X under Y?" is waiting for an answer. */
    val ruleAsk: RuleAsk? = null,
    /** Past transactions the rule would change; null while loading. */
    val askPastCount: Int? = null,
    val askAlsoUpdate: Boolean = true,
    val isSavingRule: Boolean = false,
    val ruleMessage: RuleMessage? = null,
) {
    val includedItems: List<CaptureReviewItem> get() = items.filter { it.include }

    /** Included rows that cannot be saved yet: no usable amount, or no date. */
    val incompleteCount: Int
        get() = includedItems.count { parseCaptureAmount(it.amountText) == null || runCatching { LocalDate.parse(it.date) }.isFailure }

    val missingCategoryCount: Int get() = includedItems.count { it.categoryId == null }

    val canSave: Boolean get() = phase == ScanReviewPhase.REVIEW && includedItems.isNotEmpty() && incompleteCount == 0

    val skippedCount: Int get() = maxOf(0, foundCount - addedCount - failedCount)

    /** "Add 12" / "Saving…" / "Saving 100 of 230…" (iOS `savingTitle`). */
    val saveButtonText: String
        get() {
            if (phase != ScanReviewPhase.SAVING) return "Add ${includedItems.size}"
            val progress = saveProgress
            return if (progress != null && progress.second > ScanRepository.BULK_CHUNK_SIZE) "Saving ${progress.first} of ${progress.second}…" else "Saving…"
        }
}

/** The light message after "Always" (iOS `RuleMessage`): what happened, or that the rule could not be saved. */
data class RuleMessage(val id: Long, val text: String, val isError: Boolean)

sealed interface ScanReviewEvent {
    /** Leave the review (done, discarded, or "Review later"). */
    data object Close : ScanReviewEvent

    /** "Scan more" on the done screen: back to the Scan screen. */
    data object ScanMore : ScanReviewEvent
}

/**
 * The month-grouped review of a ready scan (iOS `TransactionReviewListView` with a scan). Rows are the shared
 * [CaptureReviewItem]s of the voice/typed review. Saving goes through `POST /transactions/bulk` in chunks of 100
 * (`source: "scan"`, the scan id, every row's scan item id as idempotency key), then `POST /scans/{id}/complete`.
 *
 * Saving needs the network: unlike iOS, a scan review is not queued offline. The scan itself came from the server and
 * a failed save leaves every row in place, so "try again when you're connected" loses nothing, and resending the same
 * keys can never add a row twice.
 */
@HiltViewModel
class ScanReviewViewModel @Inject constructor(
    private val scans: ScanActions,
    private val coordinator: ScanCoordinator,
    private val dataSource: CaptureDataSource,
    private val mappings: MerchantMappingActions,
    private val learner: RuleLearner,
    private val connectivity: ConnectivityStatus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val scanId: String = savedStateHandle.get<String>(ARG_SCAN_ID).orEmpty()

    private val _state = MutableStateFlow(ScanReviewUiState())
    val state: StateFlow<ScanReviewUiState> = _state.asStateFlow()

    private val _events = Channel<ScanReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var saveJob: Job? = null
    private var askJob: Job? = null
    private var messageJob: Job? = null
    private var nextId = 0L

    init {
        coordinator.start()
        viewModelScope.launch {
            dataSource.categories().collect { categories ->
                _state.update { it.copy(categories = categories, items = resolveScanCategories(it.items, categories)) }
            }
        }
        viewModelScope.launch { dataSource.currency().collect { currency -> _state.update { it.copy(currency = currency) } } }
        load()
    }

    fun load() {
        _state.update { it.copy(phase = ScanReviewPhase.LOADING, loadError = null) }
        viewModelScope.launch {
            try {
                val detail = coordinator.takeDetail(scanId) ?: scans.get(scanId)
                // Anything but a ready scan explains itself (already added, failed, cancelled, still reading).
                if (detail.state != ScanStatus.READY) detail.resolveForOpening()
                val rows = detail.items.orEmpty()
                if (rows.isEmpty()) throw ScanFailure.NoTransactionsFound
                val categories = _state.value.categories
                _state.update {
                    it.copy(
                        phase = ScanReviewPhase.REVIEW,
                        items = resolveScanCategories(rows.map { row -> row.toReviewItem(categories) }, categories),
                        foundCount = rows.size,
                        flaggedCount = rows.count { row -> row.skip != null },
                        ruledCount = rows.count { row -> row.ruleId != null },
                        failedDocuments = detail.summary.failedDocuments,
                        merchantKeys = rows.associate { row -> row.id to normalizeMerchantKey(row.merchantName) },
                    )
                }
                loadRules()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.w("ScanReview", "open failed: ${error::class.java.simpleName}")
                _state.update { it.copy(phase = ScanReviewPhase.ERROR, loadError = openErrorMessage(error)) }
            }
        }
    }

    // region Editing

    fun toggleInclude(key: String) = updateItem(key, ReviewItemEditing::toggled)
    fun onAmountChange(key: String, text: String) = updateItem(key) { ReviewItemEditing.withAmountText(it, text) }
    fun onDescriptionChange(key: String, text: String) = updateItem(key) { ReviewItemEditing.withDescription(it, text) }
    fun onDateChange(key: String, date: LocalDate) = updateItem(key) { ReviewItemEditing.withDate(it, date) }
    fun onTypeChange(key: String, income: Boolean) = updateItem(key) { ReviewItemEditing.withType(it, income) }
    /** The row editor's category choice. A real category may start the "Always ...?" question; clearing it does not. */
    fun onCategoryChange(key: String, categoryId: String?) {
        if (categoryId == null) updateItem(key) { ReviewItemEditing.withCategory(it, null, _state.value.categories) } else categoryChosen(listOf(key), categoryId)
    }

    /** One checkbox for the whole group: everything on unless it all is. */
    fun toggleGroup(keys: List<String>) {
        if (_state.value.phase == ScanReviewPhase.SAVING) return
        val wanted = keys.toSet()
        _state.update { current ->
            val group = current.items.filter { it.idempotencyKeyId in wanted }
            val turnOn = !group.all { it.include }
            current.copy(items = current.items.map { if (it.idempotencyKeyId in wanted && !it.isFrozen) it.copy(include = turnOn, failureReason = null) else it })
        }
    }

    fun toggleGroupExpanded(groupId: String) =
        _state.update { it.copy(expandedGroups = if (groupId in it.expandedGroups) it.expandedGroups - groupId else it.expandedGroups + groupId) }

    /** A category picked for a whole group: every row gets it, then the same question as for one row. */
    fun onGroupCategoryChosen(keys: List<String>, categoryId: String) = categoryChosen(keys, categoryId)

    private fun updateItem(key: String, transform: (CaptureReviewItem) -> CaptureReviewItem) {
        if (_state.value.phase == ScanReviewPhase.SAVING) return
        _state.update { current ->
            current.copy(items = current.items.map { if (it.idempotencyKeyId == key && !it.isFrozen) transform(it) else it })
        }
    }

    // endregion

    // region Rules (Always / Just this once)

    /** The user's rules, to tell whether a choice already is one. Best effort: without them the question is asked and the server upserts anyway. */
    private fun loadRules() {
        if (!connectivity.isOnline.value) return
        viewModelScope.launch {
            try {
                val rules = mappings.rules()
                _state.update { it.copy(userRules = rules) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Rules only avoid a redundant question.
            }
        }
    }

    /**
     * A category was picked for these rows (a single row or a whole group). Sets it on every row, keeps "Your rule"
     * only where a rule really files it that way, and queues the "Always ...?" question when it applies.
     */
    private fun categoryChosen(keys: List<String>, categoryId: String) {
        val current = _state.value
        if (current.phase == ScanReviewPhase.SAVING || categoryId.isEmpty()) return
        val wanted = keys.toSet()
        val identical = MerchantMatcher.identicalRule(current.items.firstOrNull { it.idempotencyKeyId in wanted }?.merchantName, categoryId, current.userRules)
        var merchant: String? = null
        val updated = current.items.map { item ->
            if (item.idempotencyKeyId !in wanted || item.isFrozen) return@map item
            if (merchant == null) merchant = item.merchantName?.trim()?.takeIf { it.isNotEmpty() }
            var row = if (item.categoryId != categoryId) ReviewItemEditing.withCategory(item, categoryId, current.categories) else item
            // A rule that files this merchant here already covers the row.
            if (identical != null && row.skipReason != "rule") row = row.copy(ruleId = identical.id)
            row
        }
        _state.update { it.copy(items = updated) }

        val name = merchant ?: return
        val category = current.categories.firstOrNull { it.id == categoryId } ?: return
        if (!connectivity.isOnline.value || !MerchantMatcher.shouldOfferRule(name, categoryId, current.userRules)) return
        val ask = RuleAsk(nextId++, name, categoryId, category.name, keys)
        _state.update { it.copy(ruleAsk = ask, askPastCount = null, askAlsoUpdate = true, isSavingRule = false) }
        askJob?.cancel()
        askJob = viewModelScope.launch {
            // The checkbox only exists when there is something to update. A failed count just means no checkbox.
            val count = try {
                mappings.matchCount(ask.merchant, ask.categoryId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            _state.update { if (it.ruleAsk?.id == ask.id) it.copy(askPastCount = count ?: 0) else it }
        }
    }

    fun toggleAskUpdatePast() = _state.update { it.copy(askAlsoUpdate = !it.askAlsoUpdate) }

    /** "Just this once": the choice stays on the rows; no rule is written. */
    fun answerOnce() {
        if (_state.value.isSavingRule) return
        askJob?.cancel()
        _state.update { it.copy(ruleAsk = null, askPastCount = null) }
    }

    /**
     * "Always": upsert the rule, optionally apply it to past transactions, mark the rows "Your rule" and say so. A
     * failure shows a light message; the scan can still be saved (nothing here touches the reviewed rows' save).
     */
    fun answerAlways() {
        val current = _state.value
        val ask = current.ruleAsk ?: return
        if (current.isSavingRule) return
        _state.update { it.copy(isSavingRule = true) }
        val alsoUpdate = current.askAlsoUpdate && (current.askPastCount ?: 0) > 0
        viewModelScope.launch {
            val outcome = learner.always(ask.merchant, ask.categoryId, ask.categoryName, alsoUpdate)
            val rule = outcome.rule
            _state.update { state ->
                val keys = ask.itemKeys.toSet()
                state.copy(
                    isSavingRule = false,
                    ruleAsk = null,
                    askPastCount = null,
                    userRules = if (rule != null) state.userRules.filterNot { it.id == rule.id } + rule else state.userRules,
                    items = if (rule == null) state.items else state.items.map { item ->
                        if (item.idempotencyKeyId in keys && item.categoryId == ask.categoryId) {
                            // The category rule replaces any skip rule.
                            item.copy(ruleId = rule.id, skipReason = item.skipReason.takeIf { it != "rule" })
                        } else {
                            item
                        }
                    },
                )
            }
            showRuleMessage(outcome.message, isError = outcome.failed)
        }
    }

    private fun showRuleMessage(text: String, isError: Boolean) {
        val message = RuleMessage(nextId++, text, isError)
        _state.update { it.copy(ruleMessage = message) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(RULE_MESSAGE_MILLIS)
            _state.update { if (it.ruleMessage?.id == message.id) it.copy(ruleMessage = null) else it }
        }
    }

    fun dismissRuleMessage() = _state.update { it.copy(ruleMessage = null) }

    // endregion

    // region Save

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val toSave = current.includedItems.map { item ->
            val income = item.transactionType.equals("income", ignoreCase = true)
            ScanSaveItem(
                idempotencyKeyId = item.idempotencyKeyId,
                description = item.description.trim(),
                amount = requireNotNull(parseCaptureAmount(item.amountText)).toDouble(),
                date = LocalDate.parse(item.date),
                // A category the user no longer has, or of the other kind, is dropped: the server files it as Uncategorized.
                categoryId = item.categoryId?.takeIf { id -> current.categories.any { it.id == id && it.isIncome == income } },
                isIncome = income,
            )
        }
        val savingKeys = toSave.mapTo(HashSet()) { it.idempotencyKeyId }
        _state.update {
            it.copy(
                phase = ScanReviewPhase.SAVING,
                saveError = null,
                saveProgress = null,
                items = it.items.map { item -> if (item.idempotencyKeyId in savingKeys) item.copy(isFrozen = true, failureReason = null) else item },
            )
        }
        saveJob = viewModelScope.launch {
            try {
                val outcome = scans.save(scanId, toSave) { handled, total -> _state.update { it.copy(saveProgress = handled to total) } }
                // Closes the scan even if this screen is gone by the time the request returns.
                coordinator.completeInBackground(scanId, ScanOutcome.SAVED)
                _state.update {
                    it.copy(phase = ScanReviewPhase.DONE, addedCount = outcome.saved, failedCount = outcome.failed, saveProgress = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.w("ScanReview", "save failed: ${error::class.java.simpleName}")
                val failure = error as? ScanSaveFailure
                val cause = failure?.cause ?: error
                // A client error means the chunk was refused and nothing in it was written. Rows of earlier chunks
                // are already saved, and a transport / server error leaves the chunk's outcome unknown: those rows
                // stay frozen so a retry resends exactly the same payload (same idempotency keys, same categories).
                val refused = cause is ApiError.Server && cause.status in 400..499
                val handled = failure?.handled ?: 0
                _state.update { current ->
                    var seen = 0
                    current.copy(
                        phase = ScanReviewPhase.REVIEW,
                        saveProgress = null,
                        saveError = saveErrorMessage(cause),
                        items = current.items.map { item ->
                            if (item.idempotencyKeyId !in savingKeys) return@map item
                            val position = seen++
                            item.copy(isFrozen = !(refused && position >= handled))
                        },
                    )
                }
            }
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    // endregion

    // region Leaving

    /** Cancel or Back: nothing is saved yet, so ask before discarding the scan (iOS `cancelReview`). */
    fun requestClose() {
        when (_state.value.phase) {
            ScanReviewPhase.SAVING -> Unit
            ScanReviewPhase.REVIEW -> _state.update { it.copy(showDiscardDialog = true) }
            else -> viewModelScope.launch { _events.send(ScanReviewEvent.Close) }
        }
    }

    fun keepReviewing() = _state.update { it.copy(showDiscardDialog = false) }

    /** "Review later": the scan stays on Home as ready. */
    fun reviewLater() {
        _state.update { it.copy(showDiscardDialog = false) }
        viewModelScope.launch { _events.send(ScanReviewEvent.Close) }
    }

    fun discardScan() {
        _state.update { it.copy(showDiscardDialog = false) }
        coordinator.completeInBackground(scanId, ScanOutcome.DISCARDED)
        viewModelScope.launch { _events.send(ScanReviewEvent.Close) }
    }

    fun done() {
        viewModelScope.launch { _events.send(ScanReviewEvent.Close) }
    }

    fun scanMore() {
        viewModelScope.launch { _events.send(ScanReviewEvent.ScanMore) }
    }

    // endregion

    companion object {
        const val ARG_SCAN_ID = "scanId"

        /** How long the "Rule saved" message stays (iOS shows it for 6 s). */
        const val RULE_MESSAGE_MILLIS = 6_000L

        internal fun openErrorMessage(error: Throwable): String = when {
            error is ScanFailure -> error.userMessage
            error is ApiError.Network -> "You're offline. Reconnect to open this scan."
            error is ApiError.Server && error.status == 404 -> "This scan is no longer available."
            else -> "Couldn't open this scan. Try again."
        }

        internal fun saveErrorMessage(error: Throwable): String = when {
            error is ApiError.Network && error.cause is SessionChangedException ->
                "The signed-in account changed, so this scan wasn't saved."
            error is ApiError.Network -> "You're offline. Your changes are kept — try again when you're connected."
            error is ApiError.Server && error.status == 404 -> "This scan is no longer available."
            else -> "Couldn't save these transactions. Try again."
        }
    }
}
