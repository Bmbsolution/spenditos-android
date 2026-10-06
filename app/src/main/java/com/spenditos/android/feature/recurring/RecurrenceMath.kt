package com.spenditos.android.feature.recurring

import com.spenditos.android.data.model.RecurringTemplate
import java.time.LocalDate
import java.time.YearMonth

/** Calendar-only occurrence calculation matching `packages/functions/src/jobs/utils.ts:69-222`. */
object RecurrenceMath {
    fun occurrencesInMonth(template: RecurringTemplate, month: YearMonth): List<LocalDate> {
        val first = runCatching { LocalDate.parse(template.firstOccurrenceDate) }.getOrNull() ?: return emptyList()
        val rangeStart = month.atDay(1)
        val rangeEnd = month.atEndOfMonth()
        if (first.isAfter(rangeEnd)) return emptyList()
        val interval = (template.repeatInterval ?: 1).coerceAtLeast(1)
        val dates = when (template.frequency) {
            "daily" -> stepDates(first, rangeStart, rangeEnd, interval.toLong(), 10_000)
            "weekly" -> stepDates(first, rangeStart, rangeEnd, 7L * interval, 5_200)
            "bi-weekly" -> stepDates(first, rangeStart, rangeEnd, 14L * interval, 5_200)
            "monthly" -> monthDates(first, rangeStart, rangeEnd, interval, 1_200)
            "quarterly" -> monthDates(first, rangeStart, rangeEnd, 3 * interval, 1_200)
            "semi-annually" -> monthDates(first, rangeStart, rangeEnd, 6 * interval, 1_200)
            "yearly" -> yearDates(first, rangeStart, rangeEnd, interval, 100)
            "semi-monthly" -> semiMonthlyDates(template, first, month, interval)
            else -> emptyList()
        }
        val skipped = template.skippedDates.toSet()
        return dates.filterNot { it.toString() in skipped }
    }

    private fun stepDates(
        first: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        stepDays: Long,
        maxSteps: Int,
    ): List<LocalDate> {
        var date = first
        val result = mutableListOf<LocalDate>()
        var steps = 0
        while (!date.isAfter(rangeEnd) && steps < maxSteps) {
            if (!date.isBefore(rangeStart)) result += date
            date = date.plusDays(stepDays)
            steps++
        }
        return result
    }

    private fun monthDates(
        first: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        stepMonths: Int,
        maxSteps: Int,
    ): List<LocalDate> {
        var date = first
        val result = mutableListOf<LocalDate>()
        var steps = 0
        while (!date.isAfter(rangeEnd) && steps < maxSteps) {
            if (!date.isBefore(rangeStart)) result += date
            date = date.plusMonths(stepMonths.toLong())
            steps++
        }
        return result
    }

    private fun yearDates(
        first: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        stepYears: Int,
        maxSteps: Int,
    ): List<LocalDate> {
        var date = first
        val result = mutableListOf<LocalDate>()
        var steps = 0
        while (!date.isAfter(rangeEnd) && steps < maxSteps) {
            if (!date.isBefore(rangeStart)) result += date
            date = date.plusYears(stepYears.toLong())
            steps++
        }
        return result
    }

    private fun semiMonthlyDates(
        template: RecurringTemplate,
        first: LocalDate,
        month: YearMonth,
        interval: Int,
    ): List<LocalDate> {
        val firstMonth = YearMonth.from(first)
        val monthDistance = (month.year - firstMonth.year) * 12 + month.monthValue - firstMonth.monthValue
        if (monthDistance < 0 || monthDistance % interval != 0) return emptyList()

        val firstDay = first.dayOfMonth
        val secondDay = template.secondOccurrenceDay ?: (firstDay + 14).let { if (it > 30) 31 else it }
        return setOf(firstDay, secondDay)
            .map { day -> month.atDay(day.coerceIn(1, month.lengthOfMonth())) }
            .filterNot { it.isBefore(first) }
            .sorted()
    }
}
