package com.spenditos.android.feature.home.monthbalance

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.monthbalance.MonthBalanceChoice
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthBalanceViewModel` tests (MonthBalanceTests "View model" and "Expiry"). */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthBalanceTrackerTest {
    private val service = FakeMonthBalanceActions()
    private var online = true
    private val tracker = MonthBalanceTracker(service) { online }

    private val october = "2026-10"
    private val state get() = tracker.state.value

    @Test
    fun `a shortfall shows the prompt and carries nothing`() = runTest {
        tracker.load(october)
        assertNotNull(state.cardFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertEquals(listOf(october), service.getMonths)
    }

    @Test
    fun `a surplus carries nothing until chosen`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.surplus)
        tracker.load(october)
        assertNotNull(state.cardFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
    }

    @Test
    fun `carry over saves for the shown month and adds the surplus`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.surplus)
        service.setResult = Result.success(FakeMonthBalanceActions.carried)
        tracker.load(october)
        tracker.answer(october, MonthBalanceChoice.CARRIED)
        assertEquals(listOf(october to MonthBalanceChoice.CARRIED), service.setCalls)
        assertNull(state.cardFor(october))
        assertEquals(150.0, state.carriedInFor(october), 0.0)
        assertFalse(state.isSaving)
    }

    @Test
    fun `not needed hides the card and carries nothing`() = runTest {
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedShortfall)
        tracker.load(october)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertNull(state.cardFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
    }

    @Test
    fun `a failed answer keeps the card and explains without carrying anything`() = runTest {
        online = false
        service.setResult = Result.failure(ApiError.Network(IOException("Offline")))
        tracker.load(october)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertNotNull(state.cardFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertEquals("You're offline. Connect to the internet to answer this.", state.error)
        assertFalse(state.isSaving)
        // Trying again clears the old message first.
        online = true
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedShortfall)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertNull(state.error)
        assertNull(state.cardFor(october))
    }

    @Test
    fun `a failed answer while online says it could not reach the server`() = runTest {
        service.setResult = Result.failure(ApiError.Network(IOException("timeout")))
        tracker.load(october)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertEquals("Can't reach Spenditos right now. Check your connection and try again.", state.error)
        assertNotNull(state.cardFor(october))
    }

    @Test
    fun `an answer before the card loaded is ignored`() = runTest {
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertTrue(service.setCalls.isEmpty())
    }

    @Test
    fun `a failed load hides the card instead of blocking the screen`() = runTest {
        tracker.load(october)
        assertNotNull(state.cardFor(october))
        service.getResult = Result.failure(ApiError.NoData)
        tracker.load(october)
        assertNull(state.cardFor(october))
        assertNull(state.dataFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
    }

    @Test
    fun `answering twice is the same request and the same state`() = runTest {
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedShortfall)
        tracker.load(october)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        val first = state.loaded
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        assertEquals(2, service.setCalls.size)
        assertEquals(service.setCalls[0], service.setCalls[1])
        assertEquals(first, state.loaded)
        assertNull(state.cardFor(october))
    }

    @Test
    fun `a double tap while a save is running sends one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.setGate = { gate.await() }
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedShortfall)
        tracker.load(october)
        val first = launch { tracker.answer(october, MonthBalanceChoice.DISMISSED) }
        runCurrent()
        assertTrue(state.isSaving)
        tracker.answer(october, MonthBalanceChoice.DISMISSED)
        gate.complete(Unit)
        first.join()
        assertEquals(1, service.setCalls.size)
    }

    @Test
    fun `a newer load wins over a slower older one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.getGate = { gate.await() }
        val older = launch { tracker.load(october) }
        runCurrent()
        service.getGate = null
        service.getResult = Result.success(FakeMonthBalanceActions.surplus)
        tracker.load(october)
        gate.complete(Unit)
        older.join()
        assertEquals(com.spenditos.android.data.monthbalance.MonthBalanceKind.SURPLUS, state.loaded!!.kind)
    }

    // region Remove, re-add and the follow-up row (owner follow-up to #395)

    private val offlineFailure = Result.failure<com.spenditos.android.data.monthbalance.MonthBalanceData>(ApiError.Network(IOException("Offline")))

    @Test
    fun `remove shows at once and is saved as dismissed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        service.setGate = { gate.await() }
        tracker.load(october)
        assertEquals(150.0, state.carriedInFor(october), 0.0)

        val removing = launch { tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false) }
        runCurrent()
        // The save has not finished, yet left after bills no longer includes the carry-over.
        assertTrue(state.isSaving)
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertNotNull(state.followUpFor(october))
        assertNull("the card does not come back", state.cardFor(october))

        gate.complete(Unit)
        removing.join()
        assertEquals(listOf(october to MonthBalanceChoice.DISMISSED), service.setCalls)
        assertEquals(FakeMonthBalanceActions.dismissedSurplus, state.loaded)
        assertFalse(state.isSaving)
        assertNull(state.error)
    }

    @Test
    fun `a failed remove rolls back, tells why and leaves the card state alone`() = runTest {
        online = false
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        service.setResult = offlineFailure
        tracker.load(october)

        val outcome = tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false)

        assertEquals(AnswerOutcome.Failed("You're offline. Connect to the internet to answer this."), outcome)
        assertEquals(150.0, state.carriedInFor(october), 0.0)
        assertEquals(FakeMonthBalanceActions.carried, state.loaded)
        assertNull(state.followUpFor(october))
        assertNull("no stale error is kept for a later card", state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun `carry over again adds the surplus back once it is saved`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        service.setResult = Result.success(FakeMonthBalanceActions.carried)
        tracker.load(october)
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertNotNull(state.followUpFor(october))

        assertEquals(AnswerOutcome.Saved, tracker.answer(october, MonthBalanceChoice.CARRIED, errorOnCard = false))

        assertEquals(listOf(october to MonthBalanceChoice.CARRIED), service.setCalls)
        assertEquals(150.0, state.carriedInFor(october), 0.0)
        assertNull(state.followUpFor(october))
    }

    @Test
    fun `a failed carry over again keeps the follow-up row and says why`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        service.setResult = Result.failure(ApiError.Network(IOException("timeout")))
        tracker.load(october)

        val outcome = tracker.answer(october, MonthBalanceChoice.CARRIED, errorOnCard = false)

        assertTrue(outcome is AnswerOutcome.Failed)
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertNotNull(state.followUpFor(october))
        assertNull(state.error)
    }

    @Test
    fun `remove then carry over again is two idempotent answers, last one wins`() = runTest {
        service.setUpdatesGet = true
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        tracker.load(october)
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false)
        service.setResult = Result.success(FakeMonthBalanceActions.carried)
        tracker.answer(october, MonthBalanceChoice.CARRIED, errorOnCard = false)
        assertEquals(listOf(october to MonthBalanceChoice.DISMISSED, october to MonthBalanceChoice.CARRIED), service.setCalls)
        assertEquals(150.0, state.carriedInFor(october), 0.0)
    }

    @Test
    fun `a read that started during the save does not undo it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.setUpdatesGet = true
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        tracker.load(october)
        service.setGate = { gate.await() }
        val removing = launch { tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false) }
        runCurrent()
        tracker.load(october) // answers with the OLD carried state: the server has not applied the PUT yet
        assertEquals(FakeMonthBalanceActions.carried, state.loaded)
        gate.complete(Unit)
        removing.join()
        assertEquals(FakeMonthBalanceActions.dismissedSurplus, state.loaded)
        assertEquals(0.0, state.carriedInFor(october), 0.0)
    }

    @Test
    fun `a double tap on remove sends one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        service.setResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        service.setGate = { gate.await() }
        tracker.load(october)
        val first = launch { tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false) }
        runCurrent()
        assertEquals(AnswerOutcome.Ignored, tracker.answer(october, MonthBalanceChoice.DISMISSED, optimistic = true, errorOnCard = false))
        gate.complete(Unit)
        first.join()
        assertEquals(1, service.setCalls.size)
    }

    @Test
    fun `the follow-up row follows the spec`() = runTest {
        suspend fun followUp(data: com.spenditos.android.data.monthbalance.MonthBalanceData): Boolean {
            service.getResult = Result.success(data)
            tracker.load(data.month)
            return state.followUpFor(data.month) != null
        }
        val s = FakeMonthBalanceActions
        assertTrue("surplus said no thanks / removed", followUp(s.dismissedSurplus))
        assertTrue("shortfall said not needed", followUp(s.dismissedShortfall))
        assertFalse("shortfall that already has a debt", followUp(s.dismissedShortfall.copy(shortfallDebtId = "debt-1")))
        assertFalse("unanswered (the card is showing)", followUp(s.surplus))
        assertFalse("unanswered shortfall", followUp(s.shortfall))
        assertFalse("unanswered shortfall that got a debt", followUp(s.shortfall.copy(shortfallDebtId = "debt-1", showPrompt = false)))
        assertFalse("carried", followUp(s.carried))
        assertFalse("nothing to ask", followUp(s.dismissedSurplus.copy(kind = com.spenditos.android.data.monthbalance.MonthBalanceKind.NONE)))
    }

    @Test
    fun `the follow-up row expires with its month`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.dismissedSurplus)
        tracker.load(october)
        assertNotNull(state.followUpFor("2026-10"))
        assertNull(state.followUpFor("2026-11"))
    }

    // endregion

    // region Expiry: the card lives until it is answered or the month ends

    @Test
    fun `an unanswered card expires when the local month rolls over and is never shown again`() = runTest {
        tracker.load(october) // loaded at 31 Oct 23:30 local
        assertNotNull(state.cardFor("2026-10"))
        // 1 Nov 00:30 local: the key is now 2026-11 and October's card is gone, with no reload.
        assertNull(state.cardFor("2026-11"))
        assertNull(state.dataFor("2026-11"))
        assertEquals(0.0, state.carriedInFor("2026-11"), 0.0)
        // The new month evaluates its own previous month.
        service.getResult = Result.success(FakeMonthBalanceActions.chained)
        tracker.load("2026-11")
        assertEquals(listOf("2026-10", "2026-11"), service.getMonths)
        assertEquals("2026-10", state.cardFor("2026-11")!!.data.previousMonth)
    }

    @Test
    fun `an expired card cannot be answered or carried`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.carried)
        service.setResult = Result.success(FakeMonthBalanceActions.carried)
        tracker.load(october)
        assertEquals(150.0, state.carriedInFor("2026-10"), 0.0)
        assertEquals(0.0, state.carriedInFor("2026-11"), 0.0)
        tracker.answer("2026-11", MonthBalanceChoice.DISMISSED)
        assertTrue(service.setCalls.isEmpty())
    }

    @Test
    fun `the same month keeps its card at the very end of the month`() = runTest {
        tracker.load(october)
        assertNotNull(state.cardFor("2026-10")) // 31 Oct 23:59 is still October
    }

    // endregion

    @Test
    fun `pay it this month sends the day and name for the shown month and hides the card`() = runTest {
        val paying = FakeMonthBalanceActions.shortfall.copy(choice = MonthBalanceChoice.PAYING, showPrompt = false)
        service.payResult = Result.success(paying)
        tracker.load(october)
        val outcome = tracker.pay(october, "2026-10-15", "September shortfall")
        assertEquals(AnswerOutcome.Saved, outcome)
        assertEquals(listOf(Triple(october, "2026-10-15", "September shortfall")), service.payCalls)
        assertTrue(service.setCalls.isEmpty())
        assertNull(state.cardFor(october))
        assertNull(state.followUpFor(october))
        assertEquals(0.0, state.carriedInFor(october), 0.0)
        assertFalse(state.isSaving)
    }

    @Test
    fun `pay it this month hides the card at once and brings it back when the save fails`() = runTest {
        val gate = CompletableDeferred<Unit>()
        service.setGate = { gate.await() }
        tracker.load(october)
        val job = launch { tracker.pay(october, "2026-10-15", "September shortfall") }
        runCurrent()
        assertNull(state.cardFor(october))
        assertTrue(state.isSaving)
        gate.complete(Unit)
        job.join()
        assertNotNull(state.cardFor(october))
        assertNotNull(state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun `pay it this month is ignored for a surplus`() = runTest {
        service.getResult = Result.success(FakeMonthBalanceActions.surplus)
        tracker.load(october)
        assertEquals(AnswerOutcome.Ignored, tracker.pay(october, "2026-10-15", "x"))
        assertTrue(service.payCalls.isEmpty())
    }
}
