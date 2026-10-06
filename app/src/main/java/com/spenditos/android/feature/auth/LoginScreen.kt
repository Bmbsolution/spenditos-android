package com.spenditos.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.EnvBadge
import com.spenditos.android.ui.theme.Spacing

@Composable
fun LoginScreen(
    envName: String?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onCreateAccount: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onNeedsVerification: (email: String) -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.screen).imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Spenditos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            envName?.let { EnvBadge(it, Modifier.padding(top = Spacing.s)) }
            Text(
                "Sign in to see your money picture.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.l),
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("login.email"),
            )
            Spacer(Modifier.height(Spacing.s))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
                modifier = Modifier.fillMaxWidth().testTag("login.password"),
            )
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("login.error"),
                )
            }
            if (state.needsVerification) {
                TextButton(
                    onClick = { onNeedsVerification(state.verificationEmail ?: state.email) },
                    modifier = Modifier.fillMaxWidth().testTag("login.verifyEmail"),
                ) { Text("Verify email") }
            }
            Button(
                onClick = viewModel::signIn,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(52.dp).testTag("login.submit"),
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            TextButton(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("login.createAccount"),
            ) { Text("Create account") }
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.fillMaxWidth().testTag("login.forgotPassword"),
            ) { Text("Forgot password?") }
        }
    }
}
