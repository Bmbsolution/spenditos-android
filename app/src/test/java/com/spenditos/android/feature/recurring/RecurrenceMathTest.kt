package com.spenditos.android.feature.recurring

import com.spenditos.android.data.model.RecurringTemplate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceMathTest {
    private data class Case(val name: String, val template: RecurringTemplate, val month: YearMonth, val expected: List<String>)

    @Test
    fun `each frequency follows its calendar schedule`() {
        val cases = listOf(
            Case("daily", template("daily", "2026-01-05"), YearMonth.of(2026, 1), (5..31).map { "2026-01-${it.toString().padStart(2, '0')}" }),
            Case("weekly", template("weekly", "2026-01-05"), YearMonth.of(2026, 1), listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26")),
            Case("bi-weekly", template("bi-weekly", "2026-01-05"), YearMonth.of(2026, 1), listOf("2026-01-05", "2026-01-19")),
            Case("semi-monthly", template("semi-monthly", "2026-01-01", secondDay = 15), YearMonth.of(2026, 1), listOf("2026-01-01", "2026-01-15")),
            Case("monthly", template("monthly", "2026-01-10"), YearMonth.of(2026, 1), listOf("2026-01-10")),
            Case("quarterly", template("quarterly", "2026-01-10"), YearMonth.of(2026, 4), listOf("2026-04-10")),
            Case("semi-annually", template("semi-annually", "2026-01-10"), YearMonth.of(2026, 7), listOf("2026-07-10")),
            Case("yearly", template("yearly", "2025-02-28"), YearMonth.of(2026, 2), listOf("2026-02-28")),
        )
        cases.forEach { test ->
            assertEquals(test.name, test.expected.map(LocalDate::parse), RecurrenceMath.occurrencesInMonth(test.template, test.month))
        }
    }

    @Test
    fun `month ends clamp while iterative monthly dates keep backend alignment`() {
        val template = template("monthly", "2026-01-31")
        assertEquals(listOf(LocalDate.parse("2026-02-28")), RecurrenceMath.occurrencesInMonth(template, YearMonth.of(2026, 2)))
        assertEquals(listOf(LocalDate.parse("2026-03-28")), RecurrenceMath.occurrencesInMonth(template, YearMonth.of(2026, 3)))
        assertEquals(listOf(LocalDate.parse("2026-04-30")), RecurrenceMath.occurrencesInMonth(template("quarterly", "2026-01-31"), YearMonth.of(2026, 4)))
    }

    @Test
    fun `semi-monthly clamps and aligns repeat intervals from its first month`() {
        val schedule = template("semi-monthly", "2026-01-31", secondDay = 31)
        assertEquals(listOf(LocalDate.parse("2026-02-28")), RecurrenceMath.occurrencesInMonth(schedule, YearMonth.of(2026, 2)))
        assertEquals(emptyList<LocalDate>(), RecurrenceMath.occurrencesInMonth(schedule.copy(repeatInterval = 2), YearMonth.of(2026, 2)))
        assertEquals(listOf(LocalDate.parse("2026-03-31")), RecurrenceMath.occurrencesInMonth(schedule.copy(repeatInterval = 2), YearMonth.of(2026, 3)))
    }

    @Test
    fun `interval multiplier and skipped dates are applied`() {
        val everyTwoDays = template("daily", "2026-01-01").copy(repeatInterval = 2, skippedDates = listOf("2026-01-03"))
        assertEquals(
            listOf(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-07")),
            RecurrenceMath.occurrencesInMonth(everyTwoDays, YearMonth.of(2026, 1)).take(3),
        )
    }

    @Test
    fun `unknown frequency and malformed date return no occurrences`() {
        assertEquals(emptyList<LocalDate>(), RecurrenceMath.occurrencesInMonth(template("new-frequency", "2026-01-01"), YearMonth.of(2026, 1)))
        assertEquals(emptyList<LocalDate>(), RecurrenceMath.occurrencesInMonth(template("monthly", "bad-date"), YearMonth.of(2026, 1)))
    }

    @Test
    fun `generate items add with decimal arithmetic and leave out legacy estimates and inactive items`() {
        val monthlyA = template("monthly", "2026-01-01").copy(amount = 0.1)
        val monthlyB = template("monthly", "2026-01-01").copy(id = "other", amount = 0.2)
        val inactive = template("monthly", "2026-01-01").copy(id = "inactive", isActive = false, amount = 90.0)
        val legacy = template("monthly", "2026-01-01").copy(id = "legacy", type = "variable", amount = 90.0)
        val items = generateItems(listOf(monthlyA, monthlyB, inactive, legacy), { false }, YearMonth.of(2026, 1), emptyList())
        assertEquals(2, items.sumOf { it.dates.size })
        assertEquals(BigDecimal("0.3"), items.fold(BigDecimal.ZERO) { sum, item -> sum + BigDecimal.valueOf(item.amountToLog) })
    }

    private fun template(frequency: String, first: String, secondDay: Int? = null) = RecurringTemplate(
        id = "tmpl-fake",
        userId = "user-fake",
        categoryId = "cat-fake",
        description = "Fake schedule",
        amount = 10.0,
        type = "fixed",
        frequency = frequency,
        firstOccurrenceDate = first,
        secondOccurrenceDay = secondDay,
    )
}
