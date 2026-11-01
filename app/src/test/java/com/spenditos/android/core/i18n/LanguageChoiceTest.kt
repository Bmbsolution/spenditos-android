package com.spenditos.android.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
    @Test
    fun `locale tags map to a choice`() {
        assertEquals(LanguageChoice.DEVICE, LanguageChoice.fromLocaleTags(""))
        assertEquals(LanguageChoice.DEVICE, LanguageChoice.fromLocaleTags(null))
        assertEquals(LanguageChoice.FRENCH, LanguageChoice.fromLocaleTags("fr"))
        assertEquals(LanguageChoice.FRENCH, LanguageChoice.fromLocaleTags("fr-CA,en"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromLocaleTags("en-GB"))
        assertEquals(LanguageChoice.DEVICE, LanguageChoice.fromLocaleTags("de"))
    }

    @Test
    fun `choices expose the tag handed to the locale api`() {
        assertEquals("", LanguageChoice.DEVICE.localeTags)
        assertEquals("en", LanguageChoice.ENGLISH.localeTags)
        assertEquals("fr", LanguageChoice.FRENCH.localeTags)
    }

    @Test
    fun `native names are never translated`() {
        assertEquals(null, LanguageChoice.DEVICE.nativeName)
        assertEquals("English", LanguageChoice.ENGLISH.nativeName)
        assertEquals("Français", LanguageChoice.FRENCH.nativeName)
    }

    @Test
    fun `effective language is the choice or the device's supported language`() {
        assertEquals("fr", LanguageChoice.FRENCH.effectiveLanguage(listOf("en-US")))
        assertEquals("en", LanguageChoice.ENGLISH.effectiveLanguage(listOf("fr-FR")))
        assertEquals("fr", LanguageChoice.DEVICE.effectiveLanguage(listOf("fr-CA", "en-US")))
        assertEquals("en", LanguageChoice.DEVICE.effectiveLanguage(listOf("de-DE", "en-GB")))
        assertEquals("fr", LanguageChoice.DEVICE.effectiveLanguage(listOf("de-DE", "fr-FR")))
        assertEquals("en", LanguageChoice.DEVICE.effectiveLanguage(listOf("ja-JP")))
        assertEquals("en", LanguageChoice.DEVICE.effectiveLanguage(emptyList()))
    }
}
