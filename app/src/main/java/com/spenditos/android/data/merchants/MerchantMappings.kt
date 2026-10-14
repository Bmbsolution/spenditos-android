package com.spenditos.android.data.merchants

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.MerchantApplyResult
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.MerchantMappingList
import com.spenditos.android.data.model.MerchantMatchCount
import com.spenditos.android.data.model.RuleAction
import com.spenditos.android.data.model.UpdateRuleRequest
import com.spenditos.android.data.model.UpsertRuleRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag
import javax.inject.Inject
import javax.inject.Singleton

/** Routes of packages/functions/src/api/merchant-mapping.ts (Rules, #358). */
interface MerchantMappingApi {
    /** Every mapping (user, ai and system). */
    @GET("settings/merchant-mappings")
    suspend fun list(): ApiEnvelope<MerchantMappingList>

    /** An upsert by normalized `rawPattern`: 201 when created, 200 when an existing rule was updated. */
    @POST("settings/merchant-mappings")
    suspend fun upsert(@Body body: UpsertRuleRequest, @Tag sessionBinding: SessionBinding? = null): ApiEnvelope<MerchantMapping>

    /** 404 `NotFound`; 409 `RuleExists` when a rename would clash; 400 `CategoryNotFound`; 409 `CategoryBeingDeleted`. */
    @PUT("settings/merchant-mappings/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateRuleRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<MerchantMapping>

    /** 204; the body is ignored. */
    @DELETE("settings/merchant-mappings/{id}")
    suspend fun delete(@Path("id") id: String, @Tag sessionBinding: SessionBinding? = null)

    /** Past transactions (24 months, capped at 1000) that match [merchant] and are not already in [categoryId]. */
    @GET("settings/merchant-mappings/match-count")
    suspend fun matchCount(@Query("merchant") merchant: String, @Query("categoryId") categoryId: String): ApiEnvelope<MerchantMatchCount>

    /** No body; up to ~30 s. Idempotent. 409 `SkipRuleCannotApply` for a skip rule. */
    @POST("settings/merchant-mappings/{id}/apply")
    suspend fun apply(@Path("id") id: String, @Tag sessionBinding: SessionBinding? = null): ApiEnvelope<MerchantApplyResult>
}

/** The user's categories, for the optional category of a rule. */
fun interface MerchantCategories {
    fun observe(): kotlinx.coroutines.flow.Flow<List<com.spenditos.android.data.model.Category>>
}

/** The signed-in account a rule write is bound to (see [SessionBinding]); null when signed out. */
fun interface MerchantSession {
    fun binding(): SessionBinding?
}

/** What the "Always" flow did: the saved rule and, when asked, how the past-transactions update went. */
data class AlwaysResult(
    val rule: MerchantMapping,
    /** Past transactions updated; null when not requested or the call failed. */
    val pastUpdated: Int?,
    val applyFailed: Boolean,
)

interface MerchantMappingActions {
    /** Every mapping (user, AI and system): the add-transaction suggestions have always read all of them. */
    suspend fun list(): List<MerchantMapping>

    /** Only the user's Rules (`source == "user"`). */
    suspend fun rules(): List<MerchantMapping>

    /** Create or update the user's rule for [merchant]. */
    suspend fun upsertRule(merchant: String, action: RuleAction = RuleAction.CATEGORIZE, categoryId: String?): MerchantMapping

    suspend fun update(id: String, action: RuleAction, categoryId: String?): MerchantMapping

    suspend fun delete(id: String)

    /** How many past transactions (last 24 months) match the merchant and are not already in [categoryId]. */
    suspend fun matchCount(merchant: String, categoryId: String): Int

    /** Recategorizes those past transactions to the rule's category; returns how many changed. */
    suspend fun apply(ruleId: String): Int

    /**
     * The "Always" write: upsert the categorize rule, then (when [alsoUpdatePast]) apply it to past transactions.
     * Both requests are bound to the session that was signed in when this started, so an account switch in between
     * can never send the first account's rule id with the second account's token. Throws if the upsert fails;
     * a failed apply is reported in the result.
     */
    suspend fun always(merchant: String, categoryId: String, alsoUpdatePast: Boolean): AlwaysResult
}

/**
 * Online only: rules shape how the server files scans and imports, so every change waits for the server. User-scoped by
 * the bearer token; writes are bound to the signed-in session. Nothing here logs merchant names.
 */
@Singleton
class MerchantMappingRepository @Inject constructor(
    private val api: MerchantMappingApi,
    private val session: MerchantSession,
    private val invalidations: FinanceInvalidationBus,
) : MerchantMappingActions {
    override suspend fun list(): List<MerchantMapping> = apiCall { api.list() }.mappings

    override suspend fun rules(): List<MerchantMapping> = list().filter { it.isUserRule }

    override suspend fun upsertRule(merchant: String, action: RuleAction, categoryId: String?): MerchantMapping {
        val binding = bound()
        return apiCall { api.upsert(UpsertRuleRequest.of(merchant, action, categoryId), binding) }
    }

    override suspend fun update(id: String, action: RuleAction, categoryId: String?): MerchantMapping {
        val binding = bound()
        return apiCall { api.update(id, UpdateRuleRequest.of(action, categoryId), binding) }
    }

    override suspend fun delete(id: String) {
        val binding = bound()
        apiCallUnit { api.delete(id, binding) }
    }

    override suspend fun matchCount(merchant: String, categoryId: String): Int =
        apiCall { api.matchCount(merchant, categoryId) }.count

    override suspend fun apply(ruleId: String): Int = apply(ruleId, bound())

    private suspend fun apply(ruleId: String, binding: SessionBinding): Int {
        val updated = apiCall { api.apply(ruleId, binding) }.updated
        // Past transactions changed category: every transaction-based screen refreshes (the server also pushes data_changed).
        if (updated > 0) invalidations.invalidate(Aggregate.TRANSACTION)
        return updated
    }

    override suspend fun always(merchant: String, categoryId: String, alsoUpdatePast: Boolean): AlwaysResult {
        val binding = bound()
        val rule = apiCall { api.upsert(UpsertRuleRequest.of(merchant, RuleAction.CATEGORIZE, categoryId), binding) }
        if (!alsoUpdatePast) return AlwaysResult(rule, pastUpdated = null, applyFailed = false)
        return try {
            AlwaysResult(rule, pastUpdated = apply(rule.id, binding), applyFailed = false)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            AlwaysResult(rule, pastUpdated = null, applyFailed = true)
        }
    }

    private fun bound(): SessionBinding = session.binding() ?: throw ApiError.Unauthorized
}

@Module
@InstallIn(SingletonComponent::class)
object MerchantMappingModule {
    @Provides @Singleton
    fun merchantMappingApi(retrofit: Retrofit): MerchantMappingApi = retrofit.create(MerchantMappingApi::class.java)

    @Provides @Singleton
    fun merchantMappingActions(impl: MerchantMappingRepository): MerchantMappingActions = impl

    @Provides
    fun merchantCategories(repository: com.spenditos.android.data.categories.CategoryRepository): MerchantCategories =
        MerchantCategories { repository.observe() }

    @Provides
    fun merchantSession(session: SessionManager): MerchantSession = MerchantSession { session.currentBinding }
}
