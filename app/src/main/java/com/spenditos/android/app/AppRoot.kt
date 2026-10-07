package com.spenditos.android.app

import androidx.compose.foundation.layout.Box
import com.spenditos.android.feature.lock.AppLockScreen
import com.spenditos.android.core.lock.LockState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
    val lock: com.spenditos.android.core.lock.AppLockController,
    val authenticator: com.spenditos.android.core.lock.BiometricAuthenticator,
) : ViewModel() {
    /** ViewModels of the signed-out steps: kept across rotation, dropped when the step changes. */
    internal val authSteps = StepViewModelStores()

    override fun onCleared() = authSteps.clearAll()
}

/**
 * One [ViewModelStore] per signed-out step. Entering a step clears every other step's store, so a
 * finished reset or verification (and any typed password) never comes back the next time the
 * step opens, while a rotation (same step) keeps the form.
 */
internal class StepViewModelStores {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun enter(key: String): ViewModelStore {
        stores.keys.filter { it != key }.forEach { stores.remove(it)?.clear() }
        return stores.getOrPut(key) { ViewModelStore() }
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

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
    val lockState by viewModel.lock.state.collectAsStateWithLifecycle()
    val envName = viewModel.environment.displayName.takeIf { viewModel.environment.showBadge }
    val loginSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.session.expired.collect { loginSnackbar.showSnackbar("Your session expired. Please sign in again.") }
    }
    when (val session = state) {
        SessionState.SignedOut -> SignedOutFlow(envName, loginSnackbar, viewModel.authSteps)
        // Keyed by user: every screen and ViewModel below is rebuilt for a different account.
        is SessionState.SignedIn -> Box(Modifier.fillMaxSize()) {
            // While the lock is up the content below must not be reachable by accessibility
            // services either (TalkBack, uiautomator), not just covered visually.
            Box(Modifier.fillMaxSize().then(if (lockState != LockState.UNLOCKED) Modifier.clearAndSetSemantics { } else Modifier)) {
            key(session.userId) {
            LaunchedEffect(Unit) { viewModel.authSteps.clearAll() }
            var gate by rememberSaveable { mutableStateOf(Gate.Checking) }
            LaunchedEffect(Unit) {
                if (gate == Gate.Checking) gate = if (viewModel.onboarding.needsOnboarding()) Gate.Onboarding else Gate.Ready
            }
            when (gate) {
                Gate.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                // Activity-scoped (keeps SavedStateHandle across process death) but keyed by user,
                // so another account on this device never sees this account's half-filled wizard.
                Gate.Onboarding -> OnboardingScreen(
                    onFinished = { gate = Gate.Ready },
                    viewModel = hiltViewModel(key = "onboarding:${session.userId}"),
                )
                Gate.Ready -> MainScaffold(envName = envName)
            }
        }
            }
            // Opaque and on top: the lock never reveals content while it is up or coming up.
            if (lockState != LockState.UNLOCKED) {
                if (lockState == LockState.LOCKED) {
                    AppLockScreen(authenticator = viewModel.authenticator, onUnlocked = viewModel.lock::unlock)
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }
}

@Composable
private fun SignedOutFlow(envName: String?, snackbar: SnackbarHostState, stores: StepViewModelStores) {
    var step by rememberSaveable(stateSaver = AuthStepSaver) { mutableStateOf<AuthStep>(AuthStep.Login) }
    val scope = rememberCoroutineScope()
    fun backToLogin(notice: String) {
        step = AuthStep.Login
        scope.launch { snackbar.showSnackbar(notice) }
    }
    StepViewModels(stores, saveKey(step)) {
        AuthStepContent(step, envName, snackbar, onStep = { step = it }, backToLogin = ::backToLogin)
    }
}

/** Provides the step's retained [ViewModelStore] to `hiltViewModel()` calls in [content]. */
@Composable
private fun StepViewModels(stores: StepViewModelStores, key: String, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" }
    val owner = remember(stores, key, parent) { StepOwner(stores.enter(key), parent) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class StepOwner(override val viewModelStore: ViewModelStore, parent: ViewModelStoreOwner) :
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    private val parentFactory = parent as HasDefaultViewModelProviderFactory
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = parentFactory.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras(parentFactory.defaultViewModelCreationExtras).apply { set(VIEW_MODEL_STORE_OWNER_KEY, this@StepOwner) }
}

@Composable
private fun AuthStepContent(
    step: AuthStep,
    envName: String?,
    snackbar: SnackbarHostState,
    onStep: (AuthStep) -> Unit,
    backToLogin: (String) -> Unit,
) {
    when (val current = step) {
        AuthStep.Login -> LoginScreen(
            envName = envName,
            snackbarHostState = snackbar,
            onCreateAccount = { onStep(AuthStep.Register) },
            onForgotPassword = { onStep(AuthStep.Forgot) },
            onNeedsVerification = { email -> onStep(AuthStep.Verify(email)) },
        )
        AuthStep.Register -> RegisterScreen(
            onRegistered = { email -> onStep(AuthStep.Verify(email)) },
            onBack = { onStep(AuthStep.Login) },
        )
        is AuthStep.Verify -> VerifyEmailScreen(
            email = current.email,
            onVerified = { backToLogin("Email confirmed. Sign in to continue.") },
            onBack = { onStep(AuthStep.Login) },
        )
        AuthStep.Forgot -> ForgotPasswordScreen(
            onDone = { backToLogin("Password updated. Sign in with your new password.") },
            onBack = { onStep(AuthStep.Login) },
        )
    }
}

/** Saves the step as "kind" or "verify|email"; the email is only kept for the verify step. */
private val AuthStepSaver = Saver<AuthStep, String>(
    save = { step -> saveKey(step) },
    restore = { saved ->
        when {
            saved == "register" -> AuthStep.Register
            saved == "forgot" -> AuthStep.Forgot
            saved.startsWith("verify|") -> AuthStep.Verify(saved.removePrefix("verify|"))
            else -> AuthStep.Login
        }
    },
)

private fun saveKey(step: AuthStep): String = when (step) {
    AuthStep.Login -> "login"
    AuthStep.Register -> "register"
    AuthStep.Forgot -> "forgot"
    is AuthStep.Verify -> "verify|${step.email}"
}
