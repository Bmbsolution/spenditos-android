package com.spenditos.android.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.spenditos.android.core.lock.BiometricAuthenticator
import com.spenditos.android.core.lock.LockAuthResult
import com.spenditos.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen opaque lock. Never reveals what is underneath; asks for the fingerprint/face or the
 * device passcode (iOS "Use Passcode") automatically once, then on the button.
 */
@Composable
fun AppLockScreen(
    authenticator: BiometricAuthenticator,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var autoTried by rememberSaveable { mutableStateOf(false) }

    fun attempt() {
        val activity = context.findFragmentActivity() ?: return
        if (busy) return
        busy = true
        message = null
        scope.launch {
            when (val result = authenticator.authenticate(activity, "Unlock Spenditos", "Confirm it's you to continue")) {
                LockAuthResult.Success -> onUnlocked()
                LockAuthResult.Cancelled -> message = null
                is LockAuthResult.Failed -> message = result.message
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (autoTried) return@LaunchedEffect
        autoTried = true
        delay(250)
        attempt()
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow every touch so nothing underneath can be reached.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .safeDrawingPadding()
            .padding(horizontal = Spacing.screen)
            .testTag("lock.screen"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Locked", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Unlock Spenditos to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("lock.error"))
        }
        Button(onClick = { attempt() }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("lock.unlock")) {
            if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).testTag("lock.busy"), strokeWidth = 2.dp)
            Text("Unlock")
        }
    }
}

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
