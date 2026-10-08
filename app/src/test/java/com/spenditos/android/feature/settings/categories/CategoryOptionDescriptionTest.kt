package com.spenditos.android.feature.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOptionDescriptionTest {
    @Test
    fun `icon options speak their library name`() {
        assertEquals("Groceries", iconOptionDescription("🛒"))
        assertEquals("Tag", iconOptionDescription("🏷️"))
    }

    @Test
    fun `unknown and none icon options fall back to a readable label`() {
        assertEquals("No icon", iconOptionDescription("None"))
        assertEquals("💠", iconOptionDescription("💠"))
    }

    @Test
    fun `color options speak their hex without the hash`() {
        assertEquals("Color B85F42", colorOptionDescription("#B85F42"))
        assertEquals("Color 3F7D70", colorOptionDescription("3F7D70"))
    }
}
