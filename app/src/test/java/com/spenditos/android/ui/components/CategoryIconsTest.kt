package com.spenditos.android.ui.components

import androidx.test.core.app.ApplicationProvider
import androidx.core.content.res.ResourcesCompat
import com.spenditos.android.ui.components.phosphor.PhosphorCategoryIconCatalog as Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Mirrors `resolveCategoryIconName` in packages/types: emoji, then legacy name, then name keyword, then tag. */
@RunWith(RobolectricTestRunner::class)
class CategoryIconsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun storedEmojiWinsOverTheCategoryName() {
        assertEquals("credit-card", CategoryIcons.resolveName("💳", "Childcare"))
        assertEquals("coins", CategoryIcons.resolveName("💰", "Anything"))
    }

    @Test
    fun emojiVariationSelectorsAreIgnoredWhenComparing() {
        // The catalog stores "🏷️" (with U+FE0F); a stored value without it must hit the same entry, and vice versa.
        assertEquals("tag", CategoryIcons.resolveName("🏷️"))
        assertEquals("tag", CategoryIcons.resolveName("🏷"))
        assertEquals("house-line", CategoryIcons.resolveName("home"))
    }

    @Test
    fun legacySfSymbolAndSemanticNamesResolve() {
        assertEquals("shopping-cart", CategoryIcons.resolveName("cart.fill"))
        assertEquals("gas-pump", CategoryIcons.resolveName("fuelpump"))
        assertEquals("money", CategoryIcons.resolveName("banknote"))
        assertEquals("lightning", CategoryIcons.resolveName("bolt.fill", "Whatever"))
        assertEquals("game-controller", CategoryIcons.resolveName(" gamecontroller ", null))
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        assertEquals("baby-carriage", CategoryIcons.resolveName(null, "Childcare"))
        assertEquals("credit-card", CategoryIcons.resolveName(null, "Credit card"))
        assertEquals("car", CategoryIcons.resolveName(null, "Car"))
        assertEquals("barbell", CategoryIcons.resolveName("", "Fitness"))
        assertEquals("fork-knife", CategoryIcons.resolveName(null, "Food & Drink"))
        assertEquals("house-line", CategoryIcons.resolveName(null, "Housing"))
        assertEquals("heartbeat", CategoryIcons.resolveName(null, "Health"))
        assertEquals("wifi-high", CategoryIcons.resolveName(null, "Phone & Internet"))
        assertEquals("money", CategoryIcons.resolveName(null, "Paycheck"))
        assertEquals("paw-print", CategoryIcons.resolveName(null, "Pets"))
        assertEquals("arrows-clockwise", CategoryIcons.resolveName(null, "Subscriptions"))
    }

    @Test
    fun unknownIconsAndNamesFallBackToTheTag() {
        assertEquals("tag", CategoryIcons.resolveName(null, "Something else"))
        assertEquals("tag", CategoryIcons.resolveName("not-an-icon", null))
        assertEquals("tag", CategoryIcons.resolveName(null, null))
        assertEquals("tag", CategoryIcons.resolveName("   ", "  "))
        // "Carpool" must not match the "car" keyword.
        assertEquals("tag", CategoryIcons.resolveName(null, "Carpool"))
    }

    @Test
    fun unknownStoredIconStillUsesTheCategoryNameKeyword() {
        assertEquals("baby-carriage", CategoryIcons.resolveName("not-an-icon", "Childcare"))
    }

    @Test
    fun everyEmojiInTheCatalogResolvesToAnExistingDrawable() {
        assertTrue(Catalog.emoji.size > 300)
        val resources = context.resources
        for ((emoji, expected) in Catalog.emoji) {
            val name = CategoryIcons.resolveName(emoji)
            // Duplicate-after-stripping emoji may legitimately resolve to the first catalog entry of that glyph.
            assertTrue("$emoji -> $name", name in CategoryIcons.allNames)
            for (weight in CategoryIconWeight.values()) {
                val id = CategoryIcons.drawableRes(emoji, null, weight)
                assertNotEquals("$emoji $weight", 0, id)
                val suffix = if (weight == CategoryIconWeight.Duotone) "_duotone" else ""
                assertEquals("ph_" + name.replace('-', '_') + suffix, resources.getResourceEntryName(id))
            }
            assertTrue("$emoji maps to unknown icon $expected", expected in CategoryIcons.allNames)
        }
    }

    @Test
    fun everyIconParsesAsAVectorDrawableInBothWeights() {
        assertEquals(252, CategoryIcons.allNames.size)
        for (name in CategoryIcons.allNames) {
            for (weight in CategoryIconWeight.values()) {
                val drawable = ResourcesCompat.getDrawable(context.resources, CategoryIcons.drawableForName(name, weight), null)
                assertNotNull("$name $weight", drawable)
            }
        }
    }

    @Test
    fun everyLegacyAndKeywordTargetHasADrawable() {
        for (name in Catalog.legacy.values) assertTrue(name, name in CategoryIcons.allNames)
        for (keyword in Catalog.keywords) assertTrue(keyword.icon, keyword.icon in CategoryIcons.allNames)
        assertTrue(Catalog.DEFAULT_ICON_NAME in CategoryIcons.allNames)
    }

    @Test
    fun unknownPhosphorNameFallsBackToTheDefaultDrawable() {
        assertEquals(CategoryIcons.drawableForName("tag"), CategoryIcons.drawableForName("does-not-exist"))
    }
}
