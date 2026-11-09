package com.spenditos.android.feature.settings

import com.spenditos.android.data.entitlement.ProAccessState
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.PremiumPendingChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class SettingsPlanLabelPendingChangeTest {
    private fun state(period: String?, effectiveAt: String? = "2026-12-01T00:00:00.000Z", active: Boolean = true) = ProAccessState(
        legacyPremiumActive = active,
        premium = PremiumAccess(tier = "pro", active = active, pendingChange = PremiumPendingChange(period = period, effectiveAt = effectiveAt)),
    )

    private fun text(state: ProAccessState) = SettingsPlanLabel.pendingChangeText(state, Locale.US, ZoneOffset.UTC)

    @Test
    fun `names the plan and the date`() {
        assertEquals("Switches to yearly on Dec 1, 2026", text(state("P1Y")))
        assertEquals("Switches to monthly on Dec 1, 2026", text(state("P1M")))
        assertEquals("Switches to a new plan on Dec 1, 2026", text(state("P3M")))
    }

    @Test
    fun `nothing without a change, a date or Pro`() {
        assertNull(text(ProAccessState(legacyPremiumActive = true, premium = PremiumAccess(active = true))))
        assertNull(text(state("P1Y", effectiveAt = "soon")))
        assertNull(text(state("P1Y", active = false)))
    }

    @Test
    fun `it reaches the settings plan`() {
        assertEquals("Switches to yearly on Dec 1, 2026", SettingsPlanLabel.plan(state("P1Y"), Locale.US, ZoneOffset.UTC).pendingChangeText)
    }
}
