package com.spenditos.android.feature.home.monthbalance

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `MonthBalanceTests` "Wording" cases. */
class MonthBalanceCopyTest {
    private val money = { value: Double -> "$" + value.toLong() }

    @Test
    fun `the card says what the spec says`() {
        val shortfall = FakeMonthBalanceActions.shortfall
        val surplus = FakeMonthBalanceActions.surplus
        assertEquals("September ended \$200 short.", MonthBalanceCopy.headline(shortfall, money))
        assertEquals("Track it as a debt?", MonthBalanceCopy.question(shortfall))
        assertEquals("September ended \$150 ahead.", MonthBalanceCopy.headline(surplus, money))
        assertEquals("Include it in October?", MonthBalanceCopy.question(surplus))
    }

    @Test
    fun `the card says when last month included what it carried in`() {
        val chained = FakeMonthBalanceActions.chained
        assertEquals("October ended \$250 ahead.", MonthBalanceCopy.headline(chained, money))
        assertEquals("Includes \$150 you carried into October.", MonthBalanceCopy.note(chained, money))
        assertNull(MonthBalanceCopy.note(FakeMonthBalanceActions.surplus, money))
    }

    @Test
    fun `nothing is said for kind none`() {
        val none = FakeMonthBalanceActions.surplus.copy(kind = com.spenditos.android.data.monthbalance.MonthBalanceKind.NONE)
        assertNull(MonthBalanceCopy.headline(none, money))
        assertNull(MonthBalanceCopy.question(none))
        assertNull(MonthBalanceCopy.shortfallPrefill(none))
    }

    @Test
    fun `the follow-up lines say what was left out`() {
        assertEquals("Not included: September ended \$150 ahead", MonthBalanceCopy.followUpLine(FakeMonthBalanceActions.dismissedSurplus, money))
        assertEquals("Not tracked: September ended \$200 short", MonthBalanceCopy.followUpLine(FakeMonthBalanceActions.dismissedShortfall, money))
        assertEquals("Not included: October ended \$250 ahead", MonthBalanceCopy.followUpLine(FakeMonthBalanceActions.chained.copy(choice = com.spenditos.android.data.monthbalance.MonthBalanceChoice.DISMISSED), money))
        assertNull(MonthBalanceCopy.followUpLine(FakeMonthBalanceActions.surplus.copy(kind = com.spenditos.android.data.monthbalance.MonthBalanceKind.NONE), money))
        assertEquals("Remove", MonthBalanceCopy.REMOVE)
        assertEquals("Remove carried-in amount", MonthBalanceCopy.REMOVE_DESCRIPTION)
    }

    @Test
    fun `january names december and month names ignore the time zone`() {
        assertEquals("December", MonthBalanceCopy.monthName("2026-12"))
        assertEquals("December", MonthBalanceCopy.previousMonthName(LocalDate.of(2027, 1, 2)))
        assertEquals("September", MonthBalanceCopy.previousMonthName(LocalDate.of(2026, 10, 31)))
        assertEquals("February", MonthBalanceCopy.previousMonthName(LocalDate.of(2026, 3, 31)))
        assertEquals("not-a-month", MonthBalanceCopy.monthName("not-a-month"))
    }

    @Test
    fun `the month key is year dash zero padded month`() {
        assertEquals("2026-10", MonthBalanceCopy.monthKey(LocalDate.of(2026, 10, 31)))
        assertEquals("2026-01", MonthBalanceCopy.monthKey(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `a shortfall prefills the debt sheet`() {
        val prefill = MonthBalanceCopy.shortfallPrefill(FakeMonthBalanceActions.shortfall)!!
        assertEquals("September shortfall", prefill.name)
        assertEquals(200.0, prefill.owed, 0.0)
        assertEquals("2026-09", prefill.shortfallMonth)
        assertNull(MonthBalanceCopy.shortfallPrefill(FakeMonthBalanceActions.surplus))
    }
}
