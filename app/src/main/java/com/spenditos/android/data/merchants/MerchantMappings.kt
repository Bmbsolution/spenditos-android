package com.spenditos.android.data.merchants

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.core.network.apiCallUnit
import com.spenditos.android.data.model.CreateMerchantMappingRequest
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.UpdateMerchantMappingRequest
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
import javax.inject.Inject
import javax.inject.Singleton

/** Routes of packages/functions/src/api/merchant-mapping.ts (iOS' version of this screen is broken against them). */
interface MerchantMappingApi {
    @GET("settings/merchant-mappings")
    suspend fun list(): ApiEnvelope<List<MerchantMapping>>

    @POST("settings/merchant-mappings")
    suspend fun create(@Body body: CreateMerchantMappingRequest): ApiEnvelope<MerchantMapping>

    @PUT("settings/merchant-mappings/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateMerchantMappingRequest): ApiEnvelope<MerchantMapping>

    @DELETE("settings/merchant-mappings/{id}")
    suspend fun delete(@Path("id") id: String)
}

/** The user's categories, for the optional category of a name. */
fun interface MerchantCategories {
    fun observe(): kotlinx.coroutines.flow.Flow<List<com.spenditos.android.data.model.Category>>
}

interface MerchantMappingActions {
    suspend fun list(): List<MerchantMapping>
    suspend fun create(request: CreateMerchantMappingRequest): MerchantMapping
    suspend fun update(id: String, request: UpdateMerchantMappingRequest): MerchantMapping
    suspend fun delete(id: String)
}

/** Online only: these names shape what statement imports look like, so every change waits for the server. */
@Singleton
class MerchantMappingRepository @Inject constructor(private val api: MerchantMappingApi) : MerchantMappingActions {
    override suspend fun list(): List<MerchantMapping> = apiCall { api.list() }.sortedBy { it.cleanMerchantName.lowercase() }
    override suspend fun create(request: CreateMerchantMappingRequest): MerchantMapping = apiCall { api.create(request) }
    override suspend fun update(id: String, request: UpdateMerchantMappingRequest): MerchantMapping = apiCall { api.update(id, request) }
    override suspend fun delete(id: String) = apiCallUnit { api.delete(id) }
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
}
