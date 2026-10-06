package com.spenditos.android.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.feature.auth.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(val session: SessionManager, val environment: AppEnvironment) : ViewModel()

/** Launch gate (iOS RootView): signed out → login; signed in → tabs. Onboarding gate lands in Wave A. */
@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.session.state.collectAsStateWithLifecycle()
    val envName = viewModel.environment.displayName.takeIf { viewModel.environment.showBadge }
    val loginSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.session.expired.collect { loginSnackbar.showSnackbar("Your session expired. Please sign in again.") }
    }
    when (state) {
        SessionState.SignedOut -> LoginScreen(envName = envName, snackbarHostState = loginSnackbar)
        // Keyed by user: every screen and ViewModel below is rebuilt for a different account.
        is SessionState.SignedIn -> key((state as SessionState.SignedIn).userId) { MainScaffold(envName = envName) }
    }
}
