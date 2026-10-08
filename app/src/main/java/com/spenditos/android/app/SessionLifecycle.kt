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
    private val purchases: com.spenditos.android.data.purchases.PurchasesService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            // Run on every arrival of a SignedIn state, not only on distinct ones: after
            // SignedIn -> SignedOut -> SignedIn (expiry, then the same user signs back in)
            // the queue replay and cache warm-up must run again. The first emission counts
            // as an arrival; a SignedIn state repeated for the same user does not.
            var previous: SessionState? = null
            session.state.collect { state ->
                val signedIn = state as? SessionState.SignedIn
                if (signedIn != null && (previous as? SessionState.SignedIn)?.userId != signedIn.userId) {
                    runCatching { transactions.requeueAuthFailures(signedIn.userId) }
                    sync.requestSync()
                    runCatching { settings.refresh() }
                    runCatching { categories.refresh() }
                    runCatching { push.syncRegistration() }
                    runCatching { purchases.logIn(signedIn.userId) }
                }
                previous = state
            }
        }
    }
}
