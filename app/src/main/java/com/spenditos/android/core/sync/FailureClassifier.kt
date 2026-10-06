package com.spenditos.android.core.sync

import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.Transaction

/** What happened to one queued create. */
sealed interface SendOutcome {
    data class Created(val transaction: Transaction) : SendOutcome

    /** The server already has it (idempotency key matched): a lost response was retried. */
    data object AlreadySaved : SendOutcome

    data class Failed(val code: FailureCode, val message: String) : SendOutcome
}

/** Failure rules copied from iOS `OfflineQueueManager.classify` so both apps behave the same. */
object FailureClassifier {
    fun fromBulkResult(result: BulkCreateResult): SendOutcome {
        result.results.firstNotNullOfOrNull { it.transaction }?.let { return SendOutcome.Created(it) }
        if (result.duplicates > 0) return SendOutcome.AlreadySaved
        val reason = result.failed.firstOrNull()?.reason ?: result.results.firstNotNullOfOrNull { it.error }
            ?: return SendOutcome.Failed(FailureCode.UNKNOWN, "The server did not save this transaction.")
        return SendOutcome.Failed(validationCode(reason), reason)
    }

    fun fromError(error: Throwable): SendOutcome.Failed = when (error) {
        is ApiError.Network -> SendOutcome.Failed(FailureCode.TRANSPORT, "Connection failed. We will retry when you are online.")
        is ApiError.KeystoreUnavailable -> SendOutcome.Failed(FailureCode.TRANSPORT, "Secure storage is locked. We will retry shortly.")
        is ApiError.Unauthorized -> SendOutcome.Failed(FailureCode.AUTHENTICATION, "Your session expired. Sign in, then retry this transaction.")
        is ApiError.Server -> fromStatus(error.status, error.serverMessage)
        else -> SendOutcome.Failed(FailureCode.UNKNOWN, error.message ?: "Something went wrong.")
    }

    private fun fromStatus(status: Int, message: String): SendOutcome.Failed = when {
        status == 400 || status == 422 -> SendOutcome.Failed(
            validationCode(message),
            message.ifBlank { "The server rejected this transaction. Review it and try again." },
        )
        status == 401 || status == 403 -> SendOutcome.Failed(FailureCode.AUTHENTICATION, "Your session cannot submit this transaction. Sign in and retry.")
        status >= 500 || status == 408 || status == 429 -> SendOutcome.Failed(FailureCode.SERVER, "The server is temporarily unavailable. We will retry this transaction.")
        else -> SendOutcome.Failed(FailureCode.PERMANENT, message.ifBlank { "The server rejected this transaction." })
    }

    private fun validationCode(message: String): FailureCode {
        val lower = message.lowercase()
        return if ("categor" in lower && "not found" in lower) FailureCode.CATEGORY_CONFLICT else FailureCode.VALIDATION
    }
}
