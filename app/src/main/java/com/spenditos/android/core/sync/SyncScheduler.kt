package com.spenditos.android.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

fun interface SyncRequester {
    fun requestSync()
}

/**
 * Enqueues [SyncWorker] as unique work. WorkManager waits for connectivity and
 * survives process death, which covers iOS's "sync on reconnect" and "replay at launch".
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncRequester {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: an item queued while a drain is running gets its own pass.
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "sync-mutations"
    }
}
