package com.spenditos.android.feature.recurring

import androidx.compose.runtime.saveable.SaverScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round-trips the sheet draft savers through save/restore, as a recreation would. */
class RecurringSaversTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <Original, Saveable : Any> roundTrip(
        saver: androidx.compose.runtime.saveable.Saver<Original, Saveable>,
        value: Original,
    ): Original? = saver.restore(saver.run { with(scope) { save(value) } }!!)

    @Test
    fun `recurring draft round-trips with a category and a date`() {
        val draft = RecurringDraft(
            description = "Netflix",
            amountText = "15,99",
            amountVaries = true,
            frequency = "semi-monthly",
            isIncome = false,
            categoryId = "food",
            firstOccurrenceDate = LocalDate.of(2027, 1, 31),
            isActive = false,
            repeatIntervalText = "3",
            occurrencesRemainingText = "6",
            secondOccurrenceDayText = "15",
        )

        assertEquals(draft, roundTrip(RecurringDraftSaver, draft))
    }

    @Test
    fun `recurring draft round-trips without a category`() {
        val draft = RecurringDraft(categoryId = null)

        val restored = roundTrip(RecurringDraftSaver, draft)

        assertEquals(draft, restored)
        assertNull(restored!!.categoryId)
    }

    @Test
    fun `year month round-trips`() {
        val month = java.time.YearMonth.of(2026, 11)

        assertEquals(month, roundTrip(YearMonthSaver, month))
    }

    // Editor (#403): switching an active item off pauses it.

    private val editNow = java.time.Instant.parse("2026-10-26T12:00:00.500Z")

    @Test
    fun `switching an active item off also sends pausedAt`() {
        val request = RecurringDraft(description = "Gym", amountText = "45", categoryId = "c", isActive = false)
            .toUpdateRequest(wasActive = true, now = editNow)
        assertEquals(kotlinx.serialization.json.JsonPrimitive("2026-10-26T12:00:00Z"), request.pausedAt)
        assertEquals(false, request.isActive)
    }

    @Test
    fun `editing an already paused item keeps its original pausedAt and switching on sends none`() {
        val stillOff = RecurringDraft(description = "Gym", amountText = "45", categoryId = "c", isActive = false)
            .toUpdateRequest(wasActive = false, now = editNow)
        assertNull(stillOff.pausedAt)
        val on = RecurringDraft(description = "Gym", amountText = "45", categoryId = "c", isActive = true)
            .toUpdateRequest(wasActive = false, now = editNow)
        assertNull(on.pausedAt)
        val editedActive = RecurringDraft(description = "Gym", amountText = "45", categoryId = "c", isActive = true)
            .toUpdateRequest(wasActive = true, now = editNow)
        assertNull(editedActive.pausedAt)
    }
}
