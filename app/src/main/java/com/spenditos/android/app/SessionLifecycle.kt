package com.spenditos.android.app

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lifetime reactions to a session starting (launch with a saved session, or a
 * fresh sign-in): replay the queue (iOS replays after login and at launch) and warm
 * the caches. All best-effort: a failure here never blocks the UI.
 */
@Singleton
class SessionLifecycle @Inject constructor(
    private val session: SessionManager,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val settings: UserSettingsRepository,
    private val sync: SyncRequester,
    private val push: com.spenditos.android.data.push.PushRegistrar,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            session.state.filterIsInstance<SessionState.SignedIn>().distinctUntilChanged().collect { signedIn ->
                runCatching { transactions.requeueAuthFailures(signedIn.userId) }
                sync.requestSync()
                runCatching { settings.refresh() }
                runCatching { categories.refresh() }
                runCatching { push.syncRegistration() }
            }
        }
    }
}
