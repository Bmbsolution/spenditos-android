package com.spenditos.android.feature.debts

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.debts.CreateDebtRequest
import com.spenditos.android.data.debts.Debt
import com.spenditos.android.data.debts.DebtActions
import com.spenditos.android.data.debts.DebtDetail
import com.spenditos.android.data.debts.DebtPreviewRequest
import com.spenditos.android.data.debts.DebtPreviewResponse
import com.spenditos.android.data.debts.DebtWithPlan
import com.spenditos.android.data.debts.ListDebtsResponse
import com.spenditos.android.data.debts.UpdateDebtRequest
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Decoded fixtures for view-model tests (iOS `DebtTestData`; the same handwritten JSON the model tests use). */
object DebtTestData {
    private fun fixture(name: String): String =
        requireNotNull(DebtTestData::class.java.classLoader!!.getResource("contract/handwritten/debts/$name.json")).readText()

    private fun <T> data(serializer: kotlinx.serialization.KSerializer<T>, json: String): T =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(serializer), json).data!!

    val list: List<DebtWithPlan> get() = data(ListDebtsResponse.serializer(), fixture("list")).debts
    val detail: DebtDetail get() = data(DebtDetail.serializer(), fixture("detail"))
    val car: DebtWithPlan get() = list.first { it.id == "debt-car" }
}

val TEST_TODAY: LocalDate = LocalDate.of(2026, 10, 20)

fun testClock(): Clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

class FakeConnectivity(online: Boolean = true) : ConnectivityStatus {
    override val isOnline = MutableStateFlow(online)
}

class FakeCurrency(private val code: String = "USD") : UserSettingsActions {
    override fun currency(): Flow<String> = flowOf(code)
    override suspend fun refresh() = UserSettings("user-1", currency = code)
    override suspend fun updateSettings(update: UserSettingsUpdate) = UserSettings("user-1", currency = code)
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
}

/** In-memory [DebtActions] (iOS `MockDebtService`): stub the results, then read back what was asked. */
class FakeDebtActions : DebtActions {
    var listResult: Result<List<DebtWithPlan>> = Result.success(emptyList())
    var detailResult: Result<DebtDetail>? = null
    var createResult: Result<DebtDetail>? = null
    var updateResult: Result<DebtDetail>? = null
    var deleteResult: Result<Unit> = Result.success(Unit)
    var adjustmentResult: Result<DebtDetail>? = null
    var previewResult: Result<DebtPreviewResponse> = Result.success(defaultPreview)
    var extraPaymentResult: Result<Unit> = Result.success(Unit)

    /** Runs inside [recordExtraPayment] before it answers, to hold a request "in flight". */
    var extraPaymentGate: (suspend () -> Unit)? = null

    /** Runs inside [preview] before it answers, to hold a request "in flight". */
    var previewGate: (suspend () -> Unit)? = null

    val listAsOf = mutableListOf<LocalDate>()
    val detailCalls = mutableListOf<Pair<String, LocalDate>>()
    val updateCalls = mutableListOf<Pair<String, UpdateDebtRequest>>()
    val deleteCalls = mutableListOf<String>()
    val adjustmentCalls = mutableListOf<Triple<String, LocalDate, Double>>()
    val previewRequests = mutableListOf<DebtPreviewRequest>()
    val extraPaymentKeys = mutableListOf<String?>()
    val extraPaymentCalls = mutableListOf<Triple<Debt, Double, LocalDate>>()

    override suspend fun list(asOf: LocalDate): List<DebtWithPlan> {
        listAsOf += asOf
        return listResult.getOrThrow()
    }

    override suspend fun detail(id: String, asOf: LocalDate): DebtDetail {
        detailCalls += id to asOf
        return (detailResult ?: Result.failure(ApiError.NoData)).getOrThrow()
    }

    override suspend fun create(request: CreateDebtRequest, idempotencyKey: String): DebtDetail =
        (createResult ?: Result.failure(ApiError.NoData)).getOrThrow()

    override suspend fun update(id: String, request: UpdateDebtRequest): DebtDetail {
        updateCalls += id to request
        return (updateResult ?: Result.failure(ApiError.NoData)).getOrThrow()
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        deleteResult.getOrThrow()
    }

    override suspend fun addAdjustment(id: String, date: LocalDate, balance: Double): DebtDetail {
        adjustmentCalls += Triple(id, date, balance)
        return (adjustmentResult ?: Result.failure(ApiError.NoData)).getOrThrow()
    }

    override suspend fun preview(request: DebtPreviewRequest): DebtPreviewResponse {
        previewRequests += request
        previewGate?.invoke()
        return previewResult.getOrThrow()
    }

    override suspend fun recordExtraPayment(debt: Debt, amount: Double, date: LocalDate, idempotencyKey: String?): Transaction {
        extraPaymentKeys += idempotencyKey
        extraPaymentCalls += Triple(debt, amount, date)
        extraPaymentGate?.invoke()
        extraPaymentResult.getOrThrow()
        return Transaction(
            id = "txn-extra", userId = debt.userId, categoryId = debt.categoryId, description = debt.name,
            amount = amount, date = date.toString(), debtId = debt.id,
        )
    }

    companion object {
        val defaultPreview = DebtPreviewResponse(
            payment = 320.0, paymentsCount = 20, payoffDate = "2028-05-01",
            lastPaymentAmount = 180.0, totalInterest = 612.0, neverPaysOff = false,
        )
    }
}
