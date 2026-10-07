package com.spenditos.android.testing

import com.spenditos.android.data.merchants.MerchantMappingActions
import com.spenditos.android.data.model.CreateMerchantMappingRequest
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateMerchantMappingRequest
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

class FakeMerchantMappings(private val rules: List<MerchantMapping> = emptyList()) : MerchantMappingActions {
    override suspend fun list(): List<MerchantMapping> = rules
    override suspend fun create(request: CreateMerchantMappingRequest): MerchantMapping = error("unused")
    override suspend fun update(id: String, request: UpdateMerchantMappingRequest): MerchantMapping = error("unused")
    override suspend fun delete(id: String) = error("unused")
}

class FakeLastUsed(var expense: String? = null, var income: String? = null) : LastUsedCategories {
    override suspend fun get(isIncome: Boolean): String? = if (isIncome) income else expense
    override suspend fun set(isIncome: Boolean, categoryId: String) {
        if (isIncome) income = categoryId else expense = categoryId
    }
}
