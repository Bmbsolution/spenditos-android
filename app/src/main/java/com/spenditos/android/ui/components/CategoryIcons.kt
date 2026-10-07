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

    /**
     * Port of iOS `IconMapper.sfSymbolName`: the monochrome SF Symbol iOS shows for a category (the API keeps
     * the emoji). Resolve it to a Material icon with [SfSymbols.icon], or call [symbol] directly.
     */
    fun sfSymbolName(icon: String?, categoryName: String? = null, group: String? = null): String {
        val raw = icon?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            if (raw.all { it.code < 128 } && raw.contains('.')) return raw
            emojiToSymbol[raw]?.let { return it }
        }
        val name = categoryName?.lowercase().orEmpty()
        // Whole words, so "car" does not match Childcare or Credit card.
        val tokens = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        fun has(vararg words: String) = words.any { word ->
            val parts = word.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            when {
                parts.isEmpty() -> false
                parts.size == 1 -> parts[0] in tokens
                else -> name.contains(word)
            }
        }
        return when {
            has("internet", "wi-fi", "wifi") -> "globe"
            has("developer", "code", "apps") -> "chevron.left.forwardslash.chevron.right"
            has("paycheck", "salary", "money", "cash") -> "banknote"
            has("income", "freelance", "work") -> "briefcase"
            has("childcare", "daycare", "baby") -> "figure.and.child.holdinghands"
            has("credit", "card", "debt") -> "creditcard"
            has("fitness", "gym") -> "figure.strengthtraining.traditional"
            has("home", "housing", "rent") -> "house"
            has("grocery", "groceries", "shopping") -> "cart"
            has("transport", "car", "travel") -> "car"
            has("food", "meal", "restaurant") -> "fork.knife"
            has("coffee", "drink") -> "cup.and.saucer"
            has("health", "doctor", "medicine") -> "heart"
            has("game", "entertainment", "movie") -> "gamecontroller"
            has("book", "school", "education") -> "book"
            has("pet", "nature", "garden") -> "leaf"
            else -> when (group) {
                "finance" -> "dollarsign.circle"
                "technology" -> "laptopcomputer"
                "food" -> "fork.knife"
                "home" -> "house"
                "transport" -> "car"
                "shopping" -> "bag"
                "health" -> "heart"
                "entertainment" -> "play.rectangle"
                "people" -> "person.2"
                "nature" -> "leaf"
                "symbols" -> "sparkles"
                else -> "tag"
            }
        }
    }

    fun symbol(icon: String?, categoryName: String? = null, group: String? = null) =
        SfSymbols.icon(sfSymbolName(icon, categoryName, group))

    private val emojiToSymbol = mapOf(
        "💰" to "banknote",
        "💳" to "creditcard",
        "🏦" to "building.columns",
        "💵" to "banknote",
        "🪙" to "circle.grid.2x1",
        "📊" to "chart.bar",
        "📈" to "chart.line.uptrend.xyaxis",
        "📉" to "chart.line.downtrend.xyaxis",
        "🧾" to "doc.text",
        "🏷️" to "tag",
        "🎁" to "gift",
        "💼" to "briefcase",
        "🏧" to "building.columns",
        "💎" to "diamond",
        "🌐" to "globe",
        "📶" to "wifi",
        "🧑‍💻" to "chevron.left.forwardslash.chevron.right",
        "💻" to "laptopcomputer",
        "🖥️" to "desktopcomputer",
        "⌨️" to "keyboard",
        "📡" to "antenna.radiowaves.left.and.right",
        "📁" to "folder",
        "🖨️" to "printer",
        "🧰" to "wrench.and.screwdriver",
        "🍔" to "fork.knife",
        "🍕" to "fork.knife",
        "🍣" to "fork.knife",
        "🍜" to "fork.knife",
        "🍲" to "fork.knife",
        "🥗" to "leaf",
        "🍎" to "apple.logo",
        "🥕" to "carrot",
        "🛒" to "cart",
        "🧺" to "basket",
        "☕" to "cup.and.saucer",
        "🧋" to "cup.and.saucer",
        "🍪" to "circle.grid.3x3",
        "🍰" to "birthday.cake",
        "🍷" to "wineglass",
        "🥤" to "cup.and.saucer",
        "🏠" to "house",
        "🏢" to "building.2",
        "🔑" to "key",
        "💡" to "lightbulb",
        "🔌" to "powerplug",
        "🛋️" to "sofa",
        "🛏️" to "bed.double",
        "🚿" to "shower",
        "🧹" to "broom",
        "🧴" to "bottle",
        "🛁" to "bathtub",
        "🪴" to "leaf",
        "🔨" to "hammer",
        "📺" to "tv",
        "🚗" to "car",
        "⛽" to "fuelpump",
        "🔧" to "wrench.and.screwdriver",
        "🚌" to "bus",
        "🚆" to "train.side.front.car",
        "🚇" to "tram",
        "🚕" to "taxi",
        "🚲" to "bicycle",
        "🛴" to "scooter",
        "✈️" to "airplane",
        "🚢" to "ferry",
        "🅿️" to "parkingsign",
        "🧳" to "suitcase",
        "🏕️" to "tent",
        "🛍️" to "bag",
        "👕" to "tshirt",
        "👟" to "shoe",
        "👗" to "figure.dress.line.vertical.figure",
        "💄" to "paintbrush",
        "💍" to "circle.dashed",
        "🎒" to "backpack",
        "📦" to "shippingbox",
        "📱" to "iphone",
        "🪑" to "chair",
        "❤️" to "heart",
        "💊" to "pills",
        "🏥" to "cross.case",
        "🩺" to "stethoscope",
        "🩹" to "bandage",
        "🦷" to "mouth",
        "👓" to "eyeglasses",
        "🏋️" to "figure.strengthtraining.traditional",
        "🏃" to "figure.run",
        "🧘" to "figure.mind.and.body",
        "🧠" to "brain",
        "💪" to "figure.strengthtraining.traditional",
        "🚑" to "cross.case",
        "🧼" to "hands.sparkles",
        "🎮" to "gamecontroller",
        "🎬" to "film",
        "🎵" to "music.note",
        "🎧" to "headphones",
        "🎤" to "mic",
        "🎭" to "theatermasks",
        "🎨" to "paintpalette",
        "🎲" to "dice",
        "🎯" to "target",
        "🎳" to "figure.bowling",
        "🎟️" to "ticket",
        "📸" to "camera",
        "📚" to "books.vertical",
        "👨‍👩‍👧‍👦" to "person.3",
        "👥" to "person.2",
        "👶" to "figure.and.child.holdinghands",
        "🤝" to "hands.clap",
        "🧑‍🏫" to "person.crop.rectangle.stack",
        "🎓" to "graduationcap",
        "✏️" to "pencil",
        "💌" to "envelope",
        "💬" to "bubble.left",
        "🍼" to "waterbottle",
        "🧑‍🤝‍🧑" to "person.2",
        "🫶" to "heart",
        "🐾" to "pawprint",
        "🐕" to "dog",
        "🐈" to "cat",
        "🐦" to "bird",
        "🐟" to "fish",
        "🌿" to "leaf",
        "🌸" to "camera.macro",
        "🌳" to "tree",
        "🌊" to "water.waves",
        "🏖️" to "beach.umbrella",
        "⛰️" to "mountain.2",
        "☀️" to "sun.max",
        "🌱" to "sprout",
        "⭐" to "star",
        "✨" to "sparkles",
        "🌟" to "star.circle",
        "🔥" to "flame",
        "⚡" to "bolt",
        "💧" to "drop",
        "🎉" to "party.popper",
        "🎈" to "balloon",
        "🛡️" to "shield",
        "☂️" to "umbrella",
        "🔒" to "lock",
        "✅" to "checkmark.seal",
        "❓" to "questionmark",
    )
}
