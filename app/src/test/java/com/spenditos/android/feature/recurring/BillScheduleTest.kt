package com.spenditos.android.feature.recurring

import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.BillLifecycle
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

/** Mirrors the iOS `RecurringTemplateListViewModelTests` for #403: lifecycle, schedule, summary and Generate. */
class BillScheduleTest {
    private val savedLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    @After
    fun tearDown() = Locale.setDefault(savedLocale)

    private val today = LocalDate.of(2026, 10, 26)
    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val isIncome: (RecurringTemplate) -> Boolean = { it.categoryId == "cat-pay" }

    private fun template(
        id: String,
        amount: Double = 100.0,
        category: String = "cat-subs",
        frequency: String = "monthly",
        first: String = "2026-10-02",
        active: Boolean = true,
        legacy: Boolean = false,
        remaining: Int? = null,
        pausedAt: String? = null,
        endedAt: String? = null,
        skipped: List<String> = emptyList(),
        updatedAt: String? = null,
    ) = RecurringTemplate(
        id = id, userId = "u", categoryId = category, description = id, amount = amount,
        type = if (legacy) "variable" else "fixed", frequency = frequency, firstOccurrenceDate = first,
        occurrencesRemaining = remaining, isActive = active, pausedAt = pausedAt, endedAt = endedAt,
        skippedDates = skipped, updatedAt = updatedAt,
    )

    private fun entry(templateId: String, date: String, amount: Double = 100.0) = Transaction(
        id = "$templateId-$date", userId = "u", categoryId = "cat-subs", templateId = templateId,
        description = templateId, amount = amount, date = date, source = "recurring",
    )

    private fun dates(template: RecurringTemplate, month: YearMonth) =
        BillSchedule.dates(template, month).map { it.toString() }

    // MARK: Paused vs Ended

    @Test
    fun `lifecycle tells Paused from Ended`() {
        assertEquals(BillLifecycle.ACTIVE, template("a").lifecycle)
        assertEquals(BillLifecycle.ENDED, template("b", active = false, endedAt = "2026-10-20T12:00:00.000Z").lifecycle)
        // A finished N-payment bill from before endedAt existed.
        assertEquals(BillLifecycle.ENDED, template("c", active = false, remaining = 0).lifecycle)
        assertEquals(BillLifecycle.PAUSED, template("d", active = false, remaining = 0, pausedAt = "2026-10-01T00:00:00Z").lifecycle)
        // Old switched-off bills without any marker stay Paused (visible).
        assertEquals(BillLifecycle.PAUSED, template("e", active = false).lifecycle)
    }

    @Test
    fun `lists split active paused and ended and keep debt bills out`() {
        val categories = listOf(
            com.spenditos.android.data.model.Category("cat-subs", "u", "Bills", "#B85F42", isIncome = false),
            com.spenditos.android.data.model.Category("cat-pay", "u", "Salary", "#4E8F5A", isIncome = true),
        )
        val state = IncomeAndBillsUiState(
            isLoading = false,
            categories = categories,
            templates = listOf(
                template("rent"),
                template("pay", category = "cat-pay"),
                template("gym", active = false, pausedAt = "2026-09-12T00:00:00Z"),
                template("klarna", active = false, remaining = 0),
                template("car", active = false, endedAt = "2026-10-20T12:00:00.000Z"),
                template("car-payment", active = false, endedAt = "2026-10-21T12:00:00.000Z").copy(debtId = "debt-car"),
                template("debt-bill").copy(debtId = "debt-1"),
                template("debt-paused", active = false, pausedAt = "2026-09-12T00:00:00Z").copy(debtId = "debt-2"),
            ),
        )

        assertEquals(listOf("rent"), state.billTemplates.map { it.id })
        assertEquals(listOf("pay"), state.incomeTemplates.map { it.id })
        assertEquals(listOf("gym"), state.pausedTemplates.map { it.id })
        assertEquals(setOf("klarna", "car"), state.endedTemplates.map { it.id }.toSet())
        assertEquals("newest end first", "car", state.endedTemplates.first().id)
    }

    // MARK: Schedule

    @Test
    fun `schedule dates honor frequency and skipped dates`() {
        val october = YearMonth.of(2026, 10)
        val biweekly = template("klarna", frequency = "bi-weekly", first = "2026-09-30", skipped = listOf("2026-10-28"))
        assertEquals(listOf("2026-10-14"), dates(biweekly, october))
        assertEquals(listOf("2026-10-14", "2026-10-28"), dates(biweekly.copy(skippedDates = emptyList()), october))
    }

    @Test
    fun `month-end anchors follow the backend generator`() {
        // The backend steps month by month from the previous date (Jan 31, Feb 28, Mar 28), so the
        // preview must too: it is what generation will actually create. iOS steps from the first date.
        val monthEnd = template("rent", first = "2026-01-31")
        assertEquals(listOf("2026-02-28"), dates(monthEnd, YearMonth.of(2026, 2)))
        assertEquals(listOf("2026-03-28"), dates(monthEnd, YearMonth.of(2026, 3)))
        assertEquals(listOf("2026-10-28"), dates(monthEnd, YearMonth.of(2026, 10)))
    }

    @Test
    fun `next date looks into the next month and is strictly after the day`() {
        val bell = template("bell", first = "2026-10-03")
        assertEquals(LocalDate.of(2026, 11, 3), BillSchedule.nextDate(bell, today))
        // A payment due today is not "next" after today, but is after yesterday.
        val phone = template("phone", first = "2026-10-26")
        assertEquals(LocalDate.of(2026, 11, 26), BillSchedule.nextDate(phone, today))
        assertEquals(LocalDate.of(2026, 10, 26), BillSchedule.nextDate(phone, today.minusDays(1)))
        // Skipped dates are never "next".
        assertEquals(LocalDate.of(2026, 12, 3), BillSchedule.nextDate(bell.copy(skippedDates = listOf("2026-11-03")), today))
        // Past the year-end too.
        assertEquals(LocalDate.of(2027, 1, 3), BillSchedule.nextDate(bell, LocalDate.of(2026, 12, 20)))
    }

    // MARK: Month summary

    @Test
    fun `month summary counts paid and still to pay`() {
        val templates = listOf(
            template("rent", amount = 1500.0, first = "2026-10-01"),
            template("phone", amount = 60.0, first = "2026-10-28"),
            template("pay", amount = 4000.0, category = "cat-pay", first = "2026-10-15"),
            template("gym", amount = 40.0, active = false, pausedAt = "2026-09-12T00:00:00Z"),
        )
        val entries = listOf(
            entry("rent", "2026-10-01", 1500.0),
            entry("phone", "2026-10-28", 60.0), // generated ahead: not paid yet
        )

        val summary = billsMonthSummary(templates, isIncome, entries, today)!!

        assertEquals("income and paused bills don't count", 2, summary.billCount)
        assertEquals(1560.0, summary.billsTotal, 0.0)
        assertEquals(1, summary.paidCount)
        assertEquals(60.0, summary.stillToPay!!, 0.0)
        assertEquals("phone", summary.nextName)
        assertEquals(LocalDate.of(2026, 10, 28), summary.nextDate)
        assertEquals("$60.00 still to pay", BillText.summaryHeadline(summary, "USD"))
        assertEquals("1 of 2 bills paid · Next: phone, Oct 28", BillText.summaryDetail(summary))
    }

    @Test
    fun `month summary without entries leaves paid out`() {
        val templates = listOf(template("rent", amount = 1500.0, first = "2026-10-01"))
        val summary = billsMonthSummary(templates, isIncome, null, today)!!

        assertNull(summary.paidCount)
        assertNull(summary.stillToPay)
        assertEquals(1500.0, summary.billsTotal, 0.0)
        assertEquals("$1,500.00 in bills", BillText.summaryHeadline(summary, "USD"))
        assertEquals("1 bill · Next: rent, Nov 1", BillText.summaryDetail(summary))
    }

    @Test
    fun `month summary says all bills paid when nothing is left and is null without bills`() {
        val templates = listOf(template("rent", amount = 1500.0, first = "2026-10-01"))
        val paid = billsMonthSummary(templates, isIncome, listOf(entry("rent", "2026-10-01", 1500.0)), today)!!
        assertEquals(0.0, paid.stillToPay!!, 0.0)
        assertEquals("All bills paid", BillText.summaryHeadline(paid, "USD"))

        assertNull(billsMonthSummary(listOf(template("pay", category = "cat-pay")), isIncome, emptyList(), today))
        assertNull(billsMonthSummary(listOf(template("old", legacy = true)), isIncome, emptyList(), today))
        assertNull(billsMonthSummary(emptyList(), isIncome, null, today))
    }

    @Test
    fun `entries of other templates and future dates are not paid`() {
        val templates = listOf(template("rent", amount = 100.0, first = "2026-10-01"))
        val summary = billsMonthSummary(
            templates,
            isIncome,
            listOf(entry("other", "2026-10-01"), entry("rent", "2026-10-27")),
            today,
        )!!
        assertEquals(0, summary.paidCount)
        assertEquals(100.0, summary.stillToPay!!, 0.0)
    }

    // MARK: Row wording

    @Test
    fun `row status reads Next, Last payment, Paused since and Paused months`() {
        val zone = ZoneOffset.UTC
        assertEquals("Next Nov 3", BillText.rowStatus(template("bell", first = "2026-10-03"), now, zone, Locale.US))
        assertEquals("Last payment Nov 3", BillText.rowStatus(template("bell", first = "2026-10-03", remaining = 1), now, zone, Locale.US))
        // Due today still reads "Next".
        assertEquals("Next Oct 26", BillText.rowStatus(template("phone", first = "2026-10-26"), now, zone, Locale.US))
        assertNull(BillText.rowStatus(template("old", legacy = true), now, zone, Locale.US))

        val paused = template("gym", active = false, pausedAt = "2026-09-12T10:00:00Z")
        assertEquals("Paused since Sep 12", BillText.rowStatus(paused, now, zone, Locale.US))
        assertFalse(BillText.isLongPaused(paused, now, zone))
        assertEquals("Paused", BillText.rowStatus(template("gym2", active = false), now, zone, Locale.US))

        val longPaused = template("gym3", active = false, pausedAt = "2026-07-20T10:00:00Z")
        assertTrue(BillText.isLongPaused(longPaused, now, zone))
        assertEquals("Paused 3 months", BillText.rowStatus(longPaused, now, zone, Locale.US))

        assertEquals("Ended", BillText.rowStatus(template("done", active = false, endedAt = "2026-10-20T12:00:00Z"), now, zone, Locale.US))
    }

    @Test
    fun `a bill is long paused at 90 days and not at 89`() {
        val zone = ZoneOffset.UTC
        val pausedAt = "2026-07-28T12:00:00Z"
        val bill = template("gym", active = false, pausedAt = pausedAt)
        assertFalse(BillText.isLongPaused(bill, Instant.parse("2026-10-25T12:00:00Z"), zone)) // 89 days
        assertTrue(BillText.isLongPaused(bill, Instant.parse("2026-10-26T12:00:00Z"), zone)) // 90 days
        // An active or ended bill is never "long paused".
        assertFalse(BillText.isLongPaused(template("a"), now, zone))
    }

    // MARK: Generate

    @Test
    fun `generate items skip legacy paused and ended`() {
        val templates = listOf(
            template("rent", amount = 1450.0),
            template("groceries-estimate", amount = 400.0, legacy = true),
            template("paused-bill", active = false, pausedAt = "2026-09-01T00:00:00Z"),
            template("ended-bill", active = false, endedAt = "2026-09-01T00:00:00Z"),
        )

        val items = generateItems(templates, isIncome, YearMonth.of(2026, 10), emptyList())

        assertEquals(listOf("rent"), items.map { it.id })
        assertEquals(1450.0, items.first().amountToLog, 0.0)
    }

    @Test
    fun `generate items mark what is already logged`() {
        val templates = listOf(
            template("rent", first = "2026-10-01"),
            template("weekly", amount = 20.0, frequency = "weekly", first = "2026-10-05"),
        )
        val logged = listOf(entry("rent", "2026-10-01"), entry("weekly", "2026-10-05", 20.0))

        val items = generateItems(templates, isIncome, YearMonth.of(2026, 10), logged)
        val rent = items.first { it.id == "rent" }
        val weekly = items.first { it.id == "weekly" }

        assertTrue(rent.isFullyLogged)
        assertEquals(4, weekly.dates.size)
        assertEquals(1, weekly.loggedCount)
        assertEquals(60.0, weekly.amountToLog, 0.0)
        assertEquals("Oct 5, 12, 19 +1 · 1 of 4 logged", BillText.generateDates(weekly))
        assertEquals("Oct 1", BillText.generateDates(rent.copy(loggedCount = 0)))
    }

    @Test
    fun `generate items can be limited to one bill and are sorted by first date`() {
        val rent = template("rent", first = "2026-10-20")
        val phone = template("phone", first = "2026-10-03")
        val items = generateItems(listOf(rent, phone), isIncome, YearMonth.of(2026, 10), emptyList())
        assertEquals(listOf("phone", "rent"), items.map { it.id })
        assertEquals(listOf("rent"), generateItems(listOf(rent, phone), isIncome, YearMonth.of(2026, 10), emptyList(), only = rent).map { it.id })
        // A month with nothing falling in it yields no rows.
        assertTrue(generateItems(listOf(rent), isIncome, YearMonth.of(2026, 9), emptyList()).isEmpty())
    }

    @Test
    fun `net line sums income minus bills for the selected rows`() {
        val items = generateItems(
            listOf(
                template("pay", amount = 6500.0, category = "cat-pay", first = "2026-10-01"),
                template("rent", amount = 180.0, first = "2026-10-02"),
            ),
            isIncome,
            YearMonth.of(2026, 10),
            emptyList(),
        )
        assertEquals("Net +$6,320.00 for October", BillText.net(items, YearMonth.of(2026, 10), "USD"))
        assertEquals("Net −$180.00 for October", BillText.net(items.filter { !it.isIncome }, YearMonth.of(2026, 10), "USD"))
    }

    // MARK: Ended archive wording

    @Test
    fun `ended subtitle and span read like the archive rows`() {
        val zone = ZoneOffset.UTC
        val ended = template("car", active = false, endedAt = "2026-10-20T14:00:00.000Z")
        val history = BillHistorySummary(count = 12, total = 1378.32, firstDate = "2026-01-05", lastDate = "2026-10-05")

        assertEquals("Ended Oct 2026", BillText.endedSubtitle(ended, null, false, "USD", zone, Locale.US))
        assertEquals("Ended Oct 2026 · 12 payments · $1,378.32", BillText.endedSubtitle(ended, history, false, "USD", zone, Locale.US))
        assertEquals("Ended Oct 2026 · 1 payment received · $20.00", BillText.endedSubtitle(ended, BillHistorySummary(1, 20.0), true, "USD", zone, Locale.US))
        assertEquals("Ended Oct 2026 · 12 payments received · $1,378.32", BillText.endedSubtitle(ended, history, true, "USD", zone, Locale.US))
        assertEquals("Ended Oct 2026", BillText.endedSubtitle(ended, BillHistorySummary(), false, "USD", zone, Locale.US))
        assertEquals("Jan – Oct 2026", BillText.endedSpan(history, Locale.US))
        assertEquals("Sep 2025 – Oct 2026", BillText.endedSpan(history.copy(firstDate = "2025-09-30"), Locale.US))
        assertNull(BillText.endedSpan(BillHistorySummary(), Locale.US))
    }

    @Test
    fun `ended date falls back to updatedAt and groups by year`() {
        val zone = ZoneOffset.UTC
        val withEndedAt = template("a", active = false, endedAt = "2026-10-20T14:00:00.000Z", updatedAt = "2025-01-01T00:00:00Z")
        val instalment = template("klarna", active = false, remaining = 0, updatedAt = "2025-08-26T09:00:00Z")
        val unknown = template("mystery", active = false, remaining = 0)
        assertEquals(2026, endedYear(withEndedAt, zone))
        assertEquals(2025, endedYear(instalment, zone))
        assertEquals(0, endedYear(unknown, zone))
        assertEquals("Ended in 2026", BillText.endedGroupTitle(2026))
        assertEquals("Ended", BillText.endedGroupTitle(0))
        assertEquals("End Bell internet?", BillText.endDialogTitle(template("x").copy(description = "Bell internet")))
        assertEquals("End this bill?", BillText.endDialogTitle(null))
    }

    @Test
    fun `counted pluralizes`() {
        assertEquals("1 bill", counted(1, "bill"))
        assertEquals("9 bills", counted(9, "bill"))
        assertEquals("1 entry", counted(1, "entry", "entries"))
        assertEquals("0 entries", counted(0, "entry", "entries"))
    }
}
