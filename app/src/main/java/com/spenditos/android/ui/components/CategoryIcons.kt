package com.spenditos.android.ui.components

/**
 * Port of iOS `IconMapper.resolve`: category icons are emoji on the wire; legacy rows may hold an SF Symbol
 * name instead, which maps to an emoji. Anything else falls back to the tag.
 */
object CategoryIcons {
    const val DEFAULT = "🏷️"

    private val legacy = mapOf(
        "banknote" to "💰", "banknote.fill" to "💰", "dollarsign.circle" to "💰", "dollarsign.circle.fill" to "💰",
        "briefcase" to "💼", "briefcase.fill" to "💼",
        "house" to "🏠", "house.fill" to "🏠", "bolt" to "⚡", "bolt.fill" to "⚡", "globe" to "🌐", "wifi" to "🌐",
        "cart" to "🛒", "cart.fill" to "🛒", "fork.knife" to "🍴", "cup.and.saucer" to "☕", "cup.and.saucer.fill" to "☕", "pizza" to "🍕",
        "bus" to "🚌", "bus.fill" to "🚌", "fuelpump" to "⛽", "fuelpump.fill" to "⛽", "car" to "🚗", "car.fill" to "🚗",
        "wrench.and.screwdriver" to "🔧", "wrench.and.screwdriver.fill" to "🔧",
        "tv" to "📺", "tv.fill" to "📺", "play.tv" to "📺", "gamecontroller" to "🎮", "gamecontroller.fill" to "🎮",
        "creditcard" to "💳", "creditcard.fill" to "💳", "piggybank" to "🐷", "piggybank.fill" to "🐷",
        "heart" to "❤️", "heart.fill" to "❤️", "star" to "⭐", "star.fill" to "⭐", "flame" to "🔥", "flame.fill" to "🔥",
        "tag" to "🏷️", "tag.fill" to "🏷️", "sparkles" to "✨",
    )

    fun resolve(icon: String?): String {
        val value = icon?.trim().orEmpty()
        if (value.isEmpty()) return DEFAULT
        if (value.any { it.code > 127 }) return value
        return legacy[value] ?: DEFAULT
    }
}
