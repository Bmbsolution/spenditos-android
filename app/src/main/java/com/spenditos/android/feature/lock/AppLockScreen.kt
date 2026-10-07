package com.spenditos.android.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.spenditos.android.core.lock.BiometricAuthenticator
import com.spenditos.android.core.lock.LockAuthResult
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen opaque lock. Never reveals what is underneath; asks for the fingerprint/face or the
 * device passcode automatically once, then on the button.
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
                .navigationBarsPadding().padding(horizontal = Spacing.screen)
                .testTag("lock.screen"),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.size(120.dp).background(IosColors.spendOrange.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(77.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Locked", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary)
                Text(
                    "Unlock Spenditos to continue",
                    style = IosText.callout,
                    color = IosColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            message?.let {
                Text(
                    it,
                    color = Color(0xFFFF3B30),
                    style = IosText.subheadline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("lock.error"),
                )
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(24.dp))
                    .background(IosColors.spendOrange).clickable(enabled = !busy, role = Role.Button, onClick = { attempt() })
                    .testTag("lock.unlock"),
                contentAlignment = Alignment.Center,
            ) {
                RowContent(busy)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowContent(busy: Boolean) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp).testTag("lock.busy"), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Color.Black, modifier = Modifier.size(23.dp))
        }
        Text("Unlock with Biometrics", style = IosText.headline, color = Color.Black)
    }
}

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
