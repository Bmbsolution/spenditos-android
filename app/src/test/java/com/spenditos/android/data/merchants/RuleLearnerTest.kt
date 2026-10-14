package com.spenditos.android.data.merchants

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.testing.FakeMerchantMappings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLearnerTest {
    private val fake = FakeMerchantMappings()
    private val learner = RuleLearner(fake)

    @Test
    fun `always upserts, applies and writes the contract toast`() = runTest {
        fake.applyResult = 7
        val outcome = learner.always("Tim Hortons", "dining", "Dining", alsoUpdatePast = true)
        assertNotNull(outcome.rule)
        assertEquals(7, outcome.pastUpdated)
        assertFalse(outcome.failed)
        assertEquals(
            "Rule saved: Tim Hortons → Dining. 7 past transactions updated. Change it anytime in Settings › Rules.",
            outcome.message,
        )
        assertEquals(listOf(Triple("Tim Hortons", "dining", true)), fake.alwaysCalls)
    }

    @Test
    fun `always without the checkbox does not apply`() = runTest {
        val outcome = learner.always("Tim Hortons", "dining", "Dining", alsoUpdatePast = false)
        assertTrue(fake.applies.isEmpty())
        assertNull(outcome.pastUpdated)
        assertEquals("Rule saved: Tim Hortons → Dining. Change it anytime in Settings › Rules.", outcome.message)
    }

    @Test
    fun `rule failures are light and never throw`() = runTest {
        fake.failUpsert = ApiError.Network(java.io.IOException("offline"))
        val outcome = learner.always("Tim Hortons", "dining", "Dining", alsoUpdatePast = true)
        assertTrue(outcome.failed)
        assertNull(outcome.rule)
        assertEquals("Couldn't save the rule. Your transactions will still be saved.", outcome.message)

        fake.failUpsert = null
        fake.failApply = ApiError.Server(500, "boom")
        val partial = learner.always("Tim Hortons", "dining", "Dining", alsoUpdatePast = true)
        assertFalse(partial.failed)
        assertNull(partial.pastUpdated)
        assertEquals(
            "Rule saved: Tim Hortons → Dining. Past transactions couldn't be updated. Change it anytime in Settings › Rules.",
            partial.message,
        )
    }

    @Test
    fun `singular and zero past counts`() {
        assertEquals("Rule saved: A → B. 1 past transaction updated. Change it anytime in Settings › Rules.", RuleLearner.savedMessage("A", "B", 1))
        assertEquals("Rule saved: A → B. Change it anytime in Settings › Rules.", RuleLearner.savedMessage("A", "B", 0))
        assertEquals("Rule saved: A → B. Change it anytime in Settings › Rules.", RuleLearner.savedMessage("A", "B", null))
    }
}
