package com.spenditos.android.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.spenditos.android.core.lock.BiometricAuthenticator
import com.spenditos.android.core.lock.LockAuthResult
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
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

    IosScreen {
        Column(
            modifier = modifier.fillMaxSize()
                // Swallow every touch so nothing underneath can be reached.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .testTag("lock.screen"),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Locked", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Unlock Spenditos to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            message?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(12.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.testTag("lock.error"))
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton(text = "Unlock", onClick = { attempt() }, modifier = Modifier.fillMaxWidth().testTag("lock.unlock"), enabled = !busy)
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.CenterStart).padding(start = 74.dp)
                            .padding(end = 8.dp).testTag("lock.busy").size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
