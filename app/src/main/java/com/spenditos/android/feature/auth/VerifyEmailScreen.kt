package com.spenditos.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("verify.back")) { Text("Back") }
        Text("Verify your email", style = MaterialTheme.typography.headlineMedium)
        Text("Enter the 6-digit code sent to $email", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("verify.code"),
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("verify.error"))
        }
        state.notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().testTag("verify.notice"))
        }
        Button(
            onClick = { viewModel.verify(email) },
            enabled = state.canVerify,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify.submit"),
        ) {
            if (state.isLoading) CircularProgressIndicator() else Text("Verify")
        }
        TextButton(
            onClick = { viewModel.resend(email) },
            enabled = state.canResend,
            modifier = Modifier.fillMaxWidth().testTag("verify.resend"),
        ) {
            Text(if (state.resendSecondsRemaining > 0) "Resend code in ${state.resendSecondsRemaining}s" else "Resend code")
        }
    }
}
