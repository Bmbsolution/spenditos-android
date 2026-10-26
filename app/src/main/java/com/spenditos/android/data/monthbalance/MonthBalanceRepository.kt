package com.spenditos.android.data.monthbalance

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.apiCall
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit

interface MonthBalanceActions {
    /** `GET /month-balance?month=`; [month] is the device's local month. Not cached. */
    suspend fun get(month: String): MonthBalanceData

    /** `PUT /month-balance`. Online only; one record per user and month, so a repeat is harmless. */
    suspend fun set(month: String, choice: MonthBalanceChoice): MonthBalanceData
}

/**
 * Month-start prompt service (iOS `MonthBalanceService`). The read is not cached (it changes when last
 * month's entries change). The answer is an online-only write bound to the signed-in session: it never
 * touches the offline queue, and a response for another account is dropped. The server never writes a
 * transaction in this feature.
 */
@Singleton
class MonthBalanceRepository @Inject constructor(
    private val api: MonthBalanceApi,
    private val session: SessionManager,
    private val connectivity: ConnectivityStatus,
) : MonthBalanceActions {
    override suspend fun get(month: String): MonthBalanceData {
        val userId = session.currentUserId ?: throw ApiError.Unauthorized
        val data = apiCall { api.get(month) }
        if (session.currentUserId != userId) throw ApiError.Unauthorized
        return data.normalized()
    }

    override suspend fun set(month: String, choice: MonthBalanceChoice): MonthBalanceData {
        require(choice != MonthBalanceChoice.UNKNOWN) { "UNKNOWN is not an answer" }
        val binding = session.currentBinding ?: throw ApiError.Unauthorized
        if (!connectivity.isOnline.value) throw ApiError.Network(IOException("Offline"))
        val data = apiCall { api.set(SetMonthBalanceChoiceRequest(month, choice), binding) }
        if (session.currentBinding != binding) throw ApiError.Unauthorized
        return data.normalized()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object MonthBalanceModule {
    @Provides
    @Singleton
    fun monthBalanceApi(retrofit: Retrofit): MonthBalanceApi = retrofit.create(MonthBalanceApi::class.java)

    @Provides
    @Singleton
    fun monthBalanceActions(repository: MonthBalanceRepository): MonthBalanceActions = repository
}
