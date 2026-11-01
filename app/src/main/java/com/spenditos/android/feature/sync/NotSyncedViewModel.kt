package com.spenditos.android.feature.sync

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueuedItemUi(
    val id: String,
    val amount: Double,
    val description: String,
    val date: String,
    val isFailed: Boolean,
    val canRetry: Boolean,
    val message: String?,
)

data class NotSyncedUiState(
    val items: List<QueuedItemUi> = emptyList(),
    val currency: String = "USD",
    /** Set when a discard was refused (the change is being sent) or failed; cleared by [dismissNotice]. */
    val notice: String? = null,
)

/** iOS `OfflineQueueView`: review, retry, or discard queued creates. */
@HiltViewModel
class NotSyncedViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    settings: UserSettingsActions,
) : ViewModel() {
    private val notices = MutableStateFlow<String?>(null)

    val state: StateFlow<NotSyncedUiState> = combine(
        transactions.observeQueue(),
        settings.currency(),
        notices,
    ) { queue, currency, notice ->
        NotSyncedUiState(
            items = queue.map { row ->
                val item = SpenditosJson.decodeFromString(BulkTransactionItem.serializer(), row.payloadJson)
                QueuedItemUi(
                    id = row.id,
                    amount = item.amount,
                    description = item.description,
                    date = item.date,
                    isFailed = row.status == MutationStatus.FAILED,
                    canRetry = row.status == MutationStatus.FAILED,
                    message = row.failureMessage,
                )
            },
            currency = currency,
            notice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotSyncedUiState())

    fun retry(id: String) = viewModelScope.launch { transactions.retry(id) }

    fun discard(id: String) = viewModelScope.launch {
        val notice = try {
            if (transactions.discard(id)) null else IN_FLIGHT_NOTICE
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Strings.get(R.string.sync_couldn_t_discard_this_change)
        }
        if (notice != null) notices.value = notice
    }

    fun dismissNotice() {
        notices.value = null
    }

    private companion object {
        val IN_FLIGHT_NOTICE: String get() = Strings.get(R.string.sync_this_change_is_being_sent)
    }
}
