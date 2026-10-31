package com.spenditos.android.feature.home.pulse

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.compose.ui.graphics.Color
import com.spenditos.android.feature.home.CashFlowStatus

/**
 * The hero's mood, ported from iOS `FinancialWeather` and `StatusTheme`. Colors are deep enough that
 * white text keeps its contrast (iOS #336 copy: one meaning per label, income states never say "budget").
 */
enum class FinancialWeather(
    @androidx.annotation.StringRes private val titleRes: Int,
    @androidx.annotation.StringRes private val shortTitleRes: Int,
    val glyph: String,
    /** Three stops of the iOS 3x3 mesh (top-left, centre, bottom-right) for a diagonal gradient. */
    val gradient: List<Color>,
    /** A luminous accent for the orb liquid and the card glow. */
    val glow: Color,
) {
    SUNNY(R.string.common_on_track, R.string.common_on_track, "☀", listOf(Color(0xFF0A5A45), Color(0xFF1A9A72), Color(0xFF27A88A)), Color(0xFF3DDC97)),
    CLOUDY(R.string.common_watch_your_pace, R.string.common_watch_your_pace, "☁", listOf(Color(0xFF74400C), Color(0xFFA9681F), Color(0xFFB57524)), Color(0xFFF5B649)),
    RAINY(R.string.common_getting_tight, R.string.common_getting_tight, "☂", listOf(Color(0xFF7E2A14), Color(0xFFB94B24), Color(0xFFD96535)), Color(0xFFFF8A57)),
    STORM(R.string.home_spending_more_than_you_earn, R.string.home_overspending, "⚡", listOf(Color(0xFF4E1232), Color(0xFF8E1F40), Color(0xFFAB2A46)), Color(0xFFFF5470)),
    SETUP(R.string.home_add_your_income, R.string.home_add_your_income, "✦", listOf(Color(0xFF7C3328), Color(0xFFC9623F), Color(0xFFE2884C)), Color(0xFFFFB36B));

    val title: String get() = Strings.get(titleRes)
    val shortTitle: String get() = Strings.get(shortTitleRes)

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
