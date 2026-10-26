package com.spenditos.android.data.recurring

import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.auth.SessionState
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.PatchMainIncomeTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    /** Logs [month]'s entries for the given bills (all active items when [templateIds] is null). */
    suspend fun generate(month: YearMonth, templateIds: List<String>? = null): GenerateTransactionsResult

    // Lifecycle (#403): request bodies in [BillLifecycleRequests].
    /** Pause: coming back later. Upcoming unlogged entries are removed. */
    suspend fun pause(id: String, now: Instant = Instant.now()): RecurringTemplate
    /** Resume a paused bill, or undo an end. The server logs the missing entries from today. */
    suspend fun resume(id: String): RecurringTemplate
    /** End for good. [keepUpcoming] leaves this month's entries in place (the last payment is still coming). */
    suspend fun end(id: String, keepUpcoming: Boolean = false, now: Instant = Instant.now()): RecurringTemplate
    /** End after one more payment: the generator ends it once that entry is made. */
    suspend fun endAfterNextPayment(id: String): RecurringTemplate
    /** Start an ended bill again from [nextDate], with no end. */
    suspend fun startAgain(id: String, nextDate: LocalDate): RecurringTemplate
    /** What each bill has logged so far (`GET /recurring/history`, at most 50 ids). */
    suspend fun history(ids: List<String>, includeEntries: Boolean = false): Map<String, BillHistorySummary>
    /** Logged entries dated in [month] (paged), for the summary strip and the Generate sheet. */
    suspend fun monthEntries(month: YearMonth): List<Transaction>
}

/**
 * Request bodies for the Paused / Ended lifecycle, exactly as iOS `RecurringTemplateService` sends
 * them (#403). Explicit JSON nulls are real `JsonNull` values: `SpenditosJson` omits only Kotlin nulls.
 */
object BillLifecycleRequests {
    /** `ISO8601DateFormatter` style: whole seconds, `Z`. */
    fun timestamp(now: Instant): String = now.truncatedTo(ChronoUnit.SECONDS).toString()

    fun pause(now: Instant) = UpdateRecurringTemplateRequest(
        isActive = false,
        pausedAt = JsonPrimitive(timestamp(now)),
        upcomingEntries = "sync",
    )

    fun resume() = UpdateRecurringTemplateRequest(isActive = true, upcomingEntries = "sync")

    fun end(now: Instant, keepUpcoming: Boolean) = UpdateRecurringTemplateRequest(
        isActive = false,
        endedAt = JsonPrimitive(timestamp(now)),
        pausedAt = JsonNull,
        upcomingEntries = if (keepUpcoming) null else "sync",
    )

    fun endAfterNextPayment() = UpdateRecurringTemplateRequest(occurrencesRemaining = JsonPrimitive(1))

    /** Undo of [endAfterNextPayment]: back to the previous cap, or none. */
    fun restoreOccurrences(previous: Int?) =
        UpdateRecurringTemplateRequest(occurrencesRemaining = previous?.let { JsonPrimitive(it) } ?: JsonNull)

    fun startAgain(nextDate: LocalDate) = UpdateRecurringTemplateRequest(
        isActive = true,
        firstOccurrenceDate = nextDate.toString(),
        occurrencesRemaining = JsonNull,
        upcomingEntries = "sync",
    )
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
                        UpdateRecurringTemplateRequest(isActive = false, pausedAt = JsonPrimitive(BillLifecycleRequests.timestamp(Instant.now()))),
                    )
                }
                ensureCurrentUser(userId)
            }
        } catch (_: ApiError) {
            // Keep the committed template; the refresh below shows what the server saved.
        }
        // iOS and web log this month from today onward right after a NEW item is created, so the
        // item shows up this month at once instead of at the next monthly job (O3 in
        // docs/REVIEW-WAVE-A-B.md). Best effort and idempotent: the item exists either way.
        logFromToday(created)
        afterWriteRefresh()
        return created
    }

    /** `POST /transactions/generate` for one item: this month, entries dated today or later only. */
    private suspend fun logFromToday(created: RecurringTemplate) {
        if (!created.isActive) return
        val today = LocalDate.now()
        try {
            apiCall {
                api.generate(
                    GenerateTransactionsRequest(
                        targetDate = generateTargetDate(YearMonth.from(today)),
                        templateIds = listOf(created.id),
                        fromDate = today.toString(),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not fatal: the item is saved; Generate for a month can create these entries later.
        }
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

    override suspend fun generate(month: YearMonth, templateIds: List<String>?): GenerateTransactionsResult {
        val userId = activateUser()
        // The handler creates each template/date pair idempotently, so retrying this same month is safe.
        // Mid-month like iOS (YearMonth.date, day 15) and web ("<month>-15"): the handler parses the
        // date as UTC midnight and reads the month in the Lambda's local zone, so day 1 would
        // generate the previous month in any runtime whose zone is behind UTC.
        val result = apiCall { api.generate(GenerateTransactionsRequest(generateTargetDate(month), templateIds)) }
        ensureCurrentUser(userId)
        afterWriteRefresh()
        return result
    }

    override suspend fun pause(id: String, now: Instant) = update(id, BillLifecycleRequests.pause(now))

    override suspend fun resume(id: String) = update(id, BillLifecycleRequests.resume())

    override suspend fun end(id: String, keepUpcoming: Boolean, now: Instant) =
        update(id, BillLifecycleRequests.end(now, keepUpcoming))

    override suspend fun endAfterNextPayment(id: String) = update(id, BillLifecycleRequests.endAfterNextPayment())

    override suspend fun startAgain(id: String, nextDate: LocalDate) = update(id, BillLifecycleRequests.startAgain(nextDate))

    override suspend fun history(ids: List<String>, includeEntries: Boolean): Map<String, BillHistorySummary> {
        val wanted = ids.take(HISTORY_MAX_IDS)
        if (wanted.isEmpty()) return emptyMap()
        val userId = activateUser()
        val result = apiCall { api.history(wanted.joinToString(","), includeEntries.takeIf { it }) }
        ensureCurrentUser(userId)
        return result
    }

    override suspend fun monthEntries(month: YearMonth): List<Transaction> {
        val userId = activateUser()
        val all = mutableListOf<Transaction>()
        var cursor: String? = null
        repeat(ENTRY_PAGES) {
            val page = apiCall { api.entries(month.atDay(1).toString(), month.atEndOfMonth().toString(), cursor = cursor) }
            all += page.transactions
            cursor = page.pagination.nextCursor
            if (!page.pagination.hasMore || cursor == null) {
                ensureCurrentUser(userId)
                return all
            }
        }
        ensureCurrentUser(userId)
        return all
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

private const val HISTORY_MAX_IDS = 50
private const val ENTRY_PAGES = 5

/** `POST /transactions/generate` target for [month]: its 15th, never near a month boundary. */
internal fun generateTargetDate(month: YearMonth): String = month.atDay(15).toString()
