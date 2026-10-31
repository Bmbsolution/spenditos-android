package com.spenditos.android.feature.auth

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import com.spenditos.android.ui.components.DismissKeyboardWhenInactive
import com.spenditos.android.ui.components.SfSymbols
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.EnvBadge
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.dismissKeyboardOnScroll
import com.spenditos.android.ui.components.dismissKeyboardOnTapOutside
import com.spenditos.android.ui.components.rememberKeyboardDismisser
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

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
    val dismissKeyboard = rememberKeyboardDismisser()
    DismissKeyboardWhenInactive()
    IosScreen {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .dismissKeyboardOnTapOutside().dismissKeyboardOnScroll()
                    .verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrandMark()
                    Text(stringResource(R.string.auth_spenditos), style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary)
                    Text(stringResource(R.string.auth_welcome_back), style = IosText.subheadline, color = IosColors.textSecondary)
                    envName?.let { EnvBadge(it, Modifier.padding(top = Spacing.xs)) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoundedAuthTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = stringResource(R.string.auth_email),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("login.email"),
                    )
                    RoundedAuthTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = stringResource(R.string.auth_password),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { dismissKeyboard(); viewModel.signIn() }),
                        modifier = Modifier.fillMaxWidth().testTag("login.password"),
                    )
                    if (state.error != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AuthErrorBanner(state.error.orEmpty(), Modifier.fillMaxWidth(), Modifier.testTag("login.error"))
                            if (state.needsVerification) {
                                Button(
                                    onClick = { onNeedsVerification(state.verificationEmail ?: state.email) },
                                    modifier = Modifier.fillMaxWidth().testTag("login.verifyEmail"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.spendOrange, contentColor = Color.White),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Icon(SfSymbols.icon("envelope.badge.fill"), contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(R.string.auth_enter_verification_code), style = IosText.subheadline.w(FontWeight.Bold), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                    LoadingActionButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = viewModel::signIn,
                        enabled = state.canSubmit,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().testTag("login.submit"),
                    )
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.fillMaxWidth().testTag("login.forgotPassword"),
                        colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                    ) { Text(stringResource(R.string.auth_forgot_password), style = IosText.subheadline) }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.4f))

                TextButton(
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp).testTag("login.createAccount"),
                    colors = ButtonDefaults.textButtonColors(contentColor = IosColors.spendOrange),
                ) { Text(stringResource(R.string.auth_create_account), style = IosText.subheadline) }
            }
        }
    }
}

/** iOS `Image("BrandMark")` at 72pt: the same gold artwork as the app icon. */
@Composable
private fun BrandMark() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.spenditos.android.R.drawable.brand_mark),
        contentDescription = null,
        modifier = Modifier.size(72.dp),
    )
}

@Composable
private fun RoundedAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
        keyboardActions = keyboardActions,
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
private fun LoadingActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
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
private fun AuthErrorBanner(text: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.8f)).padding(16.dp),
    ) {
        Text(text, style = IosText.subheadline.w(FontWeight.Bold), color = Color.White, modifier = textModifier.fillMaxWidth())
    }
}
