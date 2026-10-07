package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requirements = state.requirements
    val score = requirements.count { it.isMet }
    val strength = when {
        score <= 1 -> PasswordStrengthPresentation("Weak", 1, Color(0xFFFF3B30))
        score == 2 -> PasswordStrengthPresentation("Fair", 2, Color(0xFFFF9500))
        score <= 4 -> PasswordStrengthPresentation("Good", 3, Color(0xFFFFCC00))
        else -> PasswordStrengthPresentation("Strong", 4, Color(0xFF34C759))
    }
    val finance = LocalFinanceColors.current

    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            PasswordHeader(onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.l)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    state.error?.let { message ->
                        Text(message, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag("changePassword.error"))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel("Current Password", modifier = if (state.currentPassword.isEmpty()) Modifier.testTag("changePassword.empty") else Modifier)
                        PasswordField(
                            value = state.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChange,
                            placeholder = "Enter current password",
                            tag = "changePassword.current",
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel("New Password")
                        PasswordField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            placeholder = "Enter new password",
                            tag = "changePassword.new",
                        )
                        if (state.newPassword.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    repeat(4) { index ->
                                        Box(
                                            Modifier.weight(1f).height(4.dp).then(
                                                subtleGlassModifier(CircleShape),
                                            ),
                                        ) {
                                            if (index < strength.level) {
                                                Box(Modifier.fillMaxSize().then(androidx.compose.ui.draw.clip(CircleShape)).background(strength.color))
                                            }
                                        }
                                    }
                                }
                                Text(strength.label, style = IosText.subheadline, color = strength.color)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel("Confirm New Password")
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            placeholder = "Re-enter new password",
                            tag = "changePassword.confirm",
                        )
                        if (state.confirmPassword.isNotEmpty() && state.confirmPassword != state.newPassword) {
                            Text("Passwords do not match", style = IosText.subheadline, color = finance.expense, modifier = Modifier.testTag("changePassword.mismatch"))
                        }
                    }

                    Column(Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        requirements.forEachIndexed { index, requirement ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                Icon(
                                    SfSymbols.icon(if (requirement.isMet) "checkmark.circle.fill" else "circle"),
                                    contentDescription = null,
                                    tint = if (requirement.isMet) finance.income else IosColors.textTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    requirement.label,
                                    style = IosText.subheadline,
                                    color = if (requirement.isMet) Color.White else Color.Gray,
                                    modifier = Modifier.testTag("changePassword.requirement.$index"),
                                )
                            }
                        }
                    }

                    if (state.isOffline) Text("Check your connection and try again.", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("changePassword.offline"))
                    if (state.success) {
                        Text("Your password has been changed successfully.", style = IosText.footnote, color = finance.income, modifier = Modifier.testTag("changePassword.success"))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                        PillButton(
                            text = "Update Password",
                            onClick = viewModel::changePassword,
                            enabled = state.canSave,
                            modifier = Modifier.fillMaxWidth().testTag("changePassword.save"),
                        )
                        if (state.isSaving) CircularProgressIndicator(
                            Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

private data class PasswordStrengthPresentation(val label: String, val level: Int, val color: Color)

@Composable
private fun PasswordLabel(text: String, style: androidx.compose.ui.text.TextStyle = IosText.subheadline, modifier: Modifier = Modifier) {
    Text(text, style = style, color = IosColors.textSecondary, modifier = modifier)
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String, tag: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = IosText.body.copy(color = IosColors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag(tag),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = IosText.body, color = IosColors.textTertiary)
                inner()
            }
        },
    )
}

@Composable
private fun PasswordHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text("Change Password", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}
