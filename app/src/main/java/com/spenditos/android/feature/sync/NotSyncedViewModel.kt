package com.spenditos.android.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class NotSyncedUiState(val items: List<QueuedItemUi> = emptyList(), val currency: String = "USD")

/** iOS `OfflineQueueView`: review, retry, or discard queued creates. */
@HiltViewModel
class NotSyncedViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    settings: UserSettingsRepository,
) : ViewModel() {
    val state: StateFlow<NotSyncedUiState> = combine(transactions.observeQueue(), settings.currency()) { queue, currency ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotSyncedUiState())

    fun retry(id: String) = viewModelScope.launch { transactions.retry(id) }

    fun discard(id: String) = viewModelScope.launch { transactions.discard(id) }
}
