package com.spenditos.android.data.recurring

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.PatchMainIncomeTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

interface RecurringActions {
    fun observe(): Flow<List<RecurringTemplate>>
    fun observeMainIncomeId(): Flow<String?>
    suspend fun refresh()
    suspend fun create(request: CreateRecurringTemplateRequest): RecurringTemplate
    suspend fun update(id: String, request: UpdateRecurringTemplateRequest): RecurringTemplate
    suspend fun delete(id: String)
    suspend fun setMainIncome(id: String?)
    suspend fun generate(month: YearMonth): GenerateTransactionsResult
}

/** In-memory cache for the online recurring screen; it remains available while the app is open offline. */
@Singleton
class RecurringRepository @Inject constructor(
    private val api: RecurringApi,
    private val session: SessionManager,
    private val invalidations: FinanceInvalidationBus,
) : RecurringActions {
    private val templates = MutableStateFlow<List<RecurringTemplate>>(emptyList())
    private val mainIncomeId = MutableStateFlow<String?>(null)
    private val savedByUser = mutableMapOf<String, SavedRecurringState>()
    @Volatile private var activeUserId: String? = null

    override fun observe(): Flow<List<RecurringTemplate>> = combine(session.state, templates) { sessionState, rows ->
        if ((sessionState as? SessionState.SignedIn)?.userId == activeUserId) rows else emptyList()
    }

    override fun observeMainIncomeId(): Flow<String?> = combine(session.state, mainIncomeId) { sessionState, id ->
        if ((sessionState as? SessionState.SignedIn)?.userId == activeUserId) id else null
    }

    override suspend fun refresh() {
        val userId = activateUser()
        val rows = apiCall { api.list() }
        ensureCurrentUser(userId)
        templates.value = rows
        saveFor(userId)
        val settings = apiCall { api.settings() }
        ensureCurrentUser(userId)
        mainIncomeId.value = settings.mainIncomeTemplateId
        saveFor(userId)
    }

    override suspend fun create(request: CreateRecurringTemplateRequest): RecurringTemplate {
        val userId = activateUser()
        var created = apiCall { api.create(request) }
        ensureCurrentUser(userId)
        // The POST has committed. The follow-up PUTs only restore fields the create handler drops,
        // so they are best effort: failing the call here would invite a second Save, which creates
        // a duplicate template (no idempotency on POST /recurring) and doubles its entries.
        try {
            if (request.amountVaries && !created.amountVaries) {
                // The current create handler drops amountVaries (recurring.ts:295-307); keep the
                // requested value by sending the follow-up PUT until the server preserves it.
                created = apiCall { api.update(created.id, UpdateRecurringTemplateRequest(amountVaries = true)) }
                ensureCurrentUser(userId)
            }
            if (!request.isActive) {
                // isActive=false is already saved by the POST; the create schema has no pausedAt
                // field, so mark the inactive create as a pause by PUT.
                created = apiCall {
                    api.update(
                        created.id,
                        UpdateRecurringTemplateRequest(isActive = false, pausedAt = Instant.now().toString()),
                    )
                }
                ensureCurrentUser(userId)
            }
        } catch (_: ApiError) {
            // Keep the committed template; the refresh below shows what the server saved.
        } finally {
            afterWriteRefresh()
        }
        return created
    }

    override suspend fun update(id: String, request: UpdateRecurringTemplateRequest): RecurringTemplate {
        val userId = activateUser()
        val updated = apiCall { api.update(id, request) }
        ensureCurrentUser(userId)
        afterWriteRefresh()
        return updated
    }

    override suspend fun delete(id: String) {
        val userId = activateUser()
        apiCallUnit { api.delete(id) }
        ensureCurrentUser(userId)
        afterWriteRefresh()
    }

    override suspend fun setMainIncome(id: String?) {
        val userId = activateUser()
        val body = PatchMainIncomeTemplateRequest(id?.let { JsonPrimitive(it) } ?: JsonNull)
        val settings = apiCall { api.setMainIncome(body) }
        ensureCurrentUser(userId)
        mainIncomeId.value = settings.mainIncomeTemplateId
        saveFor(userId)
        afterWriteRefresh()
    }

    override suspend fun generate(month: YearMonth): GenerateTransactionsResult {
        val userId = activateUser()
        // The handler creates each template/date pair idempotently, so retrying this same month is safe.
        // Mid-month like iOS (YearMonth.date, day 15) and web ("<month>-15"): the handler parses the
        // date as UTC midnight and reads the month in the Lambda's local zone, so day 1 would
        // generate the previous month in any runtime whose zone is behind UTC.
        val result = apiCall { api.generate(GenerateTransactionsRequest(generateTargetDate(month))) }
        ensureCurrentUser(userId)
        afterWriteRefresh()
        return result
    }

    private suspend fun afterWriteRefresh() {
        try {
            refresh()
        } catch (_: ApiError) {
            // The write response is authoritative when the follow-up reads are offline.
        } finally {
            invalidations.invalidate(Aggregate.RECURRING, Aggregate.TRANSACTION)
        }
    }

    @Synchronized
    private fun activateUser(): String {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        if (activeUserId != userId) {
            activeUserId?.let(::saveFor)
            activeUserId = userId
            val cached = savedByUser[userId]
            templates.value = cached?.templates.orEmpty()
            mainIncomeId.value = cached?.mainIncomeTemplateId
        }
        return userId
    }

    @Synchronized
    private fun saveFor(userId: String) {
        if (activeUserId == userId) savedByUser[userId] = SavedRecurringState(templates.value, mainIncomeId.value)
    }

    private fun ensureCurrentUser(userId: String) {
        if (session.currentUserId != userId) throw ApiError.Unauthorized
    }

    private data class SavedRecurringState(
        val templates: List<RecurringTemplate>,
        val mainIncomeTemplateId: String?,
    )
}

/** `POST /transactions/generate` target for [month]: its 15th, never near a month boundary. */
internal fun generateTargetDate(month: YearMonth): String = month.atDay(15).toString()
