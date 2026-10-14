package com.spenditos.android.data.scan

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account as the Scan engine needs it: scoping of its local state and binding of its writes. */
interface ScanSession {
    val userId: String?

    /** The account and session generation a write is bound to (see [SessionBinding]); null when signed out. */
    val binding: SessionBinding?

    /** The signed-in user id (null when signed out), emitted again on every change. */
    val userIds: Flow<String?>
}

@Singleton
class SessionManagerScanSession @Inject constructor(private val session: SessionManager) : ScanSession {
    override val userId: String? get() = session.currentUserId
    override val binding: SessionBinding? get() = session.currentBinding
    override val userIds: Flow<String?> = session.state.map { (it as? SessionState.SignedIn)?.userId }.distinctUntilChanged()
}
