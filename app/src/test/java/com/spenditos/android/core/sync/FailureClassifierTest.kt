package com.spenditos.android.core.sync

import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.BulkTransactionFailure
import com.spenditos.android.data.model.BulkTransactionResult
import com.spenditos.android.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Same table as iOS OfflineQueueManager.classify. */
class FailureClassifierTest {
    private fun code(error: Throwable) = FailureClassifier.fromError(error).code

    @Test
    fun `errors map to the iOS failure codes`() {
        assertEquals(FailureCode.TRANSPORT, code(ApiError.Network(IOException())))
        assertEquals(FailureCode.TRANSPORT, code(ApiError.KeystoreUnavailable()))
        assertEquals(FailureCode.AUTHENTICATION, code(ApiError.Unauthorized))
        assertEquals(FailureCode.VALIDATION, code(ApiError.Server(400, "Validation failed")))
        assertEquals(FailureCode.VALIDATION, code(ApiError.Server(422, "bad")))
        assertEquals(FailureCode.CATEGORY_CONFLICT, code(ApiError.Server(400, "Category cat-1 not found")))
        assertEquals(FailureCode.AUTHENTICATION, code(ApiError.Server(401, "")))
        assertEquals(FailureCode.AUTHENTICATION, code(ApiError.Server(403, "")))
        assertEquals(FailureCode.SERVER, code(ApiError.Server(500, "")))
        assertEquals(FailureCode.SERVER, code(ApiError.Server(503, "")))
        assertEquals(FailureCode.SERVER, code(ApiError.Server(408, "")))
        assertEquals(FailureCode.SERVER, code(ApiError.Server(429, "")))
        assertEquals(FailureCode.PERMANENT, code(ApiError.Server(404, "nope")))
        assertEquals(FailureCode.UNKNOWN, code(IllegalStateException("?")))
    }

    @Test
    fun `only transport and server failures retry automatically`() {
        assertEquals(setOf(FailureCode.TRANSPORT, FailureCode.SERVER), FailureCode.entries.filter { it.isRetryable }.toSet())
    }

    @Test
    fun `bulk results map to outcomes`() {
        val txn = Transaction(id = "t1", userId = "u", categoryId = "c", amount = 1.0, date = "2026-10-01")
        assertEquals(SendOutcome.Created(txn), FailureClassifier.fromBulkResult(BulkCreateResult(results = listOf(BulkTransactionResult(transaction = txn)))))
        assertEquals(SendOutcome.AlreadySaved, FailureClassifier.fromBulkResult(BulkCreateResult(duplicates = 1)))
        assertEquals(
            FailureCode.CATEGORY_CONFLICT,
            (FailureClassifier.fromBulkResult(BulkCreateResult(failed = listOf(BulkTransactionFailure(0, "category cat-9 not found")))) as SendOutcome.Failed).code,
        )
        assertEquals(
            FailureCode.VALIDATION,
            (FailureClassifier.fromBulkResult(BulkCreateResult(results = listOf(BulkTransactionResult(index = 0, error = "Invalid amount")))) as SendOutcome.Failed).code,
        )
    }
}
