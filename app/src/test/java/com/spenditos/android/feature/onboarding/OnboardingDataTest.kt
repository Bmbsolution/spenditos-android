package com.spenditos.android.feature.onboarding

import java.util.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDataTest {
    @Test
    fun `seed categories match all entries in the referenced iOS file`() {
        assertEquals(18, OnboardingData.categorySeeds.size)
        assertEquals(2, OnboardingData.categorySeeds.count { it.isIncome })
        assertTrue(OnboardingData.categorySeeds.all { it.color.startsWith("#") && it.icon.isNotBlank() })
    }

    @Test
    fun `every country map value is a valid ISO 4217 currency`() {
        assertTrue(OnboardingData.countryCurrencyMap.isNotEmpty())
        OnboardingData.countryCurrencyMap.forEach { (country, code) ->
            assertEquals("$country should map to its currency code", code, Currency.getInstance(code).currencyCode)
        }
    }

    @Test
    fun `countries with currencies rejected by the backend use USD`() {
        assertEquals("USD", OnboardingData.currencyForCountry("AW"))
        assertEquals("USD", OnboardingData.currencyForCountry("CW"))
        assertEquals("CAD", OnboardingData.currencyForCountry("ca"))
        assertEquals("USD", OnboardingData.currencyForCountry("ZZ"))
    }
}
