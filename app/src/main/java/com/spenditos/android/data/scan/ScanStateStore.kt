package com.spenditos.android.data.scan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small per-account state of scans that outlive the Scan screen (iOS `UserDefaults` keys `scanDismissedIds` / `scanPendingCompletions`). */
interface ScanStateStore {
    /** Scans the user cancelled while reading: hidden from Home and discarded once they are ready. */
    fun dismissed(userId: String): Set<String>
    fun saveDismissed(userId: String, ids: Set<String>)

    /** `scanId -> ScanOutcome.wire` for completions that have not reached the server yet. */
    fun pendingCompletions(userId: String): Map<String, String>
    fun savePendingCompletions(userId: String, pending: Map<String, String>)
}

@Singleton
class PrefsScanStateStore @Inject constructor(@ApplicationContext context: Context) : ScanStateStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun dismissed(userId: String): Set<String> = prefs.getStringSet("dismissed.$userId", emptySet()).orEmpty().toSet()

    override fun saveDismissed(userId: String, ids: Set<String>) {
        prefs.edit().putStringSet("dismissed.$userId", ids.take(MAX_DISMISSED).toSet()).apply()
    }

    override fun pendingCompletions(userId: String): Map<String, String> =
        prefs.getStringSet("completions.$userId", emptySet()).orEmpty()
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    override fun savePendingCompletions(userId: String, pending: Map<String, String>) {
        prefs.edit().putStringSet("completions.$userId", pending.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private companion object {
        const val PREFS = "scan_state"
        const val MAX_DISMISSED = 100
    }
}
