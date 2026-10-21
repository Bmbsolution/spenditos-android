package com.spenditos.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `TransactionMood` spenditos/packages/types/src/domain/gamification/mood.ts. Unknown values decode to null. */
@Serializable
enum class TransactionMood {
    @SerialName("worth_it") WORTH_IT,
    @SerialName("necessary") NECESSARY,
    @SerialName("regret") REGRET,
    @SerialName("impulsive") IMPULSIVE,
}

/**
 * `Transaction` packages/types/src/domain/transaction.ts. Income vs expense comes
 * from the category's `isIncome`, never from the amount (always positive).
 * `source`/`imageKey` are server extras iOS also decodes.
 */
@Serializable
data class Transaction(
    val id: String,
    val userId: String,
    val categoryId: String,
    val templateId: String? = null,
    val description: String = "",
    val amount: Double,
    /** YYYY-MM-DD, a calendar date in the user's time zone. Never convert through UTC. */
    val date: String,
    val mood: TransactionMood? = null,
    val source: String? = null,
    val imageKey: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** The debt this payment counts toward (docs/features/debts.md). Absent on older servers. */
    val debtId: String? = null,
)

/** `PaginatedTransactionsData` packages/types/src/api/index.ts:375. */
@Serializable
data class TransactionsPage(
    val transactions: List<Transaction> = emptyList(),
    val pagination: TransactionsPagination = TransactionsPagination(),
)

/** `TransactionsPagination` api/index.ts:360. */
@Serializable
data class TransactionsPagination(val nextCursor: String? = null, val hasMore: Boolean = false)

/**
 * Object form of `CreateBulkTransactionsRequest` api/index.ts:656. Android sends every
 * plain create through this endpoint with one item and an idempotency key
 * (docs/DECISIONS.md D2): the server derives a deterministic id from it and reports a
 * resend as a duplicate instead of writing twice.
 */
@Serializable
data class BulkCreateRequest(
    val transactions: List<BulkTransactionItem>,
    val source: String = "manual",
)

@Serializable
data class BulkTransactionItem(
    val amount: Double,
    val date: String,
    val categoryId: String,
    val description: String,
    val mood: TransactionMood? = null,
    val templateId: String? = null,
    val idempotencyKeyId: String,
    val debtId: String? = null,
)

/** `CreateBulkTransactionsResponse` api/index.ts:705. Duplicates carry no ids. */
@Serializable
data class BulkCreateResult(
    val results: List<BulkTransactionResult> = emptyList(),
    val failed: List<BulkTransactionFailure> = emptyList(),
    val duplicates: Int = 0,
)

@Serializable
data class BulkTransactionResult(val index: Int? = null, val transaction: Transaction? = null, val error: String? = null)

@Serializable
data class BulkTransactionFailure(val index: Int, val reason: String)

/**
 * `UpdateTransactionRequest` packages/types/src/api/index.ts:422. Android sends the full edited
 * form like iOS: `mood` and `templateId` go out as explicit nulls when cleared, so they are
 * JSON elements; null Kotlin fields are left out (`explicitNulls = false`).
 */
@Serializable
data class UpdateTransactionRequest(
    val amount: Double? = null,
    val date: String? = null,
    val categoryId: String? = null,
    val description: String? = null,
    val mood: kotlinx.serialization.json.JsonElement? = null,
    val templateId: kotlinx.serialization.json.JsonElement? = null,
    /** `PUT /transactions/{id}` accepts `debtId`; JsonNull unlinks, null (omitted) leaves it alone. */
    val debtId: kotlinx.serialization.json.JsonElement? = null,
)
