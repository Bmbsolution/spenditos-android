package com.spenditos.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.data.onboarding.OnboardingActions
import com.spenditos.android.feature.auth.ForgotPasswordScreen
import com.spenditos.android.feature.auth.LoginScreen
import com.spenditos.android.feature.auth.RegisterScreen
import com.spenditos.android.feature.auth.VerifyEmailScreen
import com.spenditos.android.feature.onboarding.OnboardingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val session: SessionManager,
    val environment: AppEnvironment,
    val onboarding: OnboardingActions,
) : ViewModel()

/** Where the signed-out flow is. Saved across rotation; the email travels with the verify step. */
private sealed interface AuthStep {
    data object Login : AuthStep
    data object Register : AuthStep
    data class Verify(val email: String) : AuthStep
    data object Forgot : AuthStep
}

private enum class Gate { Checking, Onboarding, Ready }

/**
 * Launch gate (iOS RootView): signed out → login/register/verify/forgot; signed in →
 * (backend `setupCompleted` check, fail-open) onboarding or the main tabs.
 */
@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.session.state.collectAsStateWithLifecycle()
    val envName = viewModel.environment.displayName.takeIf { viewModel.environment.showBadge }
    val loginSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.session.expired.collect { loginSnackbar.showSnackbar("Your session expired. Please sign in again.") }
    }
    when (val session = state) {
        SessionState.SignedOut -> SignedOutFlow(envName, loginSnackbar)
        // Keyed by user: every screen and ViewModel below is rebuilt for a different account.
        is SessionState.SignedIn -> key(session.userId) {
            var gate by rememberSaveable { mutableStateOf(Gate.Checking) }
            LaunchedEffect(Unit) {
                if (gate == Gate.Checking) gate = if (viewModel.onboarding.needsOnboarding()) Gate.Onboarding else Gate.Ready
            }
            when (gate) {
                Gate.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Gate.Onboarding -> OnboardingScreen(onFinished = { gate = Gate.Ready })
                Gate.Ready -> MainScaffold(envName = envName)
            }
        }
    }
}

@Composable
private fun SignedOutFlow(envName: String?, snackbar: SnackbarHostState) {
    var step by rememberSaveable(stateSaver = AuthStepSaver) { mutableStateOf<AuthStep>(AuthStep.Login) }
    val scope = rememberCoroutineScope()
    fun backToLogin(notice: String) {
        step = AuthStep.Login
        scope.launch { snackbar.showSnackbar(notice) }
    }
    when (val current = step) {
        AuthStep.Login -> LoginScreen(
            envName = envName,
            snackbarHostState = snackbar,
            onCreateAccount = { step = AuthStep.Register },
            onForgotPassword = { step = AuthStep.Forgot },
            onNeedsVerification = { email -> step = AuthStep.Verify(email) },
        )
        AuthStep.Register -> RegisterScreen(
            onRegistered = { email -> step = AuthStep.Verify(email) },
            onBack = { step = AuthStep.Login },
        )
        is AuthStep.Verify -> VerifyEmailScreen(
            email = current.email,
            onVerified = { backToLogin("Email confirmed. Sign in to continue.") },
            onBack = { step = AuthStep.Login },
        )
        AuthStep.Forgot -> ForgotPasswordScreen(
            onDone = { backToLogin("Password updated. Sign in with your new password.") },
            onBack = { step = AuthStep.Login },
        )
    }
}

/** Saves the step as "kind" or "verify|email"; the email is only kept for the verify step. */
private val AuthStepSaver = Saver<AuthStep, String>(
    save = { step ->
        when (step) {
            AuthStep.Login -> "login"
            AuthStep.Register -> "register"
            AuthStep.Forgot -> "forgot"
            is AuthStep.Verify -> "verify|${step.email}"
        }
    },
    restore = { saved ->
        when {
            saved == "register" -> AuthStep.Register
            saved == "forgot" -> AuthStep.Forgot
            saved.startsWith("verify|") -> AuthStep.Verify(saved.removePrefix("verify|"))
            else -> AuthStep.Login
        }
    },
)
