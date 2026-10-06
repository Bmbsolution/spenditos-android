package com.spenditos.android.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.data.transactions.DrainResult
import com.spenditos.android.data.transactions.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Drains the signed-in user's queue; other users' rows are never replayed. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TransactionRepository,
    private val session: SessionManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val userId = session.currentUserId ?: return Result.success()
        return when (repository.drainQueue(userId)) {
            DrainResult.DONE -> Result.success()
            DrainResult.RETRY_LATER -> Result.retry()
        }
    }
}
