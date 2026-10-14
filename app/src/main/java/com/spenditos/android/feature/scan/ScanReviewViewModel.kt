package com.spenditos.android.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.model.Category
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val scanId: String = savedStateHandle.get<String>(ARG_SCAN_ID).orEmpty()

    private val _state = MutableStateFlow(ScanReviewUiState())
    val state: StateFlow<ScanReviewUiState> = _state.asStateFlow()

    private val _events = Channel<ScanReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var saveJob: Job? = null

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
                    )
                }
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
    fun onCategoryChange(key: String, categoryId: String?) =
        updateItem(key) { ReviewItemEditing.withCategory(it, categoryId, _state.value.categories) }

    private fun updateItem(key: String, transform: (CaptureReviewItem) -> CaptureReviewItem) {
        if (_state.value.phase == ScanReviewPhase.SAVING) return
        _state.update { current ->
            current.copy(items = current.items.map { if (it.idempotencyKeyId == key && !it.isFrozen) transform(it) else it })
        }
    }

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
