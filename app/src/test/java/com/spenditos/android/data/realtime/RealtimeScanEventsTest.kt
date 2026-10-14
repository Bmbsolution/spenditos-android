package com.spenditos.android.data.realtime

import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** `user.data_changed` with `aggregateType == "scan"` is the "scan finished" signal; it is not a finance aggregate. */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeScanEventsTest {
    private fun message(eventId: String, type: String, mutation: String = "updated", aggregateId: String? = "scn_0002") =
        """{"type":"user.data_changed","data":{"eventId":"$eventId","aggregateType":"$type",${aggregateId?.let { "\"aggregateId\":\"$it\"," } ?: ""}"mutationType":"$mutation","occurredAt":"2026-10-11T10:01:10.000Z","version":1}}"""

    @Test
    fun `a scan event emits the scan id once and invalidates no finance aggregate`() = runTest {
        val bus = FinanceInvalidationBus()
        val events = RealtimeEvents(bus)
        val scanIds = mutableListOf<String?>()
        val invalidated = mutableListOf<Set<Aggregate>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.scanUpdates.collect { scanIds += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidated += it } }

        events.accept(message("e1", "scan"))
        events.accept(message("e1", "scan"))
        events.accept(message("e2", "scan", aggregateId = null))
        events.accept(message("e3", "scan", mutation = "exploded"))

        assertEquals(listOf("scn_0002", null), scanIds)
        assertEquals(emptyList<Set<Aggregate>>(), invalidated)
    }

    @Test
    fun `transaction events still invalidate and do not look like scans`() = runTest {
        val bus = FinanceInvalidationBus()
        val events = RealtimeEvents(bus)
        val scanIds = mutableListOf<String?>()
        val invalidated = mutableListOf<Set<Aggregate>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.scanUpdates.collect { scanIds += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { invalidated += it } }

        events.accept(message("t1", "transaction", aggregateId = "txn_1"))

        assertEquals(emptyList<String?>(), scanIds)
        assertEquals(listOf(setOf(Aggregate.TRANSACTION)), invalidated)
    }
}
