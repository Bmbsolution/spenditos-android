package com.spenditos.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric cannot model real IME focus (the host view re-focuses the first field after a
 * clear), so these check WHEN the dismiss callback fires; what it does is `clearFocus` + `hide`.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardDismissTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun tapOnEmptySpaceDismisses() {
        var dismissed = 0
        rule.setContent {
            Column(Modifier.fillMaxSize().dismissKeyboardOnTapOutsideWith { dismissed++ }) {
                Column(Modifier.fillMaxWidth().height(200.dp).testTag("empty")) {}
            }
        }
        rule.onNodeWithTag("empty").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun tapOnAButtonClicksItAndDoesNotDismiss() {
        var dismissed = 0
        var clicks = 0
        rule.setContent {
            Column(Modifier.fillMaxSize().dismissKeyboardOnTapOutsideWith { dismissed++ }) {
                Column(Modifier.fillMaxWidth().height(48.dp).clickable { clicks++ }.testTag("button")) {}
            }
        }
        rule.onNodeWithTag("button").performClick()
        assertEquals(1, clicks)
        assertEquals(0, dismissed)
    }

    @Test
    fun tapOnATextFieldDoesNotDismiss() {
        // Moving from one field to another must not close the keyboard in between.
        var dismissed = 0
        rule.setContent {
            Column(Modifier.fillMaxSize().dismissKeyboardOnTapOutsideWith { dismissed++ }) {
                var a by remember { mutableStateOf("") }
                var b by remember { mutableStateOf("") }
                BasicTextField(a, { a = it }, Modifier.fillMaxWidth().height(48.dp).background(Color.Gray).testTag("a"))
                BasicTextField(b, { b = it }, Modifier.fillMaxWidth().height(48.dp).background(Color.Gray).testTag("b"))
            }
        }
        rule.onNodeWithTag("a").performClick()
        rule.onNodeWithTag("b").performClick()
        rule.onNodeWithTag("a").performClick()
        assertEquals(0, dismissed)
    }

    @Test
    fun scrollingDismissesOncePerBurst() {
        var dismissed = 0
        rule.setContent {
            Column(
                Modifier.fillMaxSize().testTag("scroller").dismissKeyboardOnScrollWith { dismissed++ }
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.fillMaxWidth().height(3000.dp)) {}
            }
        }
        rule.onNodeWithTag("scroller").performTouchInput { swipeUp() }
        assertEquals(1, dismissed)
    }
}
