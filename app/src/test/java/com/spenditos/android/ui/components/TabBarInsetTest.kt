package com.spenditos.android.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarInsetTest {
    @Test
    fun gestureBarUsesTheIosGapInsteadOfTheFullInset() {
        // Gesture navigation: no tappable buttons, and the system still reports a 48dp bar.
        assertEquals(IosTabBarBottomGap, tabBarBottomPadding(buttonBar = 0.dp, navigationBar = 48.dp))
    }

    @Test
    fun buttonBarStaysAboveTheButtons() {
        assertEquals(58.dp, tabBarBottomPadding(buttonBar = 48.dp, navigationBar = 48.dp))
    }

    @Test
    fun smallerGestureInsetIsNotOverrun() {
        assertEquals(16.dp, tabBarBottomPadding(buttonBar = 0.dp, navigationBar = 16.dp))
    }

    @Test
    fun noSystemBarStillFloatsLikeIos() {
        assertEquals(IosTabBarBottomGap, tabBarBottomPadding(buttonBar = 0.dp, navigationBar = 0.dp))
    }
}
