package com.spenditos.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("forgot.back")) { Text("Back") }
        Text(
            if (state.step == ForgotPasswordStep.EMAIL) "Forgot password?" else "Reset password",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (state.step == ForgotPasswordStep.EMAIL) "Enter your email to receive a reset code."
            else "Enter the code sent to ${state.email} and choose a new password.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.step == ForgotPasswordStep.EMAIL) {
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag("forgot.email"),
            )
        } else {
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("forgot.code"),
            )
            OutlinedTextField(
                value = state.newPassword,
                onValueChange = { if (it.length <= 128) viewModel.onNewPasswordChange(it) },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("forgot.newPassword"),
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("forgot.error"))
        }
        state.notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().testTag("forgot.notice"))
        }
        Button(
            onClick = { if (state.step == ForgotPasswordStep.EMAIL) viewModel.sendCode() else viewModel.resetPassword() },
            enabled = if (state.step == ForgotPasswordStep.EMAIL) state.canSendCode else state.canReset,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("forgot.submit"),
        ) {
            if (state.isLoading) CircularProgressIndicator()
            else Text(if (state.step == ForgotPasswordStep.EMAIL) "Send code" else "Reset password")
        }
    }
}
