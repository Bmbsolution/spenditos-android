package com.spenditos.android.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Sync state of a cached transaction row (iOS `SyncStatus`). */
enum class SyncStatus { SYNCED, PENDING, FAILED }

/**
 * Cached transaction. Server rows use the server id; a queued create uses
 * `local-<clientMutationId>` until the server confirms it. Every query filters by [userId].
 */
@Entity(tableName = "transactions", indices = [Index("userId", "date")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amount: Double,
    val categoryId: String,
    val description: String,
    val date: String,
    val mood: String?,
    val templateId: String?,
    val source: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val syncStatus: SyncStatus,
    /** Set on optimistic rows; links to [PendingMutationEntity.id]. */
    val clientMutationId: String? = null,
)

@Entity(tableName = "categories", indices = [Index("userId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val color: String,
    val icon: String?,
    val isIncome: Boolean,
    val sortOrder: Int,
    val isSystemSuggestion: Boolean,
)

enum class MutationKind { CREATE_TRANSACTION }

/**
 * IN_FLIGHT is a claim: only one sender (inline save or SyncWorker) may send a
 * mutation at a time. Stale claims are released by the worker.
 */
enum class MutationStatus { PENDING, IN_FLIGHT, FAILED }

/** Same classes as iOS `OfflineQueueManager.FailureCode`. */
enum class FailureCode(val isRetryable: Boolean) {
    TRANSPORT(true),
    SERVER(true),
    AUTHENTICATION(false),
    VALIDATION(false),
    CATEGORY_CONFLICT(false),
    PERMANENT(false),
    UNKNOWN(false),
}

/**
 * A queued write. [id] is the client mutation id, which is also the server
 * idempotency key (`idempotencyKeyId`). [payloadJson] is frozen at enqueue time:
 * the server's deterministic id hashes the payload, so it must never change.
 */
@Entity(tableName = "pending_mutations", indices = [Index("userId", "status")])
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val kind: MutationKind,
    val payloadJson: String,
    val localTransactionId: String,
    val status: MutationStatus,
    val failureCode: FailureCode? = null,
    val failureMessage: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
)
