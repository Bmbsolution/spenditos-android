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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Spacing

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IosScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = Spacing.l),
        ) {
            item { PasswordHeader(onBack) }
            if (state.currentPassword.isEmpty()) {
                item {
                    Text(
                        "Enter your current password to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("changePassword.empty"),
                    )
                }
            }
            item { SectionHeader("Security", Modifier.padding(top = Spacing.s)) }
            item {
                GroupedCard {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        OutlinedTextField(
                            value = state.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChange,
                            label = { Text("Current password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("changePassword.current"),
                        )
                        OutlinedTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("changePassword.new"),
                        )
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = { Text("Confirm new password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("changePassword.confirm"),
                        )
                        if (state.confirmPassword.isNotEmpty() && state.confirmPassword != state.newPassword) {
                            Text("Passwords do not match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("changePassword.mismatch"))
                        }
                    }
                }
            }
            item { SectionHeader("Password requirements") }
            item {
                GroupedCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        state.requirements.forEachIndexed { index, requirement ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                Text(
                                    if (requirement.isMet) "✓" else "○",
                                    color = if (requirement.isMet) Color(0xFF1F7F47) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = requirement.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (requirement.isMet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("changePassword.requirement.$index"),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("changePassword.error")) }
                    if (state.isOffline) Text("Check your connection and try again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("changePassword.offline"))
                    if (state.success) Text("Password updated.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1F7F47), modifier = Modifier.testTag("changePassword.success"))
                    Box(Modifier.fillMaxWidth()) {
                        PillButton(
                            text = "Update password",
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

@Composable
private fun PasswordHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Change password", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
    }
}
