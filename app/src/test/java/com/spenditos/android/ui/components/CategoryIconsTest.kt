package com.spenditos.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryIconsTest {
    @Test
    fun storedEmojiWinsOverTheCategoryName() {
        assertEquals("creditcard", CategoryIcons.sfSymbolName("💳", "Childcare"))
    }

    @Test
    fun storedSfSymbolNameIsKept() {
        assertEquals("car.fill", CategoryIcons.sfSymbolName("car.fill", "Housing"))
    }

    @Test
    fun carDoesNotMatchNamesThatOnlyContainThoseLetters() {
        assertEquals("figure.and.child.holdinghands", CategoryIcons.sfSymbolName(null, "Childcare"))
        assertEquals("creditcard", CategoryIcons.sfSymbolName(null, "Credit card"))
        assertEquals("car", CategoryIcons.sfSymbolName(null, "Car"))
    }

    @Test
    fun namesWithoutAnIconUseTheSameSymbolsAsTheIconCatalog() {
        assertEquals("figure.strengthtraining.traditional", CategoryIcons.sfSymbolName(null, "Fitness"))
        assertEquals("fork.knife", CategoryIcons.sfSymbolName(null, "Food & Drink"))
        assertEquals("house", CategoryIcons.sfSymbolName(null, "Housing"))
        assertEquals("heart", CategoryIcons.sfSymbolName(null, "Health"))
        assertEquals("globe", CategoryIcons.sfSymbolName(null, "Phone & Internet"))
        assertEquals("banknote", CategoryIcons.sfSymbolName(null, "Paycheck"))
        assertEquals("tag", CategoryIcons.sfSymbolName(null, "Something else"))
    }
}
