package com.spenditos.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one way to close the keyboard: clears focus (so the field also loses its caret and the
 * focus-driven styling) and hides the IME. Port of iOS `Keyboard.dismiss()`.
 */
@Composable
fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

/**
 * Closes the keyboard on the first user scroll or drag of anything inside (iOS
 * `.scrollDismissesKeyboard(.immediately)`). Put it BEFORE `verticalScroll`, or on a parent of the
 * scrolling content, so it sits above the scroller in the nested-scroll chain.
 */
fun Modifier.dismissKeyboardOnScroll(): Modifier = composed {
    dismissKeyboardOnScrollWith(rememberKeyboardDismisser())
}

internal fun Modifier.dismissKeyboardOnScrollWith(dismiss: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(dismiss)
    val connection = remember {
        object : NestedScrollConnection {
            private var lastDismissNanos = 0L

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
                    // A scroll is dozens of events; closing the keyboard once per burst is enough.
                    val now = System.nanoTime()
                    if (now - lastDismissNanos > SCROLL_DISMISS_THROTTLE_NANOS) {
                        lastDismissNanos = now
                        current()
                    }
                }
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}

/**
 * Closes the keyboard when the user taps empty space. It watches the Final pointer pass, after
 * children have run, so a tap on a button, chip or text field (which consume it) is left alone:
 * buttons keep working and moving from one text field to another never closes the keyboard in
 * between. A `ModalBottomSheet` or dialog is its own window, so apply this inside its content too.
 */
fun Modifier.dismissKeyboardOnTapOutside(): Modifier = composed {
    dismissKeyboardOnTapOutsideWith(rememberKeyboardDismisser())
}

internal fun Modifier.dismissKeyboardOnTapOutsideWith(dismiss: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(dismiss)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(PointerEventPass.Final)
            if (up != null && !up.isConsumed) current()
        }
    }
}

/** Tap-outside plus scroll dismissal, for a form's content root. */
fun Modifier.dismissKeyboardOnTapOrScroll(): Modifier =
    dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()

/**
 * Closes the keyboard when this screen leaves composition (back, navigation, sheet closed) and
 * when the app stops (lock screen, home). iOS `.dismissesKeyboardWhenInactive()`.
 */
@Composable
fun DismissKeyboardWhenInactive() {
    val dismiss by rememberUpdatedState(rememberKeyboardDismisser())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            dismiss()
        }
    }
}

/**
 * Closes the keyboard when [visible] turns true. Use it for a sheet, dialog or picker that code
 * opens (async scan/parse results, paywall) where no tap on the form closed the keyboard. Does
 * nothing while [visible] is false, so it never fights a form that focuses its first field.
 *
 * It closes the keyboard again just after the popup goes away: when a dialog window closes, the
 * text field that owned the keyboard when it opened can get its focus back (caret and highlight,
 * no keyboard).
 */
@Composable
fun DismissKeyboardWhen(visible: Boolean) {
    val dismiss by rememberUpdatedState(rememberKeyboardDismisser())
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            wasVisible = true
            dismiss()
        } else if (wasVisible) {
            wasVisible = false
            delay(POPUP_CLOSED_PAUSE_MS)
            dismiss()
        }
    }
}

/**
 * Closes the keyboard and waits until the IME is gone and the layout has settled. Open a popup
 * that anchors to something the keyboard moves (the date dialog after the Date chips) only after
 * this returns. A no-op when the keyboard is already closed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberKeyboardSettler(): suspend () -> Unit {
    val dismiss = rememberKeyboardDismisser()
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    return remember(dismiss) {
        {
            if (imeVisible) {
                dismiss()
                withTimeoutOrNull(KEYBOARD_SETTLE_TIMEOUT_MS) { snapshotFlow { imeVisible }.first { !it } }
                delay(KEYBOARD_SETTLE_PAUSE_MS)
            }
        }
    }
}

/**
 * Keyboard options whose action key is Done. Number and decimal pads have no return key on some
 * keyboards, so every such field uses this (with [rememberKeyboardDoneActions]) to get a way out;
 * single-line text fields use it too so the key closes the keyboard instead of doing nothing.
 */
fun doneKeyboardOptions(type: KeyboardType = KeyboardType.Text): KeyboardOptions =
    KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done)

/** `onDone` that closes the keyboard. Pair with [doneKeyboardOptions] (or any `ImeAction.Done`). */
@Composable
fun rememberKeyboardDoneActions(): KeyboardActions {
    val dismiss = rememberKeyboardDismisser()
    return remember(dismiss) { KeyboardActions(onDone = { dismiss() }) }
}

private const val POPUP_CLOSED_PAUSE_MS = 150L
private const val SCROLL_DISMISS_THROTTLE_NANOS = 150_000_000L
private const val KEYBOARD_SETTLE_TIMEOUT_MS = 800L
private const val KEYBOARD_SETTLE_PAUSE_MS = 100L
