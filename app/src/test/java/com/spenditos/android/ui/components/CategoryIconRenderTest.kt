package com.spenditos.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** A single tint must draw the primary layer opaque and the Duotone secondary layer at 20% alpha. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryIconRenderTest {
    @get:Rule val rule = createComposeRule()

    /**
     * Effective tint coverage per pixel, recovered from the green channel: the capture is opaque (red over a
     * light surface), so green = background * (1 - coverage). Returns a histogram of rounded coverage percentages.
     */
    private fun coverage(weight: CategoryIconWeight): Map<Int, Int> {
        rule.setContent {
            // coins: the duotone secondary layer is the filled back coin.
            CategoryIcon("💰", null, tint = Color.Red, weight = weight, modifier = Modifier.size(128.dp).testTag("icon"))
        }
        val bitmap = rule.onNodeWithTag("icon").captureToImage().asAndroidBitmap()
        var background = 0
        val histogram = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val argb = bitmap.getPixel(x, y)
            if (x == 0 && y == 0) background = (argb shr 8) and 0xFF
            val green = (argb shr 8) and 0xFF
            val percent = ((1.0 - green.toDouble() / background) * 100).toInt()
            histogram.merge(percent, 1, Int::plus)
        }
        return histogram
    }

    private fun Map<Int, Int>.pixelsBetween(low: Int, high: Int) = filterKeys { it in low..high }.values.sum()

    @Test
    fun duotoneDrawsTheSecondaryLayerAtTwentyPercentOfTheTint() {
        val c = coverage(CategoryIconWeight.Duotone)
        assertTrue("opaque primary layer present: $c", c.pixelsBetween(98, 100) > 200)
        assertTrue("20% secondary layer present: $c", c.pixelsBetween(18, 22) > 200)
    }

    @Test
    fun regularHasNoSecondaryLayer() {
        val c = coverage(CategoryIconWeight.Regular)
        assertTrue("opaque primary layer present: $c", c.pixelsBetween(98, 100) > 200)
        // Only antialiased edge pixels may land near 20%.
        assertTrue("no flat 20% layer: $c", c.pixelsBetween(18, 22) < 150)
    }
}
