package com.spenditos.android.feature.capture

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Round-trips the review editor's date saver through save/restore, as a recreation would. */
class CaptureLocalDateSaverTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <Original, Saveable : Any> roundTrip(saver: Saver<Original, Saveable>, value: Original): Original? =
        saver.restore(saver.run { with(scope) { save(value) } }!!)

    @Test
    fun `local date round-trips as an epoch day`() {
        val date = LocalDate.of(2026, 12, 31)

        assertEquals(date, roundTrip(LocalDateSaver, date))
    }
}
