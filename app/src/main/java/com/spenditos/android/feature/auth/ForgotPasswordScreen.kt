package com.spenditos.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun ForgotPasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val viewModel: ForgotPasswordViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ForgotPasswordEvent.Done) {
                delay(1_500)
                onDone()
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
                    modifier = Modifier.testTag("forgot.back"),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Back") }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ForgotHeaderIcon(isEmailStep = state.step == ForgotPasswordStep.EMAIL)
                Text(
                    if (state.step == ForgotPasswordStep.EMAIL) "Forgot password?" else "Reset password",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    if (state.step == ForgotPasswordStep.EMAIL) "Enter your email to receive a reset code."
                    else "Enter the code sent to ${state.email} and choose a new password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.step == ForgotPasswordStep.EMAIL) {
                    RoundedForgotTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = "Email",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("forgot.email"),
                    )
                } else {
                    RoundedForgotTextField(
                        value = state.code,
                        onValueChange = viewModel::onCodeChange,
                        placeholder = "Code",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().testTag("forgot.code"),
                    )
                    RoundedForgotTextField(
                        value = state.newPassword,
                        onValueChange = { if (it.length <= 128) viewModel.onNewPasswordChange(it) },
                        placeholder = "New password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("forgot.newPassword"),
                    )
                }
                state.error?.let { error ->
                    ForgotMessageBanner(error, isError = true, modifier = Modifier.fillMaxWidth(), textModifier = Modifier.fillMaxWidth().testTag("forgot.error"))
                }
                state.notice?.let { notice ->
                    ForgotMessageBanner(notice, isError = false, modifier = Modifier.fillMaxWidth(), textModifier = Modifier.fillMaxWidth().testTag("forgot.notice"))
                }
                ForgotPrimaryButton(
                    text = if (state.step == ForgotPasswordStep.EMAIL) "Send code" else "Reset password",
                    onClick = { if (state.step == ForgotPasswordStep.EMAIL) viewModel.sendCode() else viewModel.resetPassword() },
                    enabled = if (state.step == ForgotPasswordStep.EMAIL) state.canSendCode else state.canReset,
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("forgot.submit"),
                )
            }
        }
    }
}

@Composable
private fun ForgotHeaderIcon(isEmailStep: Boolean) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (isEmailStep) Icons.Filled.Lock else Icons.Filled.Email,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp),
        )
        if (isEmailStep) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).size(23.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            )
        } else {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).size(23.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

@Composable
private fun RoundedForgotTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val fieldColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color.Black.copy(alpha = 0.06f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(fieldColor).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                innerTextField()
            }
        },
    )
}

@Composable
private fun ForgotMessageBanner(text: String, isError: Boolean, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    val tint = if (isError) MaterialTheme.colorScheme.error else LocalFinanceColors.current.income
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = if (isError) 0.88f else 0.13f)).padding(12.dp),
    ) {
        Text(
            text,
            color = if (isError) Color.White else tint,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal),
            modifier = textModifier,
        )
    }
}

@Composable
private fun ForgotPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
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
