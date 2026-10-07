package com.spenditos.android.data.push

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.spenditos.android.BuildConfig
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.apiCallUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val Context.pushPrefs by preferencesDataStore(name = "push_prefs")

/** The FCM token, or null when Firebase is not configured (no google-services.json in this build). */
fun interface FcmTokenProvider {
    suspend fun currentToken(): String?
}

class FirebaseTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : FcmTokenProvider {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(task.result?.takeIf { task.isSuccessful })
            }
        }
    }
}

/** Device-level switch plus what the server was last told. */
interface PushSettings {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(value: Boolean)
    suspend fun lastRegistered(): String?
    suspend fun setLastRegistered(value: String?)
}

@Singleton
class DataStorePushSettings @Inject constructor(@ApplicationContext private val context: Context) : PushSettings {
    override val enabled: Flow<Boolean> = context.pushPrefs.data.map { it[ENABLED] ?: false }
    override suspend fun setEnabled(value: Boolean) { context.pushPrefs.edit { it[ENABLED] = value } }
    override suspend fun lastRegistered(): String? = context.pushPrefs.data.first()[LAST]
    override suspend fun setLastRegistered(value: String?) {
        context.pushPrefs.edit { if (value == null) it.remove(LAST) else it[LAST] = value }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST = stringPreferencesKey("last_registered") // "<userId>|<token>"
    }
}

/**
 * Registers this device's FCM token with the backend (`POST /user/device-token`, platform android) while
 * notifications are switched on and a user is signed in, and removes it on switch-off and sign-out.
 * Best effort: a failure here never blocks the UI or a sign-out.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val api: PushApi,
    private val session: SessionManager,
    private val tokens: FcmTokenProvider,
    private val settings: PushSettings,
    private val appVersion: AppVersion,
) {
    val enabled: Flow<Boolean> get() = settings.enabled

    /** Sets the switch; turning it on registers right away, turning it off unregisters. */
    suspend fun setEnabled(value: Boolean) {
        settings.setEnabled(value)
        if (value) syncRegistration() else unregister()
    }

    /** Called at session start and when FCM rotates the token. */
    suspend fun syncRegistration(newToken: String? = null) {
        val userId = session.currentUserId ?: return
        if (!settings.enabled.first()) return
        val token = newToken ?: try { tokens.currentToken() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return
        val marker = "$userId|$token"
        if (settings.lastRegistered() == marker) return
        try {
            apiCallUnit { api.register(DeviceTokenRequest(token = token, bundleId = appVersion.packageName, appVersion = appVersion.name)) }
            settings.setLastRegistered(marker)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retried at the next session start or token refresh.
        }
    }

    /** Removes this device's token for the signed-in user (switch off, or just before sign-out). */
    suspend fun unregister() {
        val last = settings.lastRegistered() ?: return
        val token = last.substringAfter('|')
        try {
            apiCallUnit { api.unregister(UnregisterDeviceTokenRequest(token)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The server drops stale tokens on its own; local state is cleared either way.
        }
        settings.setLastRegistered(null)
    }
}

data class AppVersion(val packageName: String, val name: String)

@Module
@InstallIn(SingletonComponent::class)
object PushModule {
    @Provides @Singleton
    fun pushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)

    @Provides @Singleton
    fun fcmTokenProvider(impl: FirebaseTokenProvider): FcmTokenProvider = impl

    @Provides @Singleton
    fun pushSettings(impl: DataStorePushSettings): PushSettings = impl

    @Provides @Singleton
    fun appVersion(@ApplicationContext context: Context): AppVersion = AppVersion(context.packageName, BuildConfig.VERSION_NAME)
}
