package com.spenditos.android.data.purchases

import android.app.Activity
import com.paykit.core.Paykit
import com.paykit.core.PaykitConfiguration
import com.paykit.core.PaykitError
import com.paykit.core.PaykitErrorDetails
import com.paykit.core.PaykitPlan
import com.paykit.core.PaykitPlatform
import com.paykit.core.PaykitProduct
import com.paykit.core.PaykitReplacementMode
import com.paykit.core.Provider
import com.paykit.core.StorePurchaseOutcome
import com.paykit.core.UserAccess
import com.paykit.core.UserAccessPersistence
import com.paykit.core.testsupport.MockStoreProvider
import com.paykit.core.testsupport.StubTransport
import com.spenditos.android.data.model.PremiumAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaykitPurchasesServiceTest {
    private class Backend : PaymentsBackend {
        var userId: String? = "user-1"
        var tokenFailure: Exception? = null
        var syncResult = PaymentsSyncResult.SYNCED
        var syncs = 0
        val syncIfBehind = mutableListOf<Long>()
        var cleared = 0
        override fun currentUserId() = userId
        override suspend fun token(): String { tokenFailure?.let { throw it }; return "token" }
        override suspend fun sync(): PaymentsSyncResult { syncs++; return syncResult }
        override suspend fun syncIfBehind(version: Long) { syncIfBehind += version }
        override fun clear() { cleared++ }
    }

    private val store = MockStoreProvider()
    private val transport = StubTransport()
    private val backend = Backend()
    private var instances = 0
    private val activity: Activity get() = Robolectric.buildActivity(Activity::class.java).get()

    private fun service(scope: CoroutineScope, available: Boolean = true) = PaykitPurchasesService(available, backend, scope, "com.asaklex.spenditos", { store }) { tokenProvider, onAccessChanged ->
        instances++
        Paykit(
            configuration = PaykitConfiguration("https://paykit.test", PaykitPurchasesService.APP_ID),
            tokenProvider = tokenProvider,
            storeProvider = store,
            transport = transport,
            accessPersistence = object : UserAccessPersistence {
                override fun load(): UserAccess? = null
                override fun persist(access: UserAccess?) { access?.let(onAccessChanged) }
            },
        )
    }

    private fun plan(period: String, basePlan: String, order: Int? = null, highlight: String? = null) = PaykitPlan(
        PaykitPlatform.Android, "pro", basePlan, null, "pro", null, period, order, highlight,
    )

    private fun product(period: String, basePlan: String, order: Int? = null, highlight: String? = null) =
        PaykitProduct("pro", basePlan, plan(period, basePlan, order, highlight), "Spenditos Pro", "$period price")

    private val productsJson = """{"plans":[
        {"platform":"android","productId":"pro","basePlanId":"monthly","tierId":"pro","period":"P1M","displayOrder":2},
        {"platform":"android","productId":"pro","basePlanId":"yearly","tierId":"pro","period":"P1Y","displayOrder":1}]}"""
    private val identityJson = """{"appAccountToken":"a","obfuscatedAccountId":"owner-1"}"""
    private val accessJson = """{"tier":"pro","entitlements":{"pro":{"active":true,"accessUntil":null,"source":"google_play"}},"activeProvider":"google_play","version":4}"""

    private suspend fun loaded(service: PaykitPurchasesService): List<ProPackage> {
        store.catalog = listOf(
            PaykitProduct("pro", "monthly", displayName = "Spenditos Pro", displayPrice = "$4.99"),
            PaykitProduct("pro", "yearly", displayName = "Spenditos Pro", displayPrice = "$39.99"),
        )
        transport.enqueueJson(productsJson)
        return service.packages()
    }

    private fun purchasedOnStore() = store.enqueuePurchase(StorePurchaseOutcome.Success(MockStoreProvider.purchase("purchase-token", "pro")))

    @Test
    fun `packages follow the tenant plan's order and name plans by period`() = runTest {
        val packages = loaded(service(this))
        assertEquals(listOf("pro:yearly", "pro:monthly"), packages.map { it.id })
        assertEquals(listOf("Yearly", "Monthly"), packages.map { it.title })
        assertEquals(listOf("year", "month"), packages.map { it.period })
        assertEquals(listOf("$39.99", "$4.99"), packages.map { it.price })
    }

    @Test
    fun `yearly is best value next to monthly unless the tenant highlights another`() {
        val both = PaykitPurchasesService.mapProducts(listOf(product("P1M", "m"), product("P1Y", "y")))
        assertEquals(listOf(false, true), both.map { it.isBestValue })
        assertFalse(PaykitPurchasesService.mapProducts(listOf(product("P1Y", "y"))).single().isBestValue)
        val highlighted = PaykitPurchasesService.mapProducts(listOf(product("P1M", "m", highlight = "best_value"), product("P1Y", "y")))
        assertEquals(listOf(true, false), highlighted.map { it.isBestValue })
        assertTrue(PaykitPurchasesService.mapProducts(emptyList()).isEmpty())
    }

    @Test
    fun `an unknown or lifetime period keeps the store's name and no period`() {
        val lifetime = PaykitPurchasesService.mapProducts(listOf(product("lifetime", "l"))).single()
        assertNull(lifetime.period)
        assertEquals("Spenditos Pro", lifetime.title)
    }

    @Test
    fun `purchases need a url and never run in the test flavors`() {
        assertFalse(PaykitPurchasesService.isConfigurable("", isHarness = false, stage = "prod"))
        assertFalse(PaykitPurchasesService.isConfigurable("  ", isHarness = false, stage = "prod"))
        assertFalse(PaykitPurchasesService.isConfigurable("https://p", isHarness = true, stage = "harness"))
        assertFalse(PaykitPurchasesService.isConfigurable("https://p", isHarness = false, stage = "local"))
        assertTrue(PaykitPurchasesService.isConfigurable("https://p", isHarness = false, stage = "prod"))
        assertTrue(PaykitPurchasesService.isConfigurable("https://p", isHarness = false, stage = "staging"))
    }

    @Test
    fun `an unavailable build builds no sdk and sells nothing`() = runTest {
        val service = service(this, available = false)
        service.logIn("user-1")
        assertTrue(service.packages().isEmpty())
        assertEquals(PurchaseResult.Unavailable, service.purchase(activity, "pro:yearly"))
        assertEquals(PurchaseResult.Unavailable, service.restore())
        assertEquals(0, instances)
    }

    @Test
    fun `backend 503 surfaces as payments off, and nothing reaches paykit or the store`() = runTest {
        val service = service(this)
        val packages = loaded(service)
        val calls = transport.calls.size
        backend.tokenFailure = PaymentsUnavailableException()
        assertTrue(runCatching { service.packages() }.exceptionOrNull() is PaymentsUnavailableException)
        assertEquals(PurchaseResult.Unavailable, service.purchase(activity, packages.first().id))
        assertEquals(PurchaseResult.Unavailable, service.restore())
        assertTrue(store.purchaseCalls.isEmpty())
        assertEquals(calls, transport.calls.size)
    }

    @Test
    fun `a linked purchase syncs once and carries the ownership token`() = runTest(UnconfinedTestDispatcher()) {
        val service = service(this)
        val packages = loaded(service)
        transport.enqueueJson(identityJson)
        transport.enqueueJson(identityJson)
        purchasedOnStore()
        transport.enqueueJson(accessJson)

        assertEquals(PurchaseResult.Success, service.purchase(activity, packages.first().id))
        assertEquals("owner-1", store.purchaseCalls.single().obfuscatedAccountId)
        assertEquals("purchase-token", transport.calls.last().bodyString("purchaseToken"))
        assertEquals(1, backend.syncs)
        assertTrue("the purchase path syncs for itself", backend.syncIfBehind.isEmpty())
    }

    @Test
    fun `sync failing after a link is processing, not a failure`() = runTest {
        val service = service(this)
        val packages = loaded(service)
        transport.enqueueJson(identityJson)
        transport.enqueueJson(identityJson)
        purchasedOnStore()
        transport.enqueueJson(accessJson)
        backend.syncResult = PaymentsSyncResult.UNAVAILABLE
        assertEquals(PurchaseResult.Processing, service.purchase(activity, packages.first().id))
    }

    @Test
    fun `a link cut off after the store is processing, because the user may have paid`() = runTest {
        val service = service(this)
        val packages = loaded(service)
        transport.enqueueJson(identityJson)
        transport.enqueueJson(identityJson)
        purchasedOnStore()
        transport.enqueueResponder { throw PaykitError.Network("offline") }
        assertEquals(PurchaseResult.Processing, service.purchase(activity, packages.first().id))
        assertEquals("nothing linked, nothing to sync", 0, backend.syncs)
    }

    @Test
    fun `offline before the store sheet says so and opens nothing`() = runTest {
        val service = service(this)
        val packages = loaded(service)
        transport.enqueueFailure()
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.OFFLINE), service.purchase(activity, packages.first().id))
        assertTrue(store.purchaseCalls.isEmpty())
    }

    @Test
    fun `cancelled, pending and store-failed purchases pass through`() = runTest {
        val service = service(this)
        val id = loaded(service).first().id
        repeat(4) { transport.enqueueJson(identityJson) }
        store.enqueuePurchase(StorePurchaseOutcome.Cancelled)
        assertEquals(PurchaseResult.Cancelled, service.purchase(activity, id))
        store.enqueuePurchase(StorePurchaseOutcome.Pending)
        assertEquals(PurchaseResult.Pending, service.purchase(activity, id))
        store.enqueuePurchase(StorePurchaseOutcome.Failed("billing error 6"))
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.GENERIC), service.purchase(activity, id))
        assertEquals(0, backend.syncs)
    }

    @Test
    fun `a plan that was never loaded is refused`() = runTest {
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.PLAN_GONE), service(this).purchase(activity, "pro:yearly"))
    }

    @Test
    fun `link errors after the store are told apart`() {
        fun http(status: Int, code: String, provider: Provider? = null) = PaykitError.Http(PaykitErrorDetails(status, code, "", provider, null, null))
        val elsewhere = PaykitPurchasesService.afterStoreFailure(http(409, "cross_provider_active", Provider.AppleAppStore))
        assertEquals(PurchaseResult.Failed("You're already subscribed through Apple App Store. Manage your plan there."), elsewhere)
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.NOT_CONFIRMED), PaykitPurchasesService.afterStoreFailure(http(403, "ownership_mismatch")))
        assertEquals(PurchaseResult.Processing, PaykitPurchasesService.afterStoreFailure(http(503, "unavailable")))
        assertEquals(PurchaseResult.Processing, PaykitPurchasesService.afterStoreFailure(PaykitError.Network("IOException")))
    }

    @Test
    fun `restore links and syncs, and says when there is nothing`() = runTest {
        val service = service(this)
        assertEquals(PurchaseResult.NothingToRestore, service.restore())
        assertEquals(0, backend.syncs)

        store.currentPurchases = listOf(MockStoreProvider.purchase("purchase-token", "pro"))
        transport.enqueueJson(accessJson)
        assertEquals(PurchaseResult.Success, service.restore())
        assertEquals(1, backend.syncs)

        transport.enqueueFailure()
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.RESTORE_FAILED), service.restore())
    }

    @Test
    fun `an unexpected transport exception on restore is a calm failure`() = runTest {
        val service = service(this)
        store.currentPurchases = listOf(MockStoreProvider.purchase("purchase-token", "pro"))
        transport.enqueueFailure(IOException("broken pipe"))
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.RESTORE_FAILED), service.restore())
    }

    @Test
    fun `login completes an interrupted link and syncs only if the server is behind`() = withScope { scope ->
        val service = service(scope)
        store.currentPurchases = listOf(MockStoreProvider.purchase("purchase-token", "pro"))
        transport.enqueueJson(accessJson)
        service.logIn("user-1")
        scope.testScheduler.advanceUntilIdle()
        assertEquals("/v1/google/link", transport.calls.single().url.removePrefix("https://paykit.test"))
        assertEquals(listOf(4L), backend.syncIfBehind)
        assertEquals("no explicit sync: most launches change nothing", 0, backend.syncs)

        service.logIn("user-1")
        scope.testScheduler.advanceUntilIdle()
        assertEquals("one reconcile per session", 1, transport.calls.size)
        service.logOut()
    }

    @Test
    fun `a failed reconcile at login is swallowed and retried next session`() = withScope { scope ->
        val service = service(scope)
        store.currentPurchases = listOf(MockStoreProvider.purchase("purchase-token", "pro"))
        transport.enqueueFailure()
        service.logIn("user-1")
        scope.testScheduler.advanceUntilIdle()
        assertTrue(backend.syncIfBehind.isEmpty())
        service.logOut()
    }

    @Test
    fun `a pending purchase clearing later links and syncs`() = withScope { scope ->
        val service = service(scope)
        service.logIn("user-1")
        scope.testScheduler.advanceUntilIdle()
        transport.enqueueJson(accessJson)
        store.yieldUpdate(MockStoreProvider.purchase("cleared-token", "pro"))
        scope.testScheduler.advanceUntilIdle()
        assertEquals("cleared-token", transport.calls.single().bodyString("purchaseToken"))
        assertEquals(listOf(4L), backend.syncIfBehind)
        service.logOut()
    }

    @Test
    fun `an account switch never reuses the previous account's sdk`() = withScope { scope ->
        val service = service(scope)
        service.logIn("user-1")
        assertEquals(1, instances)
        backend.userId = "user-2"
        service.logIn("user-2")
        assertEquals(2, instances)

        service.logOut()
        assertEquals(1, backend.cleared)
        assertEquals("products were dropped with the user", PurchaseResult.Failed(PaykitPurchasesService.PLAN_GONE), service.purchase(activity, "pro:yearly"))
    }

    @Test
    fun `no session means nothing to bind`() = runTest {
        backend.userId = null
        val service = service(this)
        service.logIn("user-1")
        assertEquals(PurchaseResult.Unavailable, service.restore())
        assertEquals(0, instances)
    }

    @Test
    fun `a plan change replaces the device's purchase and syncs`() = runTest(UnconfinedTestDispatcher()) {
        val service = service(this)
        val packages = loaded(service)
        store.currentPurchases = listOf(MockStoreProvider.purchase("old-token", "pro"))
        transport.enqueueJson(identityJson)
        transport.enqueueJson(identityJson)
        store.enqueuePurchase(StorePurchaseOutcome.Success(MockStoreProvider.purchase("new-token", "pro")))
        transport.enqueueJson(accessJson)

        assertEquals(PurchaseResult.Success, service.changePlan(activity, packages.first { it.period == "month" }.id, "P1Y"))
        val call = store.purchaseCalls.single()
        assertEquals("old-token", call.oldPurchaseToken)
        assertEquals("yearly to monthly waits for the paid year", PaykitReplacementMode.Deferred, call.replacementMode)
        assertEquals(1, backend.syncs)
    }

    @Test
    fun `no purchase on this device means no plan change, never a second subscription`() = runTest {
        val service = service(this)
        val id = loaded(service).first().id
        transport.enqueueJson(identityJson)
        val result = service.changePlan(activity, id, "P1M")
        assertEquals(PurchaseResult.Failed("Change plans from the Google Play account you subscribed with."), result)
        assertTrue(store.purchaseCalls.isEmpty())
    }

    @Test
    fun `an unfinished plan change reads as what happened`() = runTest {
        val service = service(this)
        val id = loaded(service).first().id
        store.currentPurchases = listOf(MockStoreProvider.purchase("old-token", "pro"))
        repeat(4) { transport.enqueueJson(identityJson) }
        store.enqueuePurchase(StorePurchaseOutcome.Cancelled)
        assertEquals(PurchaseResult.Cancelled, service.changePlan(activity, id, "P1M"))
        store.enqueuePurchase(StorePurchaseOutcome.Pending)
        assertEquals(PurchaseResult.Pending, service.changePlan(activity, id, "P1M"))
        store.enqueuePurchase(StorePurchaseOutcome.Failed("billing error 6"))
        assertEquals(PurchaseResult.Failed(PaykitPurchasesService.GENERIC), service.changePlan(activity, id, "P1M"))
        assertEquals(0, backend.syncs)
    }

    @Test
    fun `longer plans apply now, shorter ones at period end`() {
        assertEquals(PaykitReplacementMode.Prorated, PaykitPurchasesService.replacementMode("P1M", "P1Y"))
        assertEquals(PaykitReplacementMode.Deferred, PaykitPurchasesService.replacementMode("P1Y", "P1M"))
        assertEquals(PaykitReplacementMode.Prorated, PaykitPurchasesService.replacementMode(null, "P1M"))
        assertEquals(PaykitReplacementMode.Prorated, PaykitPurchasesService.replacementMode("P1Y", "lifetime"))
    }

    @Test
    fun `manage routes by provider`() = runTest {
        val service = service(this)
        fun access(provider: String?, product: String? = null) = PremiumAccess(
            active = true, activeProvider = provider, version = 1,
            plan = product?.let { com.spenditos.android.data.model.PremiumPlan(provider, it, "P1M", null) },
        )
        assertEquals(
            ManageResult.Open("https://play.google.com/store/account/subscriptions?package=com.asaklex.spenditos&sku=pro+monthly"),
            service.manageUrl(access("google_play", "pro monthly")),
        )
        assertEquals(ManageResult.Open("https://play.google.com/store/account/subscriptions"), service.manageUrl(access("google_play")))
        assertEquals(ManageResult.Open("https://apps.apple.com/account/subscriptions"), service.manageUrl(access("apple_appstore")))

        transport.enqueueJson("""{"url":"https://billing.test/session"}""")
        assertEquals(ManageResult.Open("https://billing.test/session"), service.manageUrl(access("stripe")))
        assertEquals("https://spenditos.com", transport.calls.single().bodyString("returnUrl"))

        backend.tokenFailure = PaymentsUnavailableException()
        assertEquals(ManageResult.Unavailable, service.manageUrl(access("stripe")))
        backend.tokenFailure = null
        transport.enqueueFailure()
        assertEquals(ManageResult.Failed(PaykitPurchasesService.PORTAL_FAILED), service.manageUrl(access("stripe")))
        assertEquals(ManageResult.Failed(PaykitPurchasesService.PORTAL_FAILED), service.manageUrl(access("manual")))
    }

    @Test
    fun `store pages need no paykit, so an inert build still links to them`() = runTest {
        val service = service(this, available = false)
        assertEquals(
            ManageResult.Open("https://play.google.com/store/account/subscriptions"),
            service.manageUrl(PremiumAccess(active = true, activeProvider = "google_play", version = 1)),
        )
        assertEquals(ManageResult.Unavailable, service.manageUrl(PremiumAccess(active = true, activeProvider = "stripe", version = 1)))
        assertEquals(0, instances)
    }

    @Test
    fun `the paid provider follows Paykit, then the pre-Paykit source`() {
        assertEquals(Provider.GooglePlay, paidProvider(PremiumAccess(active = true, activeProvider = "google_play", version = 1)))
        assertNull("manual only on a Paykit item", paidProvider(PremiumAccess(active = true, source = "app_store", version = 1)))
        assertEquals(Provider.AppleAppStore, paidProvider(PremiumAccess(active = true, source = "app_store")))
        assertEquals(Provider.Stripe, paidProvider(PremiumAccess(active = true, source = "stripe_web")))
        assertNull(paidProvider(PremiumAccess(active = true, source = "manual")))
        assertNull("a store this version doesn't know", paidProvider(PremiumAccess(active = true, activeProvider = "a_new_store", version = 1)))
        assertNull("revoked", paidProvider(PremiumAccess(active = false, activeProvider = "google_play", version = 1)))
    }

    @Test
    fun `the deletion notice names only store subscriptions on active access`() {
        val play = storeSubscriptionNotice(PremiumAccess(active = true, activeProvider = "google_play"))
        assertTrue(play!!.contains("Google Play"))
        assertTrue(storeSubscriptionNotice(PremiumAccess(active = true, activeProvider = "apple_appstore"))!!.contains("App Store"))
        assertNull("the backend cancels Stripe", storeSubscriptionNotice(PremiumAccess(active = true, activeProvider = "stripe")))
        assertNull(storeSubscriptionNotice(PremiumAccess(active = true, activeProvider = "manual")))
        assertNull(storeSubscriptionNotice(PremiumAccess(active = true, activeProvider = "a_new_store")))
        assertNull(storeSubscriptionNotice(PremiumAccess(active = true)))
        assertNull("revoked", storeSubscriptionNotice(PremiumAccess(active = false, activeProvider = "google_play")))
        assertNull(storeSubscriptionNotice(null))
    }

    /** The updates listener runs until logOut, so it gets a scope of its own instead of the test's. */
    private fun withScope(block: suspend (TestScope) -> Unit) = runTest {
        block(TestScope(testScheduler))
    }
}
