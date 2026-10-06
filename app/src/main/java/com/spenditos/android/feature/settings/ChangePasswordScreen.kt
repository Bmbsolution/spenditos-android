package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
fun ChangePasswordScreen(viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Change password") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.currentPassword.isEmpty()) {
                        Text("Enter your current password to continue.", modifier = Modifier.testTag("changePassword.empty"))
                    }
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
                        Text("Passwords do not match", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("changePassword.mismatch"))
                    }
                }
            }
            itemsIndexed(state.requirements) { index, requirement ->
                Text(
                    text = "${if (requirement.isMet) "✓" else "○"}  ${requirement.label}",
                    color = if (requirement.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("changePassword.requirement.$index"),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("changePassword.error")) }
                    if (state.isOffline) Text("Check your connection and try again.", modifier = Modifier.testTag("changePassword.offline"))
                    if (state.success) Text("Password updated.", modifier = Modifier.testTag("changePassword.success"))
                    Button(
                        onClick = viewModel::changePassword,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().testTag("changePassword.save"),
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                        else Text("Update password")
                    }
                }
            }
        }
    }
}
