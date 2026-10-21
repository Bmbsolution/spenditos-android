package com.spenditos.android.data.transactions

import androidx.room.withTransaction
import com.spenditos.android.core.auth.SessionChangedException
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
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FailureClassifier
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SendOutcome
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkTransactionItem
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.model.UpdateTransactionRequest
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    /** Links the payment to an existing bill ("Use existing bill"). */
    val templateId: String? = null,
    val debtId: String? = null,
)

sealed interface CreateResult {
    /** [transactionId] is the server id, or null when the server reported a duplicate without one. */
    data class Saved(val transactionId: String? = null) : CreateResult

    /** Stored on the device and queued; the worker will send it. */
    data object SavedOffline : CreateResult

    /** The server refused it for a reason a retry won't fix; nothing was kept. */
    data class Rejected(val code: FailureCode, val message: String) : CreateResult
}

/** A device-only transaction is being sent right now; try again once it lands. */
class TransactionBusyException : IllegalStateException("This transaction is saving right now. Try again in a moment.")

data class BulkDeleteResult(val deleted: Int, val failed: Int)

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
        val localId = "$LOCAL_PREFIX$mutationId"
        val item = BulkTransactionItem(
            amount = draft.amount,
            date = draft.date.toString(),
            categoryId = draft.categoryId,
            description = draft.description.trim(),
            mood = draft.mood,
            templateId = draft.templateId,
            idempotencyKeyId = mutationId,
            debtId = draft.debtId,
        )
        val now = System.currentTimeMillis()
        db.withTransaction {
            transactions.upsert(
                listOf(
                    TransactionEntity(
                        id = localId, userId = userId, amount = item.amount, categoryId = item.categoryId,
                        description = item.description, date = item.date, mood = draft.mood?.name, templateId = draft.templateId,
                        source = "manual", createdAt = null, updatedAt = null, syncStatus = SyncStatus.PENDING,
                        clientMutationId = mutationId,
                        debtId = draft.debtId,
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
            is SendOutcome.Created -> CreateResult.Saved(outcome.transaction.id)
            SendOutcome.AlreadySaved -> CreateResult.Saved(null)
            SendOutcome.Deferred -> {
                // The session changed mid-send; the queue keeps it for the owning account.
                sync.requestSync()
                CreateResult.SavedOffline
            }
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

    /**
     * `PUT /transactions/{id}` with the whole edited form, online only like iOS (an edit is never
     * queued). Rows that only exist on this device are edited by discarding and re-adding, which
     * the form does not offer, so they are refused here.
     */
    suspend fun update(id: String, draft: NewTransaction): Transaction {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        require(!id.startsWith(LOCAL_PREFIX)) { "Not synced yet" }
        val updated = apiCall {
            api.update(
                id,
                UpdateTransactionRequest(
                    amount = draft.amount,
                    date = draft.date.toString(),
                    categoryId = draft.categoryId,
                    description = draft.description.trim(),
                    mood = moodJson(draft.mood),
                ),
            )
        }
        if (updated.userId == userId) transactions.upsert(listOf(updated.toEntity()))
        invalidation.invalidate(Aggregate.TRANSACTION)
        return updated
    }

    /**
     * Pays a generated bill entry: its estimate is replaced by the real amount and date, so the
     * bill isn't counted twice (iOS `billEntryToReplace`).
     */
    suspend fun payBillEntry(entry: Transaction, draft: NewTransaction): Transaction {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val updated = apiCall {
            api.update(
                entry.id,
                UpdateTransactionRequest(
                    amount = draft.amount,
                    date = draft.date.toString(),
                    categoryId = draft.categoryId,
                    description = draft.description.trim(),
                    mood = moodJson(draft.mood),
                    templateId = entry.templateId?.let(::JsonPrimitive) ?: JsonNull,
                ),
            )
        }
        if (updated.userId == userId) transactions.upsert(listOf(updated.toEntity()))
        invalidation.invalidate(Aggregate.TRANSACTION)
        return updated
    }

    /**
     * Deletes a transaction. A row that only exists on this device drops its queued create
     * instead; a synced row needs the server (`DELETE /transactions/{id}`), then leaves the cache.
     */
    suspend fun delete(id: String) {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        if (id.startsWith(LOCAL_PREFIX)) {
            val mutationId = id.removePrefix(LOCAL_PREFIX)
            val removed = db.withTransaction {
                // 0 also when the send already landed: the row then has a server id to delete by.
                val deleted = mutations.deleteUnlessInFlight(mutationId) == 1
                if (deleted) transactions.delete(userId, id)
                deleted
            }
            // Mid-send (or just sent): deleting here would leave the server copy behind.
            if (!removed) throw TransactionBusyException()
            invalidation.invalidate(Aggregate.TRANSACTION)
            return
        }
        try {
            apiCallUnit { api.delete(id) }
        } catch (e: ApiError.Server) {
            // Already gone on the server: removing it here is the outcome the user asked for.
            if (e.status != 404) throw e
        }
        transactions.delete(userId, id)
        invalidation.invalidate(Aggregate.TRANSACTION)
    }

    /**
     * iOS list Edit > Delete (n). Device-only rows drop their queued create (unless a send holds
     * them); synced rows go to the server in chunks of 100. Rows the server could not delete stay.
     */
    suspend fun bulkDelete(ids: Collection<String>): BulkDeleteResult {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        var failed = 0
        val (local, synced) = ids.distinct().partition { it.startsWith(LOCAL_PREFIX) }
        for (id in local) {
            val removed = db.withTransaction {
                val deleted = mutations.deleteUnlessInFlight(id.removePrefix(LOCAL_PREFIX)) == 1
                if (deleted) transactions.delete(userId, id)
                deleted
            }
            if (!removed) failed++
        }
        var firstError: Exception? = null
        for (chunk in synced.chunked(BULK_DELETE_MAX)) {
            val notDeleted = try {
                bulkDeleteChunk(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                firstError = firstError ?: e
                chunk.toSet()
            }
            failed += notDeleted.size
            db.withTransaction { chunk.filterNot { it in notDeleted }.forEach { transactions.delete(userId, it) } }
        }
        invalidation.invalidate(Aggregate.TRANSACTION)
        // Nothing went through at all: surface why (offline, signed out) instead of a count.
        if (firstError != null && failed == ids.distinct().size) throw firstError!!
        return BulkDeleteResult(deleted = ids.distinct().size - failed, failed = failed)
    }

    /** The ids of [chunk] the server did not delete. */
    private suspend fun bulkDeleteChunk(chunk: List<String>): Set<String> {
        var response: retrofit2.Response<okhttp3.ResponseBody>? = null
        apiCallUnit {
            val r = api.bulkDelete(BulkDeleteRequest(chunk))
            if (!r.isSuccessful) throw retrofit2.HttpException(r)
            response = r
        }
        val r = response ?: return chunk.toSet()
        if (r.code() != 207) return emptySet()
        val body = r.body()?.string().orEmpty()
        return runCatching {
            SpenditosJson.parseToJsonElement(body).jsonObject["details"]?.jsonObject
                ?.get("failedTransactionIds")?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        }.getOrNull() ?: chunk.toSet()
    }

    /** One server page of [start]..[end] for the bill duplicate check; not cached. */
    suspend fun fetchRange(start: LocalDate, end: LocalDate): List<Transaction> =
        apiCall { api.list(start.toString(), end.toString()) }.transactions

    /** Sends queued creates oldest first until the queue is empty or the network/server fails. */
    suspend fun drainQueue(userId: String): DrainResult {
        mutations.releaseStaleClaims(before = System.currentTimeMillis() - STALE_CLAIM_MS)
        while (true) {
            val next = mutations.nextPending(userId) ?: break
            val outcome = send(next.id) ?: continue
            when (outcome) {
                SendOutcome.Deferred -> return DrainResult.RETRY_LATER
                is SendOutcome.Failed if outcome.code.isRetryable -> return DrainResult.RETRY_LATER
                else -> {}
            }
        }
        // No PENDING rows left, but an IN_FLIGHT claim is unresolved: either a concurrent
        // sender in this process or a claim left by a dead process that is still younger
        // than the stale threshold. Another worker pass (WorkManager backoff) releases and
        // replays it once it settles or goes stale.
        return if (mutations.hasInFlight(userId)) DrainResult.RETRY_LATER else DrainResult.DONE
    }

    /** Manual retry from the "Not synced" screen. */
    suspend fun retry(mutationId: String) {
        mutations.release(mutationId, MutationStatus.PENDING, null, null)
        mutations.get(mutationId)?.let { transactions.setStatus(it.userId, it.localTransactionId, SyncStatus.PENDING) }
        sync.requestSync()
    }

    /**
     * Drops a queued create and its optimistic row. Returns false (and keeps both) while a
     * send holds it: the outstanding request can still commit, so the entry must survive
     * until the send settles. Unsent and failed entries discard normally.
     */
    suspend fun discard(mutationId: String): Boolean {
        val row = mutations.get(mutationId) ?: return true
        val removed = db.withTransaction {
            val deleted = mutations.deleteUnlessInFlight(mutationId) == 1
            if (deleted) transactions.delete(row.userId, row.localTransactionId)
            deleted
        }
        if (removed) invalidation.invalidate(Aggregate.TRANSACTION)
        return removed
    }

    /** Account deletion: drops every queued create of the signed-in user and its optimistic row. */
    suspend fun dropQueue() {
        val userId = session.currentUserId ?: return
        val queued = mutations.observeAll(userId).first()
        db.withTransaction {
            queued.forEach { row ->
                transactions.delete(row.userId, row.localTransactionId)
                mutations.delete(row.id)
            }
        }
        invalidation.invalidate(Aggregate.TRANSACTION)
    }

    /** After a fresh sign-in, creates that failed for auth reasons can go again. */
    suspend fun requeueAuthFailures(userId: String) {
        mutations.requeueFailures(userId, FailureCode.AUTHENTICATION)
        sync.requestSync()
    }

    /**
     * Sends one mutation if this caller wins the claim; null when someone else is sending it.
     * The request is bound to the owning account's session (tag on the request), so neither it
     * nor its 401 retry can adopt another session's credentials; a session switch defers the
     * entry back to PENDING instead of failing it.
     */
    private suspend fun send(mutationId: String): SendOutcome? {
        val binding = session.currentBinding ?: return SendOutcome.Deferred
        if (mutations.claim(mutationId, System.currentTimeMillis()) == 0) return null
        val row = mutations.get(mutationId) ?: return null
        if (row.userId != binding.userId) {
            // Never another account's payload with this session's credentials.
            mutations.release(mutationId, MutationStatus.PENDING, null, null)
            return SendOutcome.Deferred
        }
        val item = try {
            SpenditosJson.decodeFromString(BulkTransactionItem.serializer(), row.payloadJson)
        } catch (e: Exception) {
            // A payload that no longer decodes can never be sent or retried: settle the claim
            // as a local validation failure instead of throwing out of the worker.
            val failure = SendOutcome.Failed(
                FailureCode.VALIDATION,
                "This saved transaction could not be read. Review it and add it again.",
            )
            mutations.release(mutationId, MutationStatus.FAILED, failure.code, failure.message)
            transactions.setStatus(row.userId, row.localTransactionId, SyncStatus.FAILED)
            invalidation.invalidate(Aggregate.TRANSACTION)
            return failure
        }
        val outcome = try {
            FailureClassifier.fromBulkResult(apiCall { api.createBulk(BulkCreateRequest(listOf(item)), binding) })
        } catch (e: CancellationException) {
            // The caller went away mid-send (e.g. the sheet dismissed). The release and the
            // retry request must survive cancellation, or the claim sticks as IN_FLIGHT.
            withContext(NonCancellable) {
                mutations.release(mutationId, MutationStatus.PENDING, null, null)
                sync.requestSync()
            }
            throw e
        } catch (e: ApiError.Network) {
            if (e.cause is SessionChangedException) {
                // Signed out or another account signed in mid-send: keep it queued, not failed.
                mutations.release(mutationId, MutationStatus.PENDING, null, null)
                SendOutcome.Deferred
            } else {
                FailureClassifier.fromError(e)
            }
        } catch (e: Exception) {
            FailureClassifier.fromError(e)
        }
        when (outcome) {
            SendOutcome.Deferred -> return outcome
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

    private fun moodJson(mood: TransactionMood?) =
        SpenditosJson.encodeToJsonElement(TransactionMood.serializer().nullable, mood)

    private fun Transaction.toEntity() = TransactionEntity(
        id = id, userId = userId, amount = amount, categoryId = categoryId, description = description,
        date = date, mood = mood?.name, templateId = templateId, source = source, createdAt = createdAt,
        updatedAt = updatedAt, syncStatus = SyncStatus.SYNCED, debtId = debtId,
    )

    private companion object {
        const val MAX_PAGES = 25
        const val LOCAL_PREFIX = "local-"
        const val BULK_DELETE_MAX = 100
        const val STALE_CLAIM_MS = 2 * 60 * 1000L
    }
}
