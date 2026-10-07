package com.spenditos.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.EnvBadge
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.Spacing

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
    IosScreen {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrandMark()
                    Text(
                        "Spenditos",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Welcome back",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    envName?.let { EnvBadge(it, Modifier.padding(top = Spacing.xs)) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoundedAuthTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = "Email",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("login.email"),
                    )
                    RoundedAuthTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = "Password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
                        modifier = Modifier.fillMaxWidth().testTag("login.password"),
                    )
                    state.error?.let { error ->
                        AuthErrorBanner(error, Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                            Modifier.testTag("login.error")
                        }
                    }
                    if (state.needsVerification) {
                        TextButton(
                            onClick = { onNeedsVerification(state.verificationEmail ?: state.email) },
                            modifier = Modifier.fillMaxWidth().testTag("login.verifyEmail"),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        ) { Text("Verify email") }
                    }
                    AuthPrimaryButton(
                        text = "Sign in",
                        onClick = viewModel::signIn,
                        enabled = state.canSubmit,
                        loading = state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(52.dp).testTag("login.submit"),
                    )
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.fillMaxWidth().testTag("login.forgotPassword"),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Forgot password?") }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))

                TextButton(
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("login.createAccount").padding(bottom = 24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Create account") }
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Canvas(Modifier.size(72.dp)) {
        val scale = size.width / 100f
        val gold = Brush.linearGradient(
            colors = listOf(Color(0xFFFFD36B), Color(0xFFE3A938)),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
        )
        val outerSweep = Path().apply {
            moveTo(25f, 66f)
            cubicTo(11f, 51f, 12f, 30f, 24f, 17f)
            cubicTo(34f, 6f, 49f, 4f, 67f, 4f)
            lineTo(86f, 4f)
            lineTo(73f, 18f)
            lineTo(58f, 18f)
            cubicTo(46f, 18f, 38f, 22f, 33f, 31f)
            cubicTo(29f, 39f, 29f, 48f, 34f, 55f)
        }
        val risingLine = Path().apply {
            moveTo(13f, 88f)
            lineTo(38f, 59f)
            lineTo(49f, 70f)
            lineTo(70f, 47f)
            lineTo(85f, 31f)
        }
        val lowerSweep = Path().apply {
            moveTo(77f, 53f)
            cubicTo(89f, 69f, 84f, 82f, 68f, 90f)
            lineTo(43f, 90f)
        }
        val style = Stroke(width = 11f, cap = StrokeCap.Square, join = StrokeJoin.Round)
        drawContext.transform.scale(scale, scale, pivot = Offset.Zero)
        drawPath(outerSweep, gold, style = style)
        drawPath(risingLine, gold, style = style)
        drawPath(lowerSweep, gold, style = style)
        val arrowHead = Path().apply {
            moveTo(77f, 25f)
            lineTo(98f, 22f)
            lineTo(97f, 44f)
            close()
        }
        drawPath(arrowHead, gold)
    }
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
    val fieldColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        Color(0xFF2C2C2E)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(fieldColor)
            .padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
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
private fun AuthErrorBanner(text: String, modifier: Modifier = Modifier, textModifier: () -> Modifier = { Modifier }) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.88f)).padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, modifier = textModifier().fillMaxWidth())
    }
}
