package com.spenditos.android.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.w
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import java.util.Currency
import java.util.Locale

private val welcomeSlides = listOf(
    WelcomeSlideData(
        symbol = "chart.bar.fill",
        title = "Track Your Spending",
        subtitle = "See where your money goes with beautiful charts and clear insights.",
    ),
    WelcomeSlideData(
        symbol = "target",
        title = "Hit Your Goals",
        subtitle = "Set savings targets that keep you motivated every day.",
    ),
    WelcomeSlideData(
        symbol = "flame.fill",
        title = "Build Streaks",
        subtitle = "Log daily to build habits, earn XP, and unlock achievements.",
    ),
    WelcomeSlideData(
        symbol = null,
        title = "Ready to Start",
        subtitle = "Let's set up your account in just a few quick steps.",
    ),
)

private data class WelcomeSlideData(val symbol: String?, val title: String, val subtitle: String)
private data class CurrencyOption(val code: String, val name: String)

private val popularCurrencyCodes = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "XOF")
private val currencyPickerPopularCodes = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "XOF", "XAF", "NGN", "ZAR", "BRL", "MXN")

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.initialize()
        viewModel.events.collect { event -> if (event == OnboardingEvent.Finished) onFinished() }
    }

    IosScreen {
        Column(
            Modifier.fillMaxSize().imePadding().navigationBarsPadding().testTag("onboarding.screen"),
        ) {
            OnboardingPageIndicator(currentPage = state.page)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.page) {
                    0 -> WelcomePage(onGetStarted = viewModel::onContinue)
                    1 -> NamePage(name = state.displayName, onNameChange = viewModel::onNameChange)
                    2 -> PreferencesPage(
                        state = state,
                        onCountryChange = viewModel::onCountryChange,
                        onCurrencyChange = viewModel::onCurrencyChange,
                        onLanguageChange = viewModel::onLanguageChange,
                        onRetry = viewModel::retry,
                        onShowCurrencyPicker = { showCurrencyPicker = true },
                    )
                    else -> ReadyPage(state = state)
                }

                if (state.page > 0) {
                    TextButton(
                        onClick = viewModel::onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp).testTag("onboarding.back"),
                        colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                    ) { Text("Back", style = IosText.callout) }
                }
            }

            if (state.errorKind == OnboardingErrorKind.INPUT) {
                state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), Modifier.testTag("onboarding.inputError")) }
            }
            if (state.errorKind == OnboardingErrorKind.COMPLETE) {
                state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), Modifier.testTag("onboarding.error")) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.testTag("onboarding.retry"),
                        colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                    ) { Text("Retry", style = IosText.subheadline) }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 32.dp)
                    .testTag("onboarding.actions"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state.page) {
                    1 -> OnboardingActionButton(
                        text = "Continue",
                        onClick = viewModel::onContinue,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding.continue"),
                    )
                    2 -> OnboardingActionButton(
                        text = "Continue",
                        onClick = viewModel::onContinue,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding.continue"),
                    )
                    3 -> OnboardingCompletionButton(
                        loading = state.isSaving,
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding.finish"),
                    )
                }
            }
        }
    }

    if (showCurrencyPicker) {
        OnboardingCurrencyPicker(
            selectedCurrency = state.currency,
            onCurrencySelected = viewModel::onCurrencyChange,
            onDismiss = { showCurrencyPicker = false },
        )
    }
}

@Composable
private fun OnboardingPageIndicator(currentPage: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.l).testTag("onboarding.progress"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                Modifier.height(6.dp)
                    .then(if (index == currentPage) Modifier.size(width = 24.dp, height = 6.dp) else Modifier.size(width = 8.dp, height = 6.dp))
                    .clip(CircleShape)
                    .background(if (index <= currentPage) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.45f)),
            )
        }
    }
}

@Composable
private fun WelcomePage(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { welcomeSlides.size })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val slide = welcomeSlides[page]
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(120.dp).then(glassCardModifier(RoundedCornerShape(Radius.l), shadowAlpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val image = slide.symbol?.let { SfSymbols.icon(it) } ?: Icons.Filled.ArrowCircleRight
                Icon(image, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(72.dp))
            }
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(slide.title, style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
                Text(slide.subtitle, style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = if (page == welcomeSlides.lastIndex) 0.dp else 24.dp),
            ) {
                welcomeSlides.indices.forEach { index ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (index == pagerState.currentPage) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f)),
                    )
                }
            }
            if (page == welcomeSlides.lastIndex) {
            OnboardingActionButton("Get Started", onGetStarted, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).testTag("onboarding.continue"))
            }
        }
    }
}

@Composable
private fun NamePage(name: String, onNameChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(77.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("What should we call you?", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text("This is shown on your dashboard", style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            OnboardingTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = "Your name",
                style = IosText.headline,
                centered = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding.name"),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PreferencesPage(
    state: OnboardingUiState,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onRetry: () -> Unit,
    onShowCurrencyPicker: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
        Icon(SfSymbols.icon("gearshape.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(77.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Set your preferences", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text("You can always change these later in Settings", style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.countryLoaded && !state.countryEdited && state.errorKind != OnboardingErrorKind.COUNTRY) {
                DetectedCountryCard(state.country, state.currency)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text("Currency", style = IosText.subheadline, color = IosColors.textSecondary)
                val quickPicks = listOf(state.currency) + popularCurrencyCodes.filter { it != state.currency }.take(3)
                quickPicks.chunked(2).forEach { rowCodes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        rowCodes.forEach { code ->
                            CurrencyChoiceCard(
                                option = CurrencyOption(code, currencyName(code)),
                                selected = state.currency == code,
                                onClick = { onCurrencyChange(code) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowCodes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onShowCurrencyPicker, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("All currencies", style = IosText.subheadline, color = IosColors.spendOrange)
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Language", style = IosText.subheadline, color = IosColors.textSecondary)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("onboarding.languageOptions"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    LanguageChip("en", "English", state.language == "en", onLanguageChange)
                    LanguageChip("fr", "Français", state.language == "fr", onLanguageChange)
                }
            }

            // Android lets users enter a country and currency code when detection or a currency search is unavailable.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OnboardingTextField(
                    value = state.country,
                    onValueChange = onCountryChange,
                    placeholder = "Country code",
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.country"),
                )
                Text("Two letters, such as CA", style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.padding(start = Spacing.s))
                OnboardingTextField(
                    value = state.currency,
                    onValueChange = onCurrencyChange,
                    placeholder = "Currency",
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.currency"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                )
                Text("Three-letter code, such as CAD", style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.padding(start = Spacing.s))
            }

            if (state.isDetectingCountry) {
                Row(
                    Modifier.fillMaxWidth().testTag("onboarding.countryLoading"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = IosColors.spendOrange, strokeWidth = 2.dp)
                    Text("Checking your country…", style = IosText.callout, color = IosColors.textSecondary)
                }
            }
            if (state.errorKind == OnboardingErrorKind.COUNTRY) {
                state.error?.let { OnboardingErrorBanner(it, Modifier.fillMaxWidth(), Modifier.testTag("onboarding.countryError")) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("onboarding.countryRetry"), colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange)) {
                        Text("Retry", style = IosText.subheadline)
                    }
                }
            }
        }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DetectedCountryCard(country: String, currency: String) {
    val income = IosColors.income
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, income.copy(alpha = 0.3f), RoundedCornerShape(Radius.m)).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(countryFlag(country), style = IosText.system(28))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(countryName(country), style = IosText.body, color = IosColors.textPrimary)
            Text("Detected currency: $currency", style = IosText.subheadline, color = IosColors.textSecondary)
        }
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = income, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CurrencyChoiceCard(option: CurrencyOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.l)
    val cardColor = if (selected) IosColors.spendOrange else MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = modifier.heightIn(min = 100.dp).dropShadow(
            shape,
            Shadow(radius = 20.dp, color = Color.Black, offset = DpOffset(0.dp, 3.dp), alpha = 0.06f),
        ).clip(shape).background(cardColor).border(1.dp, if (selected) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
        ) {
            Text(currencySymbol(option.code), style = IosText.system(36, FontWeight.Bold), color = if (selected) Color.White else IosColors.textPrimary)
            Text(option.code, style = IosText.headline, color = if (selected) Color.White else IosColors.textPrimary)
            Text(option.name, style = IosText.subheadline, color = if (selected) Color.White.copy(alpha = 0.85f) else IosColors.textSecondary, textAlign = TextAlign.Center)
        }
        if (selected) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = "Selected", tint = Color.White, modifier = Modifier.padding(8.dp).size(20.dp))
            }
        }
    }
}

@Composable
private fun LanguageChip(code: String, label: String, selected: Boolean, onLanguageChange: (String) -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    Box(
        Modifier.clip(shape).background(if (selected) IosColors.spendOrange else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.55f), shape)
            .clickable(role = Role.Button) { onLanguageChange(code) }.padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("onboarding.language.$code"),
    ) {
        Text(label, style = IosText.subheadline.w(FontWeight.Bold), color = if (selected) Color.White else IosColors.textPrimary)
    }
}

@Composable
private fun ReadyPage(state: OnboardingUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        val readyBrush = Brush.linearGradient(listOf(IosColors.income, Color(0xFF30B0C7)))
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(96.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(brush = readyBrush, blendMode = BlendMode.SrcIn)
                },
        )
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("Your essentials are ready.", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "Log one transaction now to see your financial picture.",
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.m)) {
            SummaryRow(icon = "dollarsign.circle", text = "Currency: ${state.currency}")
            SummaryRow(icon = "square.grid.2x2", text = "${OnboardingData.categorySeeds.size} categories ready")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SummaryRow(icon: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        val image = if (icon == "square.grid.2x2") Icons.Outlined.GridView else SfSymbols.icon(icon)
        Icon(image, contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(24.dp))
        Text(text, style = IosText.callout, color = IosColors.textSecondary)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = IosText.body,
    centered: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val shape = RoundedCornerShape(if (centered) Radius.m else 12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, IosColors.separator.copy(alpha = 0.55f), shape).padding(16.dp),
        singleLine = true,
        textStyle = style.copy(color = IosColors.textPrimary, textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(IosColors.spendOrange),
        keyboardOptions = keyboardOptions,
        decorationBox = { innerTextField ->
            Box(contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style, color = IosColors.textSecondary, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                innerTextField()
            }
        },
    )
}

@Composable
private fun OnboardingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(Radius.l)
    val buttonColor = if (enabled) IosColors.spendOrange else IosColors.separator.copy(alpha = 0.2f)
    Box(
        modifier.heightIn(min = 52.dp).clip(shape).background(buttonColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.headline, color = if (enabled) Color.White else IosColors.textTertiary)
    }
}

@Composable
private fun OnboardingCompletionButton(loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.l)
    Box(
        modifier.heightIn(min = 52.dp).clip(shape)
            .background(Brush.horizontalGradient(listOf(IosColors.spendOrange, IosColors.spendGold)))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Text("Log First Transaction", style = IosText.headline, color = Color.White)
        }
    }
}

@Composable
private fun OnboardingErrorBanner(text: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.8f)).padding(16.dp)) {
        Text(text, color = Color.White, style = IosText.subheadline.w(FontWeight.Bold), modifier = textModifier.fillMaxWidth())
    }
}

@Composable
private fun OnboardingCurrencyPicker(selectedCurrency: String, onCurrencySelected: (String) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val allCurrencies = remember {
        Currency.getAvailableCurrencies().map { CurrencyOption(it.currencyCode, currencyPickerName(it.currencyCode)) }
            .distinctBy(CurrencyOption::code).sortedBy(CurrencyOption::name)
    }
    val popular = remember(allCurrencies) { currencyPickerPopularCodes.mapNotNull { code -> allCurrencies.firstOrNull { it.code == code } } }
    val filtered = remember(search, allCurrencies) {
        if (search.isBlank()) allCurrencies else allCurrencies.filter {
            it.code.contains(search, ignoreCase = true) || it.name.contains(search, ignoreCase = true)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = Spacing.screen)) {
            Text("Currency", style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m))
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().height(44.dp).then(subtleGlassModifier(RoundedCornerShape(12.dp))).padding(horizontal = Spacing.m),
                singleLine = true,
                textStyle = IosText.body.copy(color = IosColors.textPrimary),
                cursorBrush = SolidColor(IosColors.spendOrange),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (search.isEmpty()) Text("Search currencies", style = IosText.body, color = IosColors.textSecondary)
                        inner()
                    }
                },
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (search.isBlank()) {
                    item { CurrencySection("Popular", popular, selectedCurrency, onCurrencySelected, onDismiss) }
                    item {
                        CurrencySection("All currencies", allCurrencies.filterNot { item -> popular.any { it.code == item.code } }, selectedCurrency, onCurrencySelected, onDismiss)
                    }
                } else {
                    item { CurrencySection(null, filtered, selectedCurrency, onCurrencySelected, onDismiss) }
                }
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun CurrencySection(
    title: String?,
    options: List<CurrencyOption>,
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        title?.let { Text(it, style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.padding(start = 4.dp)) }
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l)) {
            options.forEachIndexed { index, option ->
                CurrencyPickerRow(option, selectedCurrency == option.code) {
                    onCurrencySelected(option.code)
                    onDismiss()
                }
                if (index < options.lastIndex) HorizontalDivider(Modifier.padding(start = Spacing.m), color = IosColors.separator)
            }
        }
    }
}

@Composable
private fun CurrencyPickerRow(option: CurrencyOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(option.code, style = IosText.body.copy(fontFamily = FontFamily.Monospace), color = IosColors.textPrimary)
            Text(option.name, style = IosText.caption, color = IosColors.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        if (selected) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
    }
}

private fun countryName(code: String): String = Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

private fun countryFlag(code: String): String = code.uppercase(Locale.ROOT).filter(Char::isLetter).take(2)
    .map { character -> String(Character.toChars(0x1F1E6 + character.code - 'A'.code)) }.joinToString("")

private fun currencySymbol(code: String): String = when (code) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    "CAD" -> "C$"
    "AUD" -> "A$"
    "CHF" -> "Fr"
    "INR" -> "₹"
    "SEK", "NOK", "DKK" -> "kr"
    "NZD" -> "NZ$"
    "BRL" -> "R$"
    "MXN" -> "Mex$"
    "ZAR" -> "R"
    else -> code
}

private fun currencyName(code: String): String = when (code) {
    "USD" -> "US Dollar"
    "EUR" -> "Euro"
    "GBP" -> "British Pound"
    "CAD" -> "Canadian Dollar"
    "AUD" -> "Australian Dollar"
    "JPY" -> "Japanese Yen"
    "CHF" -> "Swiss Franc"
    "CNY" -> "Chinese Yuan"
    "INR" -> "Indian Rupee"
    "MXN" -> "Mexican Peso"
    "BRL" -> "Brazilian Real"
    "ZAR" -> "South African Rand"
    "SEK" -> "Swedish Krona"
    "NOK" -> "Norwegian Krone"
    "DKK" -> "Danish Krone"
    "NZD" -> "New Zealand Dollar"
    "SGD" -> "Singapore Dollar"
    "HKD" -> "Hong Kong Dollar"
    "XOF" -> "West African CFA"
    "XAF" -> "Central African CFA"
    else -> runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)
}

private fun currencyPickerName(code: String): String = when (code) {
    "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "MXN", "BRL", "ZAR",
    "SEK", "NOK", "DKK", "NZD", "SGD", "HKD", "XOF", "XAF" -> currencyName(code)
    else -> runCatching { Currency.getInstance(code).getDisplayName(Locale.US) }.getOrDefault(code)
}
