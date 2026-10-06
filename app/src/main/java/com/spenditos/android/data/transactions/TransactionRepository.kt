package com.spenditos.android.data.transactions

import androidx.room.withTransaction
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.db.MutationKind
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.PendingMutationEntity
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FailureClassifier
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SendOutcome
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the add form needs; mirrors the iOS manual-entry body. */
data class NewTransaction(
    val amount: Double,
    val categoryId: String,
    val description: String,
    val date: LocalDate,
    val mood: TransactionMood? = null,
)

sealed interface CreateResult {
    data object Saved : CreateResult

    /** Stored on the device and queued; the worker will send it. */
    data object SavedOffline : CreateResult

    /** The server refused it for a reason a retry won't fix; nothing was kept. */
    data class Rejected(val code: FailureCode, val message: String) : CreateResult
}

enum class DrainResult { DONE, RETRY_LATER }

/**
 * Transactions: cache-first reads and the offline create queue, which are one
 * system (docs/ARCHITECTURE.md §5). Every create goes through
 * `POST /transactions/bulk` with a persisted idempotency key (DECISIONS D2).
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val api: TransactionsApi,
    private val db: SpenditosDatabase,
    private val session: SessionManager,
    private val connectivity: ConnectivityStatus,
    private val sync: SyncRequester,
    private val invalidation: FinanceInvalidationBus,
) {
    private val transactions = db.transactions()
    private val mutations = db.mutations()

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>> {
        val userId = session.currentUserId ?: return emptyFlow()
        return transactions.observeRange(userId, start.toString(), end.toString())
    }

    fun observeQueue(): Flow<List<PendingMutationEntity>> {
        val userId = session.currentUserId ?: return emptyFlow()
        return mutations.observeAll(userId)
    }

    /** Fetches every page of [start]..[end] and replaces the cached synced rows of that range. */
    suspend fun refreshRange(start: LocalDate, end: LocalDate) {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val fetched = mutableListOf<Transaction>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = apiCall { api.list(start.toString(), end.toString(), cursor = cursor) }
            fetched += page.transactions
            cursor = page.pagination.nextCursor
            if (!page.pagination.hasMore || cursor == null) {
                transactions.replaceSyncedRange(userId, start.toString(), end.toString(), fetched.map { it.toEntity() })
                return
            }
        }
        // Range larger than MAX_PAGES * 200: keep what we have without deleting anything.
        transactions.upsert(fetched.map { it.toEntity() })
    }

    suspend fun create(draft: NewTransaction): CreateResult {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val mutationId = UUID.randomUUID().toString()
        val localId = "local-$mutationId"
        val item = BulkTransactionItem(
            amount = draft.amount,
            date = draft.date.toString(),
            categoryId = draft.categoryId,
            description = draft.description.trim(),
            mood = draft.mood,
            idempotencyKeyId = mutationId,
        )
        val now = System.currentTimeMillis()
        db.withTransaction {
            transactions.upsert(
                listOf(
                    TransactionEntity(
                        id = localId, userId = userId, amount = item.amount, categoryId = item.categoryId,
                        description = item.description, date = item.date, mood = draft.mood?.name, templateId = null,
                        source = "manual", createdAt = null, updatedAt = null, syncStatus = SyncStatus.PENDING,
                        clientMutationId = mutationId,
                    ),
                ),
            )
            mutations.insert(
                PendingMutationEntity(
                    id = mutationId, userId = userId, kind = MutationKind.CREATE_TRANSACTION,
                    payloadJson = SpenditosJson.encodeToString(BulkTransactionItem.serializer(), item),
                    localTransactionId = localId, status = MutationStatus.PENDING, createdAt = now,
                ),
            )
        }
        invalidation.invalidate(Aggregate.TRANSACTION)

        if (!connectivity.isOnline.value) {
            sync.requestSync()
            return CreateResult.SavedOffline
        }
        return when (val outcome = send(mutationId)) {
            null -> CreateResult.SavedOffline // the worker claimed it first
            is SendOutcome.Created, SendOutcome.AlreadySaved -> CreateResult.Saved
            is SendOutcome.Failed -> if (outcome.code.isRetryable) {
                sync.requestSync()
                CreateResult.SavedOffline
            } else {
                // Same as iOS: an online save the server refuses is not kept; the form shows why.
                discard(mutationId)
                CreateResult.Rejected(outcome.code, outcome.message)
            }
        }
    }

    /** Sends queued creates oldest first until the queue is empty or the network/server fails. */
    suspend fun drainQueue(userId: String): DrainResult {
        mutations.releaseStaleClaims(before = System.currentTimeMillis() - STALE_CLAIM_MS)
        while (true) {
            val next = mutations.nextPending(userId) ?: return DrainResult.DONE
            val outcome = send(next.id) ?: continue
            if (outcome is SendOutcome.Failed && outcome.code.isRetryable) return DrainResult.RETRY_LATER
        }
    }

    /** Manual retry from the "Not synced" screen. */
    suspend fun retry(mutationId: String) {
        mutations.release(mutationId, MutationStatus.PENDING, null, null)
        mutations.get(mutationId)?.let { transactions.setStatus(it.userId, it.localTransactionId, SyncStatus.PENDING) }
        sync.requestSync()
    }

    /** Drops a queued create and its optimistic row. */
    suspend fun discard(mutationId: String) {
        val row = mutations.get(mutationId) ?: return
        db.withTransaction {
            transactions.delete(row.userId, row.localTransactionId)
            mutations.delete(mutationId)
        }
        invalidation.invalidate(Aggregate.TRANSACTION)
    }

    /** After a fresh sign-in, creates that failed for auth reasons can go again. */
    suspend fun requeueAuthFailures(userId: String) {
        mutations.requeueFailures(userId, FailureCode.AUTHENTICATION)
        sync.requestSync()
    }

    /** Sends one mutation if this caller wins the claim; null when someone else is sending it. */
    private suspend fun send(mutationId: String): SendOutcome? {
        if (mutations.claim(mutationId, System.currentTimeMillis()) == 0) return null
        val row = mutations.get(mutationId) ?: return null
        val item = SpenditosJson.decodeFromString(BulkTransactionItem.serializer(), row.payloadJson)
        val outcome = try {
            FailureClassifier.fromBulkResult(apiCall { api.createBulk(BulkCreateRequest(listOf(item))) })
        } catch (e: CancellationException) {
            mutations.release(mutationId, MutationStatus.PENDING, null, null)
            throw e
        } catch (e: Exception) {
            FailureClassifier.fromError(e)
        }
        when (outcome) {
            is SendOutcome.Created -> db.withTransaction {
                transactions.delete(row.userId, row.localTransactionId)
                transactions.upsert(listOf(outcome.transaction.toEntity()))
                mutations.delete(mutationId)
            }
            SendOutcome.AlreadySaved -> db.withTransaction {
                // The server row exists but the reply carries no id; the next refresh brings it.
                transactions.delete(row.userId, row.localTransactionId)
                mutations.delete(mutationId)
            }
            is SendOutcome.Failed -> {
                val status = if (outcome.code.isRetryable) MutationStatus.PENDING else MutationStatus.FAILED
                mutations.release(mutationId, status, outcome.code, outcome.message)
                transactions.setStatus(
                    row.userId, row.localTransactionId,
                    if (outcome.code.isRetryable) SyncStatus.PENDING else SyncStatus.FAILED,
                )
            }
        }
        invalidation.invalidate(Aggregate.TRANSACTION)
        return outcome
    }

    private fun Transaction.toEntity() = TransactionEntity(
        id = id, userId = userId, amount = amount, categoryId = categoryId, description = description,
        date = date, mood = mood?.name, templateId = templateId, source = source, createdAt = createdAt,
        updatedAt = updatedAt, syncStatus = SyncStatus.SYNCED,
    )

    private companion object {
        const val MAX_PAGES = 25
        const val STALE_CLAIM_MS = 2 * 60 * 1000L
    }
}
