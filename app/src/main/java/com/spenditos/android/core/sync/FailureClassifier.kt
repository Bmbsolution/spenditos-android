package com.spenditos.android.core.sync

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.Transaction

/** What happened to one queued create. */
sealed interface SendOutcome {
    data class Created(val transaction: Transaction) : SendOutcome

    /** The server already has it (idempotency key matched): a lost response was retried. */
    data object AlreadySaved : SendOutcome

    /** Not sent: the owning session ended or changed. The entry stays queued and recoverable. */
    data object Deferred : SendOutcome

    data class Failed(val code: FailureCode, val message: String) : SendOutcome
}

/** Failure rules copied from iOS `OfflineQueueManager.classify` so both apps behave the same. */
object FailureClassifier {
    fun fromBulkResult(result: BulkCreateResult): SendOutcome {
        result.results.firstNotNullOfOrNull { it.transaction }?.let { return SendOutcome.Created(it) }
        if (result.duplicates > 0) return SendOutcome.AlreadySaved
        val reason = result.failed.firstOrNull()?.reason ?: result.results.firstNotNullOfOrNull { it.error }
            ?: return SendOutcome.Failed(FailureCode.UNKNOWN, Strings.get(R.string.core_sync_the_server_did_not_save))
        return SendOutcome.Failed(validationCode(reason), reason)
    }

    fun fromError(error: Throwable): SendOutcome.Failed = when (error) {
        is ApiError.Network -> SendOutcome.Failed(FailureCode.TRANSPORT, Strings.get(R.string.core_sync_connection_failed_we_will_retry))
        is ApiError.KeystoreUnavailable -> SendOutcome.Failed(FailureCode.TRANSPORT, Strings.get(R.string.core_sync_secure_storage_is_locked_we))
        is ApiError.Unauthorized -> SendOutcome.Failed(FailureCode.AUTHENTICATION, Strings.get(R.string.core_sync_your_session_expired_sign_in))
        is ApiError.Server -> fromStatus(error.status, error.serverMessage)
        else -> SendOutcome.Failed(FailureCode.UNKNOWN, error.message ?: Strings.get(R.string.core_sync_something_went_wrong))
    }

    private fun fromStatus(status: Int, message: String): SendOutcome.Failed = when {
        status == 400 || status == 422 -> SendOutcome.Failed(
            validationCode(message),
            message.ifBlank { Strings.get(R.string.core_sync_the_server_rejected_this_transaction) },
        )
        status == 401 || status == 403 -> SendOutcome.Failed(FailureCode.AUTHENTICATION, Strings.get(R.string.core_sync_your_session_cannot_submit_this))
        status >= 500 || status == 408 || status == 429 -> SendOutcome.Failed(FailureCode.SERVER, Strings.get(R.string.core_sync_the_server_is_temporarily_unavailable))
        else -> SendOutcome.Failed(FailureCode.PERMANENT, message.ifBlank { Strings.get(R.string.core_sync_the_server_rejected_this_transaction_2) })
    }

    private fun validationCode(message: String): FailureCode {
        val lower = message.lowercase()
        return if ("categor" in lower && "not found" in lower) FailureCode.CATEGORY_CONFLICT else FailureCode.VALIDATION
    }
}
