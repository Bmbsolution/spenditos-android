package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.DismissKeyboardWhen
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.doneKeyboardOptions
import com.spenditos.android.ui.components.rememberKeyboardDoneActions
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requirements = state.requirements
    val score = requirements.count { it.isMet }
    val strength = when {
        score <= 1 -> PasswordStrengthPresentation(stringResource(R.string.settings_weak), 1, Color(0xFFFF3B30))
        score == 2 -> PasswordStrengthPresentation(stringResource(R.string.settings_fair), 2, Color(0xFFFF9500))
        score <= 4 -> PasswordStrengthPresentation(stringResource(R.string.common_good), 3, Color(0xFFFFCC00))
        else -> PasswordStrengthPresentation(stringResource(R.string.settings_strong), 4, Color(0xFF34C759))
    }
    val finance = LocalFinanceColors.current

    DismissKeyboardWhenInactive()
    DismissKeyboardWhen(state.success)
    IosScreen {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            PasswordHeader(onBack)
            Column(
                Modifier.weight(1f).dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                    .verticalScroll(rememberScrollState()).padding(bottom = Spacing.l),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    state.error?.let { message ->
                        ErrorBanner(message, Modifier.testTag("changePassword.error"))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel(stringResource(R.string.settings_current_password), modifier = if (state.currentPassword.isEmpty()) Modifier.testTag("changePassword.empty") else Modifier)
                        PasswordField(
                            value = state.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChange,
                            placeholder = stringResource(R.string.settings_enter_current_password),
                            tag = "changePassword.current",
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel(stringResource(R.string.settings_new_password))
                        PasswordField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            placeholder = stringResource(R.string.settings_enter_new_password),
                            tag = "changePassword.new",
                        )
                        if (state.newPassword.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Row(
                                    Modifier.semantics(mergeDescendants = true) {
                                        contentDescription = passwordStrengthAnnouncement(strength.label)
                                    },
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    repeat(4) { index ->
                                        Box(
                                            Modifier.weight(1f).height(4.dp).clip(CircleShape)
                                                .background(if (index < strength.level) strength.color else Color.Black.copy(alpha = 0.06f)),
                                        )
                                    }
                                }
                                Text(strength.label, style = IosText.subheadline, color = strength.color)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PasswordLabel(stringResource(R.string.settings_confirm_new_password))
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            placeholder = stringResource(R.string.settings_re_enter_new_password),
                            tag = "changePassword.confirm",
                        )
                        if (state.confirmPassword.isNotEmpty() && state.confirmPassword != state.newPassword) {
                            Text(stringResource(R.string.common_passwords_do_not_match), style = IosText.subheadline, color = finance.expense, modifier = Modifier.testTag("changePassword.mismatch"))
                        }
                    }

                    Column(Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        requirements.forEachIndexed { index, requirement ->
                            Row(
                                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                                    stateDescription = if (requirement.isMet) "met" else "not met"
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                Icon(
                                    if (requirement.isMet) SfSymbols.icon("checkmark.circle.fill") else SfSymbols.icon("circle"),
                                    contentDescription = null,
                                    tint = if (requirement.isMet) finance.income else IosColors.textTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    requirement.label,
                                    style = IosText.subheadline,
                                    color = if (requirement.isMet) IosColors.textPrimary else IosColors.textSecondary,
                                    modifier = Modifier.testTag("changePassword.requirement.$index"),
                                )
                            }
                        }
                    }

                    if (state.isOffline) Text(stringResource(R.string.settings_check_your_connection_and_try), style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("changePassword.offline"))
                    if (state.isSaving) {
                        SavingPill(stringResource(R.string.settings_update_password), Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("changePassword.save"))
                    } else {
                        PillButton(
                            text = stringResource(R.string.settings_update_password),
                            onClick = viewModel::changePassword,
                            enabled = state.canSave,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("changePassword.save"),
                        )
                    }
                }
            }
        }
    }
    if (state.success) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.settings_password_updated), style = IosText.headline) },
            text = { Text(stringResource(R.string.settings_your_password_has_been_changed), style = IosText.body, modifier = Modifier.testTag("changePassword.success")) },
            confirmButton = { TextButton(onClick = onBack) { Text("OK", color = IosColors.spendOrange) } },
        )
    }
}

private data class PasswordStrengthPresentation(val label: String, val level: Int, val color: Color)

/** One spoken label for the color-only strength bars. */
internal fun passwordStrengthAnnouncement(label: String): String =
    Strings.get(R.string.settings_password_strength, label.lowercase(java.util.Locale.ROOT))

@Composable
private fun SavingPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(Radius.m)).background(IosColors.spendOrange.copy(alpha = 0.72f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        Text(text, style = IosText.headline, color = Color.White, modifier = Modifier.padding(start = Spacing.s))
    }
}

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
        textStyle = IosText.callout.copy(color = IosColors.textPrimary),
        keyboardOptions = doneKeyboardOptions(KeyboardType.Password),
        keyboardActions = rememberKeyboardDoneActions(),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().then(subtleGlassModifier()).padding(Spacing.m).testTag(tag),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = IosText.callout, color = IosColors.textTertiary)
                inner()
            }
        },
    )
}

@Composable
private fun PasswordHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text(stringResource(R.string.settings_change_password), style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}
