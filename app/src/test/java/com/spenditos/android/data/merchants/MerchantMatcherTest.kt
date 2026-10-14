package com.spenditos.android.data.merchants

import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantMatcherTest {
    private fun rule(id: String, pattern: String, category: String? = "dining", action: RuleAction = RuleAction.CATEGORIZE, source: String = "user", regex: Boolean = false, clean: String = pattern) =
        MerchantMapping(id, "u", pattern, clean, category, source = source, isRegex = regex, action = action.wire)

    @Test
    fun `the merchant key matches the contract`() {
        assertEquals("timhortons4521", normalizeMerchantKey("TIM HORTONS #4521"))
        assertEquals("timhortons", normalizeMerchantKey("Tim Hortons"))
        assertEquals("cafédu", normalizeMerchantKey("Café du!"))
        assertEquals("timhortons", normalizeMerchantKey("  Tim-Hortons!! "))
        assertEquals("disney", normalizeMerchantKey("Disney+"))
        assertEquals("paymentthankyou", normalizeMerchantKey("PAYMENT – THANK YOU"))
        assertEquals("", normalizeMerchantKey("  #-- "))
        assertEquals("", normalizeMerchantKey(null))
        assertEquals("", normalizeMerchantKey(""))
    }

    @Test
    fun `ask for a named merchant with no rule`() {
        assertTrue(MerchantMatcher.shouldOfferRule("Tim Hortons", "dining", emptyList()))
    }

    @Test
    fun `no ask when an identical user rule exists, whatever the case or punctuation`() {
        val rules = listOf(rule("r1", "TIM HORTONS"))
        assertFalse(MerchantMatcher.shouldOfferRule("tim hortons!", "dining", rules))
        assertEquals("r1", MerchantMatcher.identicalRule("Tim Hortons", "dining", rules)?.id)
    }

    @Test
    fun `matches the clean name too`() {
        val rules = listOf(rule("r1", "SQ *TIMS", clean = "Tim Hortons"))
        assertEquals("r1", MerchantMatcher.identicalRule("Tim Hortons", "dining", rules)?.id)
    }

    @Test
    fun `ask when the existing rule files it elsewhere or skips`() {
        assertTrue(MerchantMatcher.shouldOfferRule("Tim Hortons", "dining", listOf(rule("r1", "Tim Hortons", category = "coffee"))))
        assertTrue(MerchantMatcher.shouldOfferRule("Tim Hortons", "dining", listOf(rule("r1", "Tim Hortons", category = null, action = RuleAction.SKIP))))
    }

    @Test
    fun `no ask for an empty merchant or category`() {
        assertFalse(MerchantMatcher.shouldOfferRule(null, "dining", emptyList()))
        assertFalse(MerchantMatcher.shouldOfferRule("   ", "dining", emptyList()))
        assertFalse(MerchantMatcher.shouldOfferRule("###", "dining", emptyList()))
        assertFalse(MerchantMatcher.shouldOfferRule("Tim", null, emptyList()))
        assertFalse(MerchantMatcher.shouldOfferRule("Tim", "", emptyList()))
    }

    @Test
    fun `only user rules that are not patterns count as identical`() {
        assertNull(MerchantMatcher.identicalRule("Tim Hortons", "dining", listOf(rule("r1", "Tim Hortons", source = "ai"))))
        assertNull(MerchantMatcher.identicalRule("Tim Hortons", "dining", listOf(rule("r1", "Tim Hortons", source = "system"))))
        assertNull(MerchantMatcher.identicalRule("Tim Hortons", "dining", listOf(rule("r1", "Tim Hortons", regex = true))))
    }

    @Test
    fun `a skip rule is identical to a skip, and ruleFor finds the row an upsert would update`() {
        val skip = rule("r2", "Payment", category = null, action = RuleAction.SKIP)
        assertEquals("r2", MerchantMatcher.identicalRule("payment", null, listOf(skip), RuleAction.SKIP)?.id)
        assertNull(MerchantMatcher.identicalRule("payment", "dining", listOf(skip)))
        assertEquals("r2", MerchantMatcher.ruleFor("PAYMENT", listOf(skip))?.id)
        assertNull(MerchantMatcher.ruleFor("PAYMENT", listOf(rule("r3", "Payment", source = "ai"))))
        assertNull(MerchantMatcher.ruleFor("", listOf(skip)))
    }
}
