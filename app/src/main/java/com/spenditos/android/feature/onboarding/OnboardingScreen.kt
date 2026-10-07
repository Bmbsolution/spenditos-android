package com.spenditos.android.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import java.util.Locale

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

    IosScreen {
        Column(
            Modifier.fillMaxSize().imePadding().navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m)
                .testTag("onboarding.screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick setup", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.weight(1f))
                Text("Step ${state.page + 1} of 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("onboarding.progress"))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier.height(6.dp)
                            .then(if (index == state.page) Modifier.size(width = 24.dp, height = 6.dp) else Modifier.size(width = 8.dp, height = 6.dp))
                            .clip(CircleShape)
                            .background(if (index <= state.page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    )
                }
            }

            when (state.page) {
                0 -> WelcomePage(Modifier.weight(1f).fillMaxWidth())
                1 -> NamePage(
                    name = state.displayName,
                    onNameChange = viewModel::onNameChange,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                2 -> PreferencesPage(
                    state = state,
                    onCountryChange = viewModel::onCountryChange,
                    onCurrencyChange = viewModel::onCurrencyChange,
                    onLanguageChange = viewModel::onLanguageChange,
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> ReadyPage(state = state, modifier = Modifier.weight(1f).fillMaxWidth())
            }

            if (state.errorKind == OnboardingErrorKind.INPUT) {
                state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth(), Modifier.testTag("onboarding.inputError")) }
            }
            if (state.errorKind == OnboardingErrorKind.COMPLETE) {
                state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth(), Modifier.testTag("onboarding.error")) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.testTag("onboarding.retry"),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Retry") }
                }
            }

            Row(
                Modifier.fillMaxWidth().testTag("onboarding.actions"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.page > 0) {
                    TextButton(
                        onClick = viewModel::onBack,
                        modifier = Modifier.testTag("onboarding.back"),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Back") }
                }
                if (state.page < 3) {
                    PillButton(
                        text = "Continue",
                        onClick = viewModel::onContinue,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).height(52.dp).testTag("onboarding.continue"),
                    )
                } else {
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                        PillButton(
                            text = "Finish setup",
                            onClick = viewModel::finish,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding.finish"),
                        )
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.CenterStart).padding(start = 74.dp).size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).then(glassCardModifier(RoundedCornerShape(Radius.l))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Welcome to Spenditos", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text("Set up your account in a few steps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NamePage(name: String, onNameChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text("What should we call you?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.m))
        Text("This is shown on your dashboard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.s))
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.l)) {
            RoundedOnboardingTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = "Name",
                modifier = Modifier.fillMaxWidth().testTag("onboarding.name"),
                centered = true,
            )
        }
    }
}

@Composable
private fun PreferencesPage(
    state: OnboardingUiState,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.l).size(64.dp))
        Text("Your location and language", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text("You can always change these later in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (state.countryLoaded && !state.countryEdited && state.errorKind != OnboardingErrorKind.COUNTRY) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                            .background(LocalFinanceColors.current.income.copy(alpha = 0.08f))
                            .border(1.dp, LocalFinanceColors.current.income.copy(alpha = 0.28f), RoundedCornerShape(Radius.m))
                            .padding(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Text(countryFlag(state.country), style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f)) {
                            Text(countryName(state.country), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text("Detected currency: ${state.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LocalFinanceColors.current.income, modifier = Modifier.size(22.dp))
                    }
                }

                RoundedOnboardingTextField(
                    value = state.country,
                    onValueChange = onCountryChange,
                    placeholder = "Country code",
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.country"),
                )
                Text("Two letters, such as CA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.s))

                RoundedOnboardingTextField(
                    value = state.currency,
                    onValueChange = onCurrencyChange,
                    placeholder = "Currency",
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.currency"),
                )
                Text("Three-letter code, such as CAD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.s))

                Text("Language", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp).testTag("onboarding.languageOptions"),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf("en" to "English", "fr" to "Français").forEach { (code, label) ->
                        val selected = state.language == code
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(13.dp))
                                .background(if (selected) (if (isSystemInDarkTheme()) Color(0xFF636366) else Color.White) else Color.Transparent)
                                .clickable { onLanguageChange(code) }
                                .testTag("onboarding.language.$code"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (state.isDetectingCountry) {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.testTag("onboarding.countryLoading"),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            Text("Checking your country…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.errorKind == OnboardingErrorKind.COUNTRY) {
                    state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth(), Modifier.testTag("onboarding.countryError")) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("onboarding.countryRetry"), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(state: OnboardingUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(160.dp).clip(CircleShape).background(LocalFinanceColors.current.income.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LocalFinanceColors.current.income, modifier = Modifier.size(80.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("You're ready, ${state.displayName.trim()}.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text("${state.currency} · ${state.country} · ${if (state.language == "fr") "Français" else "English"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RoundedOnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val fieldColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color.White
    val textStyle = if (centered) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(Radius.m)).background(fieldColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(Radius.m)).padding(horizontal = Spacing.m),
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                innerTextField()
            }
        },
    )
}

@Composable
private fun OnboardingErrorBanner(text: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.88f)).padding(12.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = textModifier.fillMaxWidth())
    }
}

private fun countryName(code: String): String = Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

private fun countryFlag(code: String): String = code.uppercase(Locale.ROOT)
    .filter(Char::isLetter)
    .take(2)
    .map { character -> String(Character.toChars(0x1F1E6 + character.code - 'A'.code)) }
    .joinToString("")
