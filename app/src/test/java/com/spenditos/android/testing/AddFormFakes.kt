package com.spenditos.android.testing

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.merchants.AlwaysResult
import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.transactions.LastUsedCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

/** Records repeat creates; [failCreate] makes the next creates throw. */
class FakeRecurring(templates: List<RecurringTemplate> = emptyList()) : RecurringActions {
    val templates = MutableStateFlow(templates)
    val created = mutableListOf<CreateRecurringTemplateRequest>()
    var failCreate = false

    override fun observe(): Flow<List<RecurringTemplate>> = templates
    override fun observeMainIncomeId(): Flow<String?> = MutableStateFlow(null)
    override suspend fun refresh() = Unit
    override suspend fun create(request: CreateRecurringTemplateRequest): RecurringTemplate {
        if (failCreate) error("repeat failed")
        created += request
        return RecurringTemplate(
            id = "tpl-${created.size}", userId = "user-1", categoryId = request.categoryId, description = request.description,
            amount = request.amount, type = request.type, frequency = request.frequency, firstOccurrenceDate = request.firstOccurrenceDate,
        )
    }
    override suspend fun update(id: String, request: UpdateRecurringTemplateRequest): RecurringTemplate = error("unused")
    override suspend fun delete(id: String) = error("unused")
    override suspend fun setMainIncome(id: String?) = error("unused")
    override suspend fun generate(month: YearMonth): GenerateTransactionsResult = error("unused")
}

/** In-memory Rules API: records every call, fails on demand (`failure`, `failUpsert`, `failApply`, ...). */
class FakeMerchantMappings(rules: List<MerchantMapping> = emptyList()) : MerchantMappingActions {
    val rows = rules.toMutableList()
    var failure: Throwable? = null
    var failUpsert: Throwable? = null
    var failApply: Throwable? = null
    var failMatchCount: Throwable? = null
    var matchCountResult = 0
    var applyResult = 0

    val upserts = mutableListOf<Triple<String, RuleAction, String?>>()
    val updates = mutableListOf<Triple<String, RuleAction, String?>>()
    val deletes = mutableListOf<String>()
    val matchCounts = mutableListOf<Pair<String, String>>()
    val applies = mutableListOf<String>()
    val alwaysCalls = mutableListOf<Triple<String, String, Boolean>>()

    override suspend fun list(): List<MerchantMapping> { failure?.let { throw it }; return rows.toList() }
    override suspend fun rules(): List<MerchantMapping> = list().filter { it.isUserRule }
    override suspend fun upsertRule(merchant: String, action: RuleAction, categoryId: String?): MerchantMapping {
        (failUpsert ?: failure)?.let { throw it }
        upserts += Triple(merchant, action, categoryId)
        val existing = rows.indexOfFirst { it.rawPattern.equals(merchant, ignoreCase = true) }
        val saved = MerchantMapping(
            id = if (existing >= 0) rows[existing].id else "rule-${rows.size + 1}",
            userId = "u", rawPattern = merchant, cleanMerchantName = merchant,
            categoryId = categoryId.takeIf { action == RuleAction.CATEGORIZE }, action = action.wire,
        )
        if (existing >= 0) rows[existing] = saved else rows += saved
        return saved
    }
    override suspend fun update(id: String, action: RuleAction, categoryId: String?): MerchantMapping {
        failure?.let { throw it }
        updates += Triple(id, action, categoryId)
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) throw ApiError.Server(404, "NotFound")
        val saved = rows[index].copy(action = action.wire, categoryId = categoryId.takeIf { action == RuleAction.CATEGORIZE })
        rows[index] = saved
        return saved
    }
    override suspend fun delete(id: String) { failure?.let { throw it }; deletes += id; rows.removeAll { it.id == id } }
    override suspend fun matchCount(merchant: String, categoryId: String): Int {
        (failMatchCount ?: failure)?.let { throw it }
        matchCounts += merchant to categoryId
        return matchCountResult
    }
    override suspend fun apply(ruleId: String): Int { (failApply ?: failure)?.let { throw it }; applies += ruleId; return applyResult }
    override suspend fun always(merchant: String, categoryId: String, alsoUpdatePast: Boolean): AlwaysResult {
        alwaysCalls += Triple(merchant, categoryId, alsoUpdatePast)
        val rule = upsertRule(merchant, RuleAction.CATEGORIZE, categoryId)
        if (!alsoUpdatePast) return AlwaysResult(rule, null, false)
        return try {
            AlwaysResult(rule, apply(rule.id), false)
        } catch (_: ApiError) {
            AlwaysResult(rule, null, true)
        }
    }
}

class FakeLastUsed(var expense: String? = null, var income: String? = null) : LastUsedCategories {
    override suspend fun get(isIncome: Boolean): String? = if (isIncome) income else expense
    override suspend fun set(isIncome: Boolean, categoryId: String) {
        if (isIncome) income = categoryId else expense = categoryId
    }
}
