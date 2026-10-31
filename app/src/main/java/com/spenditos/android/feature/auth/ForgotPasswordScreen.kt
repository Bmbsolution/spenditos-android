package com.spenditos.android.feature.auth

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.SfSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
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
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
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

    DismissKeyboardWhenInactive()
    IosScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                    .verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ForgotHeaderIcon(isEmailStep = state.step == ForgotPasswordStep.EMAIL)
                    Text(
                        if (state.step == ForgotPasswordStep.EMAIL) stringResource(R.string.auth_forgot_password) else stringResource(R.string.auth_reset_password),
                        style = IosText.largeTitle.w(FontWeight.Bold),
                        color = IosColors.textPrimary,
                    )
                    Text(
                        if (state.step == ForgotPasswordStep.EMAIL) stringResource(R.string.auth_enter_your_email_to_receive)
                        else stringResource(R.string.auth_enter_the_code_we_sent, state.email),
                        style = IosText.subheadline,
                        color = IosColors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.step == ForgotPasswordStep.EMAIL) {
                        RoundedForgotTextField(
                            value = state.email,
                            onValueChange = viewModel::onEmailChange,
                            placeholder = stringResource(R.string.auth_email),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().testTag("forgot.email"),
                        )
                    } else {
                        RoundedForgotTextField(
                            value = state.code,
                            onValueChange = viewModel::onCodeChange,
                            placeholder = "6-digit code",
                            keyboardOptions = doneKeyboardOptions(KeyboardType.Number),
                            textStyle = IosText.rounded(22),
                            textAlign = TextAlign.Center,
                            heightDp = 58,
                            modifier = Modifier.fillMaxWidth().testTag("forgot.code"),
                        )
                        RoundedForgotTextField(
                            value = state.newPassword,
                            onValueChange = { if (it.length <= 128) viewModel.onNewPasswordChange(it) },
                            placeholder = stringResource(R.string.auth_new_password_min_8_characters),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = doneKeyboardOptions(KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("forgot.newPassword"),
                        )
                    }
                    state.error?.let { error ->
                        ForgotErrorBanner(error, Modifier.fillMaxWidth().testTag("forgot.error"))
                    }
                    state.notice?.let { notice ->
                        if (state.isDone) {
                            ForgotSuccessBanner(stringResource(R.string.auth_password_reset_you_can_now), Modifier.fillMaxWidth().testTag("forgot.notice"))
                        } else {
                            Text(notice, style = IosText.subheadline, color = Color(0xFF34C759), modifier = Modifier.fillMaxWidth().testTag("forgot.notice"))
                        }
                    }
                    ForgotPrimaryButton(
                        text = if (state.step == ForgotPasswordStep.EMAIL) stringResource(R.string.auth_send_code) else stringResource(R.string.auth_reset_password),
                        onClick = { if (state.step == ForgotPasswordStep.EMAIL) viewModel.sendCode() else viewModel.resetPassword() },
                        enabled = if (state.step == ForgotPasswordStep.EMAIL) state.canSendCode else state.canReset,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("forgot.submit"),
                    )
                    if (state.isDone) {
                        TextButton(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                            colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                        ) { Text(stringResource(R.string.auth_back_to_sign_in), style = IosText.headline) }
                    }
                }
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp).testTag("forgot.back"),
                colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
            ) { Text(stringResource(R.string.common_back), style = IosText.subheadline) }
        }
    }
}

@Composable
private fun ForgotHeaderIcon(isEmailStep: Boolean) {
    Icon(
        imageVector = if (isEmailStep) SfSymbols.icon("lock.rotation") else SfSymbols.icon("envelope.badge.fill"),
        contentDescription = null,
        tint = IosColors.spendOrange,
        modifier = Modifier.size(72.dp),
    )
}

@Composable
private fun RoundedForgotTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = IosText.body,
    textAlign: TextAlign = TextAlign.Start,
    heightDp: Int = 54,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.06f)).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = textStyle.copy(color = IosColors.textPrimary, textAlign = textAlign),
        cursorBrush = SolidColor(IosColors.spendOrange),
        keyboardOptions = keyboardOptions,
        keyboardActions = rememberKeyboardDoneActions(),
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = IosColors.textSecondary, textAlign = textAlign)
                innerTextField()
            }
        },
    )
}

@Composable
private fun ForgotErrorBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.8f)).padding(16.dp)) {
        Text(text, color = Color.White, style = IosText.subheadline.w(FontWeight.Bold), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ForgotSuccessBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF34C759).copy(alpha = 0.8f)).padding(16.dp)) {
        Text(text, color = Color.White, style = IosText.subheadline.w(FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ForgotPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
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
