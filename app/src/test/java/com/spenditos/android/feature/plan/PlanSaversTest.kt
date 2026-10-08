package com.spenditos.android.feature.plan

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round-trips the plan sheet savers through save/restore, as a recreation would. */
class PlanSaversTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <Original, Saveable : Any> roundTrip(saver: Saver<Original, Saveable>, value: Original): Original? =
        saver.restore(saver.run { with(scope) { save(value) } }!!)

    @Test
    fun `local date round-trips as an epoch day`() {
        val date = LocalDate.of(2026, 2, 28)

        assertEquals(date, roundTrip(LocalDateSaver, date))
    }

    @Test
    fun `confirm action round-trips and null restores as no dialog`() {
        assertEquals(ConfirmAction.APPLY, roundTrip(ConfirmActionSaver, ConfirmAction.APPLY))
        assertEquals(ConfirmAction.DISCARD, roundTrip(ConfirmActionSaver, ConfirmAction.DISCARD))
        assertNull(roundTrip(ConfirmActionSaver, null))
    }
}
