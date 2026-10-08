package com.spenditos.android.feature.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

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
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                    .navigationBarsPadding().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Create Account", style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary)
                    Text("Start tracking your finances", style = IosText.subheadline, color = IosColors.textSecondary)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
                        placeholder = "Password (min 8 characters)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("register.password"),
                    )
                    PasswordStrengthContent(state.strength, state.requirements)
                    RoundedRegisterTextField(
                        value = state.confirmPasswordText,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        placeholder = "Confirm Password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("register.confirmPassword"),
                    )
                    if (state.confirmPasswordText.isNotEmpty() && !state.passwordsMatch) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            Text(
                                "Passwords do not match",
                                color = Color(0xFFFF3B30),
                                style = IosText.caption,
                                modifier = Modifier.testTag("register.passwordMismatch"),
                            )
                        }
                    }
                    state.error?.let { error ->
                        RegisterErrorBanner(error, Modifier.fillMaxWidth())
                    }
                    RegisterPrimaryButton(
                        text = "Create Account",
                        onClick = viewModel::register,
                        enabled = state.canRegister,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("register.submit"),
                    )
                }
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp).testTag("register.back"),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
            ) { Text("Back", style = IosText.subheadline) }
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
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.06f)).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = IosText.body.copy(color = IosColors.textPrimary),
        cursorBrush = SolidColor(IosColors.spendOrange),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = IosText.body, color = IosColors.textSecondary)
                innerTextField()
            }
        },
    )
}

@Composable
private fun PasswordStrengthContent(strength: PasswordStrength, requirements: List<PasswordRequirement>) {
    val segments = when (strength) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.MEDIUM -> 2
        PasswordStrength.STRONG -> 3
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> Color(0xFFFF3B30)
        PasswordStrength.MEDIUM -> Color(0xFFFF9500)
        PasswordStrength.STRONG -> Color(0xFF34C759)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = passwordStrengthAnnouncement(strength)
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (index < segments) color else Color.Gray.copy(alpha = 0.3f)),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requirements.forEach { requirement ->
                val tint = if (requirement.isMet) Color(0xFF34C759) else Color.Gray
                Row(
                    Modifier.semantics(mergeDescendants = true) {
                        stateDescription = if (requirement.isMet) "met" else "not met"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (requirement.isMet) SfSymbols.icon("checkmark.circle.fill") else SfSymbols.icon("circle"),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(requirement.label, color = tint, style = IosText.caption)
                }
            }
        }
    }
}

/** One spoken label for the color-only strength bars. */
internal fun passwordStrengthAnnouncement(strength: PasswordStrength): String =
    "Password strength: " + when (strength) {
        PasswordStrength.WEAK -> "weak"
        PasswordStrength.MEDIUM -> "fair"
        PasswordStrength.STRONG -> "strong"
    }

@Composable
private fun RegisterPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
    AuthRegisterLoadingButton(text, onClick, enabled, loading, modifier)
}

@Composable
private fun AuthRegisterLoadingButton(text: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
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

@Composable
private fun RegisterErrorBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.8f)).padding(16.dp)) {
        Text(text, color = Color.White, style = IosText.subheadline.w(FontWeight.Bold), modifier = Modifier.fillMaxWidth().testTag("register.error"))
    }
}
