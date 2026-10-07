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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
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

    IosScreen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("register.back"),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Back") }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Create account", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text("Start tracking your finances.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoundedRegisterTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("register.email"),
                )
                RoundedRegisterTextField(
                    value = state.password,
                    onValueChange = { if (it.length <= 128) viewModel.onPasswordChange(it) },
                    placeholder = "Password",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("register.password"),
                )
                PasswordStrengthMeter(state.strength)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    state.requirements.forEach { requirement ->
                        val tint = if (requirement.isMet) LocalFinanceColors.current.income else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (requirement.isMet) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(requirement.label, color = tint, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                RoundedRegisterTextField(
                    value = state.confirmPasswordText,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    placeholder = "Confirm password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("register.confirmPassword"),
                )
                if (state.confirmPasswordText.isNotEmpty() && !state.passwordsMatch) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            "Passwords do not match.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("register.passwordMismatch"),
                        )
                    }
                }
                state.error?.let { error ->
                    RegisterErrorBanner(error, Modifier.fillMaxWidth())
                }
                RegisterPrimaryButton(
                    text = "Create account",
                    onClick = viewModel::register,
                    enabled = state.canRegister,
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("register.submit"),
                )
            }
        }
    }
}

@Composable
private fun RoundedRegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val fieldColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color.Black.copy(alpha = 0.06f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(fieldColor).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
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
private fun PasswordStrengthMeter(strength: PasswordStrength) {
    val segments = when (strength) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.MEDIUM -> 2
        PasswordStrength.STRONG -> 3
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> Color(0xFFF59E0B)
        PasswordStrength.STRONG -> LocalFinanceColors.current.income
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (index < segments) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                )
            }
        }
        Text(strength.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RegisterPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
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

@Composable
private fun RegisterErrorBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.88f)).padding(12.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth().testTag("register.error"))
    }
}
