package com.spenditos.android.core.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Aggregates named like the server's `user.data_changed` events (iOS FinanceDataReconciliationCoordinator). */
enum class Aggregate { TRANSACTION, CATEGORY, RECURRING, IMPORT, SETTINGS, ENTITLEMENT, ACCOUNT }

/** In-process "this data changed, refresh it" signal. Screens subscribe to the aggregates they show. */
@Singleton
class FinanceInvalidationBus @Inject constructor() {
    private val _events = MutableSharedFlow<Set<Aggregate>>(extraBufferCapacity = 16)
    val events: SharedFlow<Set<Aggregate>> = _events.asSharedFlow()

    fun invalidate(vararg aggregates: Aggregate) {
        _events.tryEmit(aggregates.toSet())
    }
}
