package com.spenditos.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun VerifyEmailScreen(email: String, onVerified: () -> Unit, onBack: () -> Unit) {
    val viewModel: VerifyEmailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is VerifyEmailEvent.Verified) {
                delay(1_200)
                onVerified()
            }
        }
    }

    IosScreen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("verify.back"),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Back") }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
                Text("Verify your email", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Enter the 6-digit code sent to $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OtpTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    modifier = Modifier.fillMaxWidth().testTag("verify.code"),
                )
                state.error?.let { ErrorMessageBanner(it, Modifier.fillMaxWidth()) }
                state.notice?.let { NoticeMessageBanner(it, Modifier.fillMaxWidth()) }
                AuthVerifyButton(
                    text = "Verify",
                    onClick = { viewModel.verify(email) },
                    enabled = state.canVerify,
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify.submit"),
                )
                TextButton(
                    onClick = { viewModel.resend(email) },
                    enabled = state.canResend,
                    modifier = Modifier.fillMaxWidth().testTag("verify.resend"),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (state.resendSecondsRemaining > 0) "Resend code in ${state.resendSecondsRemaining}s" else "Resend code")
                }
            }
        }
    }
}

@Composable
private fun OtpTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { index ->
                        val active = index == value.length.coerceAtMost(5)
                        OtpDigitBox(value.getOrNull(index)?.toString().orEmpty(), active)
                    }
                }
                Box(Modifier.size(1.dp)) { innerTextField() }
            }
        },
    )
}

@Composable
private fun OtpDigitBox(digit: String, active: Boolean) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val boxColor = when {
        active && dark -> Color(0xFF2C2C2E)
        active -> Color.Black.copy(alpha = 0.08f)
        dark -> Color(0xFF1C1C1E)
        else -> Color.Black.copy(alpha = 0.03f)
    }
    Box(
        modifier = Modifier.size(width = 48.dp, height = 56.dp).clip(RoundedCornerShape(16.dp))
            .background(boxColor)
            .border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ErrorMessageBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.88f)).padding(12.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth().testTag("verify.error"))
    }
}

@Composable
private fun NoticeMessageBanner(text: String, modifier: Modifier = Modifier) {
    val tint = com.spenditos.android.ui.theme.LocalFinanceColors.current.income
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.13f)).padding(12.dp)) {
        Text(text, color = tint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().testTag("verify.notice"))
    }
}

@Composable
private fun AuthVerifyButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center) {
        PillButton(text, onClick, modifier = modifier, enabled = enabled && !loading)
        if (loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.CenterStart).padding(start = 74.dp).size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        }
    }
}
