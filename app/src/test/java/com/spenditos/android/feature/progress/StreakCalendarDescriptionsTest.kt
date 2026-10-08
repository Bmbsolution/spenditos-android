package com.spenditos.android.feature.progress

import com.spenditos.android.data.model.StreakCalendarDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalendarDescriptionsTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun day(date: LocalDate, hasEntry: Boolean = false, streak: Boolean = false, freeze: Boolean = false) =
        StreakCalendarDay(date = date, hasEntry = hasEntry, isStreakDay = streak, freezeUsed = freeze)

    @Test
    fun `row summary counts active days of the last fourteen`() {
        val days = (1..14).map { offset ->
            day(today.minusDays((offset - 1).toLong()), hasEntry = offset % 2 == 0, streak = offset % 2 == 0)
        }
        assertEquals("7 of the last 14 days active", streakCalendarSummary(days))
    }

    @Test
    fun `row summary adapts to a shorter window`() {
        assertEquals("0 of the last 3 days active", streakCalendarSummary(List(3) { day(today) }))
    }

    @Test
    fun `day states cover active, freeze, today and missed`() {
        assertEquals("active", streakDayStateDescription(today.minusDays(1), active = true, freezeUsed = false, today = today))
        assertEquals("freeze", streakDayStateDescription(today.minusDays(2), active = false, freezeUsed = true, today = today))
        assertEquals("missed", streakDayStateDescription(today.minusDays(3), active = false, freezeUsed = false, today = today))
        assertEquals("today", streakDayStateDescription(today, active = false, freezeUsed = false, today = today))
        assertEquals("active today", streakDayStateDescription(today, active = true, freezeUsed = false, today = today))
    }
}
