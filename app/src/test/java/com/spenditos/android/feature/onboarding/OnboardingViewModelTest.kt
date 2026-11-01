package com.spenditos.android.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.onboarding.OnboardingActions
import com.spenditos.android.data.model.OnboardingCategoryRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `country detection applies country currency and sends the resolved app language`() = runTest(mainDispatcherRule.dispatcher) {
        val actions = FakeOnboardingActions(country = "CA")
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.CANADA_FRENCH)
        val viewModel = try { OnboardingViewModel(actions, SavedStateHandle()) } finally { java.util.Locale.setDefault(previous) }

        viewModel.initialize()
        advanceUntilIdle()

        assertEquals("CA", viewModel.state.value.country)
        assertEquals("CAD", viewModel.state.value.currency)
        assertEquals("fr", viewModel.state.value.language)
        assertFalse(viewModel.state.value.isDetectingCountry)
    }

    @Test
    fun `wizard answers and current page restore from saved state`() = runTest(mainDispatcherRule.dispatcher) {
        val savedState = SavedStateHandle()
        val viewModel = OnboardingViewModel(FakeOnboardingActions(), savedState)
        viewModel.onContinue()
        viewModel.onNameChange("Rae")
        viewModel.onContinue()
        viewModel.onCountryChange("FR")
        viewModel.onCurrencyChange("EUR")
        viewModel.onContinue()

        val restored = OnboardingViewModel(FakeOnboardingActions(), savedState)
        assertEquals(3, restored.state.value.page)
        assertEquals("Rae", restored.state.value.displayName)
        assertEquals("FR", restored.state.value.country)
        assertEquals("EUR", restored.state.value.currency)
    }

    @Test
    fun `successful completion emits finished event`() = runTest(mainDispatcherRule.dispatcher) {
        val actions = FakeOnboardingActions()
        val viewModel = readyViewModel(actions)
        val finished = async { viewModel.events.first() }

        viewModel.finish()
        advanceUntilIdle()

        assertEquals(OnboardingEvent.Finished, finished.await())
        assertEquals(listOf("Rae", "CAD", "CA", "en"), actions.completedWith)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `settings rejection stays on ready page and offers retry`() = runTest(mainDispatcherRule.dispatcher) {
        val actions = FakeOnboardingActions(completeError = ApiError.Server(400, "Validation"))
        val viewModel = readyViewModel(actions)

        viewModel.finish()
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.page)
        assertEquals("Couldn't finish setup. Please retry.", viewModel.state.value.error)
        assertEquals(OnboardingErrorKind.COMPLETE, viewModel.state.value.errorKind)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `network failure gets offline copy and retry can finish`() = runTest(mainDispatcherRule.dispatcher) {
        val actions = FakeOnboardingActions(completeError = ApiError.Network(IOException("offline")))
        val viewModel = readyViewModel(actions)

        viewModel.finish()
        advanceUntilIdle()
        assertEquals("You're offline. Check your connection and retry.", viewModel.state.value.error)

        val finished = async { viewModel.events.first() }
        actions.completeError = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(OnboardingEvent.Finished, finished.await())
    }

    @Test
    fun `country detection errors can be retried without losing the local default`() = runTest(mainDispatcherRule.dispatcher) {
        val actions = FakeOnboardingActions(countryError = ApiError.Network(IOException("offline")))
        val viewModel = OnboardingViewModel(actions, SavedStateHandle())

        viewModel.initialize()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error!!.contains("offline"))

        actions.countryError = null
        actions.country = "CA"
        viewModel.retry()
        advanceUntilIdle()
        assertEquals("CA", viewModel.state.value.country)
        assertEquals("CAD", viewModel.state.value.currency)
        assertEquals(null, viewModel.state.value.error)
    }

    private fun readyViewModel(actions: FakeOnboardingActions): OnboardingViewModel = OnboardingViewModel(
        actions,
        SavedStateHandle(
            mapOf(
                "onboarding.page" to 3,
                "onboarding.name" to "Rae",
                "onboarding.country" to "CA",
                "onboarding.currency" to "CAD",
            ),
        ),
    )

    private class FakeOnboardingActions(
        var country: String? = "US",
        var countryError: Throwable? = null,
        var completeError: Throwable? = null,
    ) : OnboardingActions {
        var completedWith: List<String>? = null

        override suspend fun needsOnboarding(): Boolean = true

        override suspend fun detectCountry(): String? {
            countryError?.let { throw it }
            return country
        }

        override suspend fun complete(
            displayName: String,
            currency: String,
            country: String,
            language: String,
            seedCategories: List<OnboardingCategoryRequest>,
        ) {
            completedWith = listOf(displayName, currency, country, language)
            completeError?.let { throw it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}
