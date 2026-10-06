package com.spenditos.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.theme.Spacing

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.initialize()
        viewModel.events.collect { event -> if (event == OnboardingEvent.Finished) onFinished() }
    }

    Column(
        Modifier.fillMaxSize().padding(Spacing.screen).testTag("onboarding.screen"),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text("Quick setup", style = MaterialTheme.typography.titleLarge)
        Text("Step ${state.page + 1} of 4", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("onboarding.progress"))

        when (state.page) {
            0 -> {
                Text("Welcome to Spenditos", style = MaterialTheme.typography.headlineMedium)
                Text("Set up your account in a few steps.", style = MaterialTheme.typography.bodyLarge)
            }
            1 -> {
                Text("What should we call you?", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.name"),
                )
            }
            2 -> {
                Text("Your location and language", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.country,
                    onValueChange = viewModel::onCountryChange,
                    label = { Text("Country code") },
                    supportingText = { Text("Two letters, such as CA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.country"),
                )
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::onCurrencyChange,
                    label = { Text("Currency") },
                    supportingText = { Text("Three-letter code, such as CAD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.currency"),
                )
                Text("Language", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("onboarding.languageOptions")) {
                    listOf("en" to "English", "fr" to "Français").forEachIndexed { index, (code, label) ->
                        SegmentedButton(
                            selected = state.language == code,
                            onClick = { viewModel.onLanguageChange(code) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            modifier = Modifier.testTag("onboarding.language.$code"),
                        ) { Text(label) }
                    }
                }
                if (state.isDetectingCountry) {
                    Row(
                        Modifier.testTag("onboarding.countryLoading"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Checking your country…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.errorKind == OnboardingErrorKind.COUNTRY) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("onboarding.countryError")) }
                    TextButton(onClick = viewModel::retry, modifier = Modifier.testTag("onboarding.countryRetry")) { Text("Retry") }
                }
            }
            else -> {
                Text("You're ready, ${state.displayName.trim()}.", style = MaterialTheme.typography.headlineSmall)
                Text("${state.currency} · ${state.country} · ${if (state.language == "fr") "Français" else "English"}")
            }
        }

        if (state.errorKind == OnboardingErrorKind.INPUT) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("onboarding.inputError")) }
        }
        if (state.errorKind == OnboardingErrorKind.COMPLETE) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("onboarding.error")) }
            TextButton(onClick = viewModel::retry, modifier = Modifier.testTag("onboarding.retry")) { Text("Retry") }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().testTag("onboarding.actions"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.page > 0) {
                TextButton(onClick = viewModel::onBack, modifier = Modifier.testTag("onboarding.back")) { Text("Back") }
            }
            if (state.page < 3) {
                Button(
                    onClick = viewModel::onContinue,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).height(52.dp).testTag("onboarding.continue"),
                ) { Text("Continue") }
            } else {
                Button(
                    onClick = viewModel::finish,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).height(52.dp).testTag("onboarding.finish"),
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Finish setup")
                }
            }
        }
    }
}
