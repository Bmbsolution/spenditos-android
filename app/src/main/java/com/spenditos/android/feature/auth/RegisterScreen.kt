package com.spenditos.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

@Composable
fun RegisterScreen(onRegistered: (email: String) -> Unit, onBack: () -> Unit) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is RegisterEvent.Registered) onRegistered(event.email)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("register.back")) { Text("Back") }
        Text("Create account", style = MaterialTheme.typography.headlineMedium)
        Text("Start tracking your finances.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Enter your email and choose a password to continue.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("register.email"),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = { if (it.length <= 128) viewModel.onPasswordChange(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("register.password"),
        )
        PasswordStrengthMeter(state.strength)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.requirements.forEach { requirement ->
                Text(
                    "${if (requirement.isMet) "✓" else "○"} ${requirement.label}",
                    color = if (requirement.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedTextField(
            value = state.confirmPasswordText,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = { Text("Confirm password") },
            singleLine = true,
            isError = state.confirmPasswordText.isNotEmpty() && !state.passwordsMatch,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("register.confirmPassword"),
        )
        if (state.confirmPasswordText.isNotEmpty() && !state.passwordsMatch) {
            Text("Passwords do not match.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("register.passwordMismatch"))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("register.error"))
        }
        Button(
            onClick = viewModel::register,
            enabled = state.canRegister,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("register.submit"),
        ) {
            if (state.isLoading) CircularProgressIndicator() else Text("Create account")
        }
    }
}

@Composable
private fun PasswordStrengthMeter(strength: PasswordStrength) {
    val segments = when (strength) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.MEDIUM -> 2
        PasswordStrength.STRONG -> 3
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Box(
                Modifier.weight(1f).height(5.dp).background(
                    if (index < segments) color else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small,
                ),
            )
        }
    }
    Text(strength.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
}
