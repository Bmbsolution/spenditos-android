package com.spenditos.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.spenditos.android.ui.components.phosphor.PhosphorCategoryIconCatalog as Catalog

/** Phosphor weight used for category icons. [Duotone] draws the secondary layer at 20% alpha of the tint. */
enum class CategoryIconWeight { Regular, Duotone }

/**
 * Category icons, identical on web, iOS and Android. The API keeps storing the emoji a user picked (legacy rows
 * may hold an SF Symbol name); every client resolves it to the same Phosphor icon through the shared catalog in
 * `packages/types/src/domain/category-icons.ts`. The catalog data in [Catalog] and the drawables are generated
 * by `scripts/generate-phosphor-category-icons.py`; only the resolver below is hand-written.
 *
 * Resolution order (port of `resolveCategoryIconName`): emoji, legacy name, whole-word category-name keyword,
 * default tag.
 */
object CategoryIcons {
    private const val VARIATION_SELECTOR = "️"

    /** Emoji with variation selectors removed -> Phosphor name; the first catalog entry wins, like the TS loop. */
    private val emojiWithoutSelector: Map<String, String> by lazy {
        val out = HashMap<String, String>(Catalog.emoji.size * 2)
        for ((emoji, name) in Catalog.emoji) out.putIfAbsent(emoji.replace(VARIATION_SELECTOR, ""), name)
        out
    }

    private val nonAlphanumeric = Regex("[^a-z0-9]+")

    /** The Phosphor icon name (kebab-case, e.g. `shopping-cart`) for a stored icon value and category name. */
    fun resolveName(icon: String?, categoryName: String? = null): String {
        val raw = icon?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            (Catalog.emoji[raw] ?: Catalog.legacy[raw])?.let { return it }
            emojiWithoutSelector[raw.replace(VARIATION_SELECTOR, "")]?.let { return it }
        }
        val tokens = (categoryName ?: "").lowercase().split(nonAlphanumeric).filter { it.isNotEmpty() }.toSet()
        for (keyword in Catalog.keywords) {
            if (keyword.words.any { it in tokens }) return keyword.icon
        }
        return Catalog.DEFAULT_ICON_NAME
    }

    /** The VectorDrawable resource for a Phosphor icon name; unknown names fall back to the default tag. */
    @DrawableRes
    fun drawableForName(phosphorName: String, weight: CategoryIconWeight = CategoryIconWeight.Duotone): Int {
        val table = if (weight == CategoryIconWeight.Duotone) Catalog.duotone else Catalog.regular
        return table[phosphorName] ?: table.getValue(Catalog.DEFAULT_ICON_NAME)
    }

    /** Resolve a stored icon value and category name straight to a drawable resource. */
    @DrawableRes
    fun drawableRes(
        icon: String?,
        categoryName: String? = null,
        weight: CategoryIconWeight = CategoryIconWeight.Duotone,
    ): Int = drawableForName(resolveName(icon, categoryName), weight)

    /** Every Phosphor name the catalog can produce, for tests and tooling. */
    val allNames: Set<String> get() = Catalog.regular.keys
}

/**
 * The one component every category icon renders through. [tint] is applied as a single colour: the Duotone
 * secondary layer is a 20% alpha path in the drawable, and Compose tints with SrcIn, which keeps that alpha,
 * so one tint draws both layers (full and 20%) exactly as on web and iOS.
 */
@Composable
fun CategoryIcon(
    icon: String?,
    categoryName: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    weight: CategoryIconWeight = CategoryIconWeight.Duotone,
) {
    val resource = remember(icon, categoryName, weight) { CategoryIcons.drawableRes(icon, categoryName, weight) }
    Icon(painterResource(resource), contentDescription = null, tint = tint, modifier = modifier)
}
