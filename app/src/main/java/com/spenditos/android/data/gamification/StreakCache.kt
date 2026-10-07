package com.spenditos.android.data.gamification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spenditos.android.core.auth.SessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.streakPrefs by preferencesDataStore(name = "streak_cache")

/**
 * The last streak the app fetched, per user, for the widget (iOS writes `streakDays` to the app
 * group the same way). The widget never calls the network.
 */
interface StreakCache {
    suspend fun get(): Int?
    suspend fun set(days: Int)
}

@Singleton
class DataStoreStreakCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
) : StreakCache {
    override suspend fun get(): Int? {
        val userId = session.currentUserId ?: return null
        return context.streakPrefs.data.first()[key(userId)]
    }

    override suspend fun set(days: Int) {
        val userId = session.currentUserId ?: return
        context.streakPrefs.edit { it[key(userId)] = days }
    }

    private fun key(userId: String) = intPreferencesKey("streak.$userId")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StreakCacheModule {
    @Binds
    abstract fun bindStreakCache(cache: DataStoreStreakCache): StreakCache
}
