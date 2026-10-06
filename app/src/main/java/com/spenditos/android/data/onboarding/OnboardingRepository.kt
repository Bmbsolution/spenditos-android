package com.spenditos.android.data.onboarding

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.OnboardingCategoryRequest
import com.spenditos.android.data.model.UpdateOnboardingSettingsRequest
import com.spenditos.android.data.user.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface OnboardingActions {
    suspend fun needsOnboarding(): Boolean
    suspend fun detectCountry(): String?
    suspend fun complete(
        displayName: String,
        currency: String,
        country: String,
        language: String,
        seedCategories: List<OnboardingCategoryRequest>,
    )
}

/** Launch gate and quick setup writes for the authenticated user. */
@Singleton
class OnboardingRepository @Inject constructor(
    private val api: OnboardingApi,
    private val settings: UserSettingsRepository,
    private val categories: CategoryRepository,
    private val session: SessionManager,
    private val flags: OnboardingFlagStore,
) : OnboardingActions {
    /**
     * iOS `checkOnboardingStatus`: skip when this account already finished setup on this device;
     * otherwise ask the server. Only `setupCompleted = true` skips it. Any failure (including a 404
     * for an account with no settings yet) shows onboarding, so a new user is never blocked by a
     * temporary network error. Completing it again is safe (see [complete]).
     */
    override suspend fun needsOnboarding(): Boolean {
        val userId = session.currentUserId ?: return true
        if (flags.isCompleted(userId)) return false
        return try {
            val done = apiCall { api.settings() }.setupCompleted
            if (done) flags.markCompleted(userId)
            !done
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

    override suspend fun detectCountry(): String? =
        apiCall { api.detectCountry() }.country

    /** Seeds only an empty category list, then saves settings and refreshes both caches. */
    override suspend fun complete(
        displayName: String,
        currency: String,
        country: String,
        language: String,
        seedCategories: List<OnboardingCategoryRequest>,
    ) {
        val existingCategories = apiCall { api.categories() }
        if (existingCategories.isEmpty()) {
            apiCall { api.createCategories(seedCategories) }
        }

        val update = UpdateOnboardingSettingsRequest(
            displayName = displayName,
            currency = currency,
            country = country,
            language = language,
            setupCompleted = true,
        )
        val saved = apiCall { api.updateSettings(update) }
        // The backend's create path currently omits setupCompleted/language from its
        // create input. Once that first PUT creates the row, a second PUT updates it.
        if (!saved.setupCompleted) {
            val updated = apiCall { api.updateSettings(update) }
            if (!updated.setupCompleted) {
                throw IllegalStateException("The server did not save setup completion")
            }
        }
        session.currentUserId?.let { flags.markCompleted(it) }
        // Setup is saved. Warming the caches is best effort: failing here showed "Couldn't finish
        // setup" for a finished setup (SessionLifecycle and the screens refresh them anyway).
        try {
            settings.refresh()
            categories.refresh()
        } catch (_: ApiError) {
        }
    }
}
