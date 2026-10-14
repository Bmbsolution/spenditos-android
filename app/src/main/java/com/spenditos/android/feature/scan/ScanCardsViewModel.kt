package com.spenditos.android.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.scan.ScanCardState
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanFailure
import com.spenditos.android.data.scan.ScanResolution
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.core.network.ApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanCardEvent {
    data class OpenReview(val scanId: String) : ScanCardEvent
    data class OpenReceipt(val prefill: AddTransactionPrefill) : ScanCardEvent
    data class Message(val text: String) : ScanCardEvent
}

/** Home's scan lines: the coordinator's cards, and opening one (iOS `DashboardView.openScan`). */
@HiltViewModel
class ScanCardsViewModel @Inject constructor(
    private val coordinator: ScanCoordinator,
) : ViewModel() {
    val cards: StateFlow<List<ScanCardState>> = coordinator.cards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while a scan is being read; Home polls only then. */
    val hasProcessing: StateFlow<Boolean> = coordinator.state
        .map { state -> state.scans.any { it.state == ScanStatus.PROCESSING && it.scanId !in state.dismissed } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = Channel<ScanCardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        coordinator.start()
    }

    /** Refreshes now, then every few seconds while a scan is processing. Cancel the calling coroutine to stop. */
    suspend fun pollUntilIdle() = coordinator.pollUntilIdle()

    /**
     * Open a ready scan from its card: a single receipt goes to the prefilled add form, anything else to the review
     * list. A scan that cannot be opened (already added, failed, offline) says so and the list is re-read.
     */
    fun open(scanId: String) {
        viewModelScope.launch {
            try {
                when (val resolution = coordinator.resolve(scanId)) {
                    is ScanResolution.Receipt -> {
                        coordinator.completeInBackground(resolution.scanId, ScanOutcome.SAVED)
                        _events.send(ScanCardEvent.OpenReceipt(resolution.item.toPrefill()))
                    }
                    is ScanResolution.Review -> _events.send(ScanCardEvent.OpenReview(scanId))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val text = when (error) {
                    is ScanFailure -> error.userMessage
                    is ApiError.Network -> "You're offline. Reconnect to open this scan."
                    else -> "Couldn't open this scan. Try again."
                }
                _events.send(ScanCardEvent.Message(text))
                coordinator.refresh()
            }
        }
    }
}
