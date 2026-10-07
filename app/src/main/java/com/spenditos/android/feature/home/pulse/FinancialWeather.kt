package com.spenditos.android.feature.home.pulse

import androidx.compose.ui.graphics.Color
import com.spenditos.android.feature.home.CashFlowStatus

/**
 * The hero's mood, ported from iOS `FinancialWeather` and `StatusTheme`. Colors are deep enough that
 * white text keeps its contrast (iOS #336 copy: one meaning per label, income states never say "budget").
 */
enum class FinancialWeather(
    val title: String,
    val shortTitle: String,
    val glyph: String,
    /** Three stops of the iOS 3x3 mesh (top-left, centre, bottom-right) for a diagonal gradient. */
    val gradient: List<Color>,
    /** A luminous accent for the orb liquid and the card glow. */
    val glow: Color,
) {
    SUNNY("On track", "On track", "☀", listOf(Color(0xFF0A5A45), Color(0xFF1A9A72), Color(0xFF27A88A)), Color(0xFF3DDC97)),
    CLOUDY("Watch your pace", "Watch your pace", "☁", listOf(Color(0xFF74400C), Color(0xFFA9681F), Color(0xFFB57524)), Color(0xFFF5B649)),
    RAINY("Getting tight", "Getting tight", "☂", listOf(Color(0xFF7E2A14), Color(0xFFB94B24), Color(0xFFD96535)), Color(0xFFFF8A57)),
    STORM("Spending more than you earn", "Overspending", "⚡", listOf(Color(0xFF4E1232), Color(0xFF8E1F40), Color(0xFFAB2A46)), Color(0xFFFF5470)),
    SETUP("Add your income", "Add your income", "✦", listOf(Color(0xFF7C3328), Color(0xFFC9623F), Color(0xFFE2884C)), Color(0xFFFFB36B));

    companion object {
        /** iOS `pulseWeather` for the current month. */
        fun from(state: CashFlowStatus.State): FinancialWeather = when (state) {
            CashFlowStatus.State.ON_TRACK -> SUNNY
            CashFlowStatus.State.WATCH -> CLOUDY
            CashFlowStatus.State.TIGHT -> RAINY
            CashFlowStatus.State.OVER -> STORM
            CashFlowStatus.State.SETUP -> SETUP
        }
    }
}
