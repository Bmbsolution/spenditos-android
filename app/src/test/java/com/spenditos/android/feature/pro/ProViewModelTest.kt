package com.spenditos.android.feature.pro

import android.app.Activity
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.purchases.ProPackage
import com.spenditos.android.data.purchases.PurchaseResult
import com.spenditos.android.data.purchases.PurchasesService
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProViewModelTest {
    private class FakePurchases(
        override val isAvailable: Boolean = true,
        var packages: List<ProPackage> = listOf(
            ProPackage("monthly", "Monthly", "$4.99", "month", false),
            ProPackage("yearly", "Yearly", "$39.99", "year", true),
        ),
        var purchaseResult: PurchaseResult = PurchaseResult.Success,
        var restoreResult: PurchaseResult = PurchaseResult.Success,
    ) : PurchasesService {
        var purchased: String? = null
        override suspend fun logIn(userId: String) = Unit
        override suspend fun logOut() = Unit
        override suspend fun packages(): List<ProPackage> = packages
        override suspend fun purchase(activity: Activity, packageId: String): PurchaseResult { purchased = packageId; return purchaseResult }
        override suspend fun restore(): PurchaseResult = restoreResult
    }

    private fun proVm(purchases: PurchasesService, settings: FakeSettings) = ProViewModel(purchases, settings, settings.access)

    /** Pro turns on after [activeAfter] refreshes (the webhook lag); never when null. */
    private class FakeSettings(var activeAfter: Int?) : UserSettingsActions {
        val access = com.spenditos.android.testing.FakeProAccess()
        var status: com.spenditos.android.data.model.PremiumStatus? = null
        var refreshes = 0
        var failure: Throwable? = null
        override fun currency(): Flow<String> = flowOf("USD")
        override suspend fun refresh(): UserSettings {
            failure?.let { throw it }
            refreshes++
            return UserSettings("u", premium = PremiumAccess(active = activeAfter?.let { refreshes > it } == true), premiumStatus = status).also { access.apply(it, null) }
        }
        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings = error("unused")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = error("unused")
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val activity: Activity get() = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun `loads plans and preselects the best value`() = runTest {
        val vm = proVm(FakePurchases(), FakeSettings(activeAfter = null))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.packages.size)
        assertEquals("yearly", vm.state.value.selectedId)
        assertFalse(vm.state.value.isPro)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `an unavailable build says so and sells nothing`() = runTest {
        val vm = proVm(FakePurchases(isAvailable = false), FakeSettings(activeAfter = null))
        advanceUntilIdle()
        assertFalse(vm.state.value.isAvailable)
        assertTrue(vm.state.value.packages.isEmpty())
    }

    @Test
    fun `already Pro is shown as Pro`() = runTest {
        val vm = proVm(FakePurchases(), FakeSettings(activeAfter = 0))
        advanceUntilIdle()
        assertTrue(vm.state.value.isPro)
    }

    @Test
    fun `welcome premium still shows the plans, a grant counts as pro`() = runTest {
        val welcome = FakeSettings(activeAfter = null).apply { status = com.spenditos.android.data.model.PremiumStatus(true, "2026-11-12T00:00:00Z", "welcome") }
        val welcomeVm = proVm(FakePurchases(), welcome)
        advanceUntilIdle()
        assertFalse("a welcome user can still buy", welcomeVm.state.value.isPro)
        assertEquals(2, welcomeVm.state.value.packages.size)

        val grant = FakeSettings(activeAfter = null).apply { status = com.spenditos.android.data.model.PremiumStatus(true, null, "grant") }
        val grantVm = proVm(FakePurchases(), grant)
        advanceUntilIdle()
        assertTrue(grantVm.state.value.isPro)
    }

    @Test
    fun `a purchase waits for the server to flip Pro and then confirms`() = runTest {
        val purchases = FakePurchases()
        val settings = FakeSettings(activeAfter = 3) // initial load = refresh 1, then lag
        val vm = proVm(purchases, settings)
        advanceUntilIdle()
        vm.select("monthly")
        vm.buy(activity)
        advanceUntilIdle()
        assertEquals("monthly", purchases.purchased)
        assertTrue(vm.state.value.isPro)
        assertEquals("You have Pro. Thank you!", vm.state.value.notice)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `if the server never flips, the user is told it is processing`() = runTest {
        val settings = FakeSettings(activeAfter = null)
        val vm = proVm(FakePurchases(), settings)
        advanceUntilIdle()
        val before = settings.refreshes
        vm.buy(activity)
        advanceUntilIdle()
        assertFalse(vm.state.value.isPro)
        assertEquals("Your purchase is processing. Pro will turn on shortly.", vm.state.value.notice)
        assertEquals(ProViewModel.POLLS, settings.refreshes - before)
    }

    @Test
    fun `cancelled, pending and failed purchases are calm`() = runTest {
        val purchases = FakePurchases(purchaseResult = PurchaseResult.Cancelled)
        val vm = proVm(purchases, FakeSettings(activeAfter = null))
        advanceUntilIdle()
        vm.buy(activity); advanceUntilIdle()
        assertNull(vm.state.value.error); assertNull(vm.state.value.notice); assertFalse(vm.state.value.isBusy)
        purchases.purchaseResult = PurchaseResult.Pending
        vm.buy(activity); advanceUntilIdle()
        assertEquals("Your purchase is pending. Pro will turn on when it clears.", vm.state.value.notice)
        purchases.purchaseResult = PurchaseResult.Failed("The purchase didn't go through.")
        vm.buy(activity); advanceUntilIdle()
        assertEquals("The purchase didn't go through.", vm.state.value.error)
    }

    @Test
    fun `restore confirms through the server too`() = runTest {
        val vm = proVm(FakePurchases(), FakeSettings(activeAfter = 1))
        advanceUntilIdle()
        vm.restore(); advanceUntilIdle()
        assertTrue(vm.state.value.isPro)
    }

    @Test
    fun `no plans and a settings failure still render something sensible`() = runTest {
        val settings = FakeSettings(activeAfter = null).apply { failure = IOException("offline") }
        val vm = proVm(FakePurchases(packages = emptyList()), settings)
        advanceUntilIdle()
        assertFalse(vm.state.value.isPro)
        assertEquals("No plans are available right now. Try again later.", vm.state.value.error)
    }
}
