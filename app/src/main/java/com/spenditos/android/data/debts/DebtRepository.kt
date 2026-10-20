package com.spenditos.android.data.debts

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One `Idempotency-Key` per "Add debt" attempt: generate it once, reuse it for every retry. */
object DebtIdempotencyKey {
    const val MAX_LENGTH = 128

    fun generate(): String = UUID.randomUUID().toString()
}

interface DebtActions {
    /** `GET /debts`; not cached (a balance moves with every payment). */
    suspend fun list(asOf: LocalDate = LocalDate.now()): List<DebtWithPlan>

    /** `GET /debts/{id}`. */
    suspend fun detail(id: String, asOf: LocalDate = LocalDate.now()): DebtDetail

    /** `POST /debts`. Reuse [idempotencyKey] when retrying the same attempt. Online only. */
    suspend fun create(request: CreateDebtRequest, idempotencyKey: String): DebtDetail

    /** `PATCH /debts/{id}`. Online only. */
    suspend fun update(id: String, request: UpdateDebtRequest): DebtDetail

    /** `DELETE /debts/{id}`. Online only. */
    suspend fun delete(id: String)

    /** `POST /debts/{id}/adjustments` ("Correct balance"). Online only. */
    suspend fun addAdjustment(id: String, date: LocalDate, balance: Double): DebtDetail

    /** `POST /debts/preview` (stateless, nothing saved). */
    suspend fun preview(request: DebtPreviewRequest): DebtPreviewResponse
}

/**
 * Debts service layer (iOS `DebtService`). Reads are not cached. Writes are online-only: they
 * never touch the offline queue, and fail with [ApiError.Network] (the app's "needs a
 * connection" error) before any request goes out. After a write the server adds, resizes or
 * removes the debt's upcoming bill entries (also after extra payments and corrections), so
 * transactions and recurring bills are invalidated, the same two aggregates as iOS.
 */
@Singleton
class DebtRepository @Inject constructor(
    private val api: DebtApi,
    private val session: SessionManager,
    private val connectivity: ConnectivityStatus,
    private val invalidations: FinanceInvalidationBus,
) : DebtActions {
    override suspend fun list(asOf: LocalDate): List<DebtWithPlan> {
        val userId = requireUser()
        val response = apiCall { api.list(asOf.toString()) }
        ensureCurrentUser(userId)
        return response.debts
    }

    override suspend fun detail(id: String, asOf: LocalDate): DebtDetail {
        val userId = requireUser()
        val detail = apiCall { api.get(id, asOf.toString()) }
        ensureCurrentUser(userId)
        return detail
    }

    override suspend fun create(request: CreateDebtRequest, idempotencyKey: String): DebtDetail {
        require(idempotencyKey.length in 1..DebtIdempotencyKey.MAX_LENGTH) { "Idempotency-Key must be 1..128 characters" }
        val binding = requireOnlineWrite()
        return apiCall { api.create(idempotencyKey, request, binding) }.also { changed() }
    }

    override suspend fun update(id: String, request: UpdateDebtRequest): DebtDetail {
        val binding = requireOnlineWrite()
        return apiCall { api.update(id, request, binding) }.also { changed() }
    }

    override suspend fun delete(id: String) {
        val binding = requireOnlineWrite()
        apiCallUnit { api.delete(id, binding) }
        changed()
    }

    override suspend fun addAdjustment(id: String, date: LocalDate, balance: Double): DebtDetail {
        val binding = requireOnlineWrite()
        return apiCall { api.createAdjustment(id, CreateDebtAdjustmentRequest(date.toString(), balance), binding) }
            .also { changed() }
    }

    override suspend fun preview(request: DebtPreviewRequest): DebtPreviewResponse {
        val userId = requireUser()
        val response = apiCall { api.preview(request) }
        ensureCurrentUser(userId)
        return response
    }

    private fun requireUser(): String = session.currentUserId ?: throw ApiError.Unauthorized

    private fun ensureCurrentUser(userId: String) {
        if (session.currentUserId != userId) throw ApiError.Unauthorized
    }

    /** Signed in and online, else nothing is sent. Returns the session the write is bound to. */
    private fun requireOnlineWrite() = (session.currentBinding ?: throw ApiError.Unauthorized).also {
        if (!connectivity.isOnline.value) throw ApiError.Network(IOException("Offline"))
    }

    private fun changed() = invalidations.invalidate(Aggregate.TRANSACTION, Aggregate.RECURRING)
}
