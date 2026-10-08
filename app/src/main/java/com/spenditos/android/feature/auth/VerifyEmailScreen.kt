package com.spenditos.android.feature.auth

import com.spenditos.android.ui.components.SfSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.w
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
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(SfSymbols.icon("envelope.badge.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(72.dp))
                    Text("Verify your email", style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary)
                    Text(
                        "Enter the 6-digit code we sent to\n$email",
                        style = IosText.subheadline,
                        color = IosColors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                    VerifyLoadingButton(
                        text = "Verify",
                        onClick = { viewModel.verify(email) },
                        enabled = state.canVerify,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("verify.submit"),
                    )
                    TextButton(
                        onClick = { viewModel.resend(email) },
                        enabled = state.canResend,
                        modifier = Modifier.fillMaxWidth().testTag("verify.resend"),
                        colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                    ) {
                        Text(
                            if (state.resendSecondsRemaining > 0) "Resend code in ${state.resendSecondsRemaining}s" else "Resend code",
                            style = IosText.subheadline,
                        )
                    }
                }
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp).testTag("verify.back"),
                colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
            ) { Text("Back", style = IosText.subheadline) }
        }
    }
}

@Composable
private fun OtpTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp).semantics { contentDescription = "Verification code" },
        singleLine = true,
        textStyle = IosText.rounded(28, FontWeight.Bold).copy(color = Color.Transparent, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "6-digit verification code" },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) { index ->
                        OtpDigitBox(value.getOrNull(index)?.toString().orEmpty(), focused && index == value.length)
                    }
                }
                Box(Modifier.size(1.dp)) { innerTextField() }
            }
        },
    )
}

@Composable
private fun OtpDigitBox(digit: String, focused: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier.size(width = 48.dp, height = 56.dp).clip(shape)
            .background(Color.Black.copy(alpha = if (focused) 0.08f else 0.03f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) IosColors.spendOrange else Color.Black.copy(alpha = 0.1f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            digit,
            style = IosText.rounded(28, FontWeight.Bold),
            color = if (digit.isNotEmpty()) IosColors.textPrimary else Color.Gray,
        )
    }
}

@Composable
private fun ErrorMessageBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.8f)).padding(16.dp)) {
        Text(text, color = Color.White, style = IosText.subheadline.w(FontWeight.Bold), modifier = Modifier.fillMaxWidth().testTag("verify.error"))
    }
}

@Composable
private fun NoticeMessageBanner(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color(0xFF34C759), style = IosText.subheadline, modifier = modifier.testTag("verify.notice"))
}

@Composable
private fun VerifyLoadingButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val disabled = !enabled
    val background = when {
        loading -> IosColors.spendOrange.copy(alpha = 0.72f)
        disabled -> if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
        else -> IosColors.spendOrange
    }
    Box(
        modifier.height(52.dp).clip(shape).background(background)
            .then(if (disabled) Modifier.border(1.dp, IosColors.separator.copy(alpha = 0.4f), shape) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Text(text, style = IosText.headline, color = if (disabled) IosColors.textTertiary else Color.White)
        }
    }
}
