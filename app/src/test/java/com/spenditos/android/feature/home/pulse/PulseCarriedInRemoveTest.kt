package com.spenditos.android.feature.home.pulse

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.ui.theme.SpenditosTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The "Carried in" line on the Home status card and its Remove control (owner follow-up to #395). */
@RunWith(RobolectricTestRunner::class)
class PulseCarriedInRemoveTest {
    @get:Rule val rule = createComposeRule()

    private val categories = listOf(Category("pay", "u", "Pay", "#445566", isIncome = true), Category("food", "u", "Food", "#445566", isIncome = false))
    private val today = LocalDate.of(2026, 10, 10)
    private fun status(carriedIn: Double) = CashFlowStatus.make(
        listOf(
            Transaction("p", "u", "pay", null, "Pay", 3000.0, "2026-10-01"),
            Transaction("f", "u", "food", null, "Lunch", 500.0, "2026-10-05"),
        ),
        categories, today, CashFlowStatus.cashAmount(carriedIn),
    )

    private fun show(carriedIn: Double, onRemove: (() -> Unit)?, enabled: Boolean = true) = rule.setContent {
        SpenditosTheme(darkTheme = false) {
            PulseHero(
                status = status(carriedIn), history = emptyList(), currency = "USD", locale = Locale.US,
                onSeeMath = {}, onOpenHealth = {}, onReviewPlan = {}, onAddIncome = {},
                onRemoveCarriedIn = onRemove, removeEnabled = enabled,
            )
        }
    }

    @Test
    fun `the carried in line has a labelled Remove that is at least 48dp and removes at once`() {
        var removed = 0
        show(150.0, onRemove = { removed++ })
        rule.onNodeWithTag("home.carriedIn", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("month-balance-remove", useUnmergedTree = true)
            .assertExists()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
        rule.onNode(hasContentDescription("Remove carried-in amount"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("month-balance-remove", useUnmergedTree = true).performClick()
        assertEquals("no confirmation step", 1, removed)
    }

    @Test
    fun `Remove waits while an answer is being saved`() {
        show(150.0, onRemove = {}, enabled = false)
        rule.onNodeWithTag("month-balance-remove", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `no carry-over means no line and no Remove`() {
        show(0.0, onRemove = {})
        rule.onNodeWithTag("home.carriedIn", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("month-balance-remove", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Remove stays hidden when the host does not offer it`() {
        show(150.0, onRemove = null)
        rule.onNodeWithTag("home.carriedIn", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("month-balance-remove", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(BigDecimal("150.00"), status(150.0).carriedIn)
    }
}
