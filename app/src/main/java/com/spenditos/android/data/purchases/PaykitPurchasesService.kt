package com.spenditos.android.data.purchases

import android.app.Activity
import android.content.Context
import com.paykit.android.PlayBillingStoreProvider
import com.paykit.core.Paykit
import com.paykit.core.PaykitConfiguration
import com.paykit.core.PaykitError
import com.paykit.core.PaykitHttpRequest
import com.paykit.core.PaykitHttpResponse
import com.paykit.core.PaykitProduct
import com.paykit.core.PaykitReplacementMode
import com.paykit.core.PaykitTransport
import com.paykit.core.Provider
import com.paykit.core.PurchaseState
import com.paykit.core.RestoreResult
import com.paykit.core.StoreProviding
import com.paykit.core.UrlConnectionTransport
import com.paykit.core.UserAccess
import com.paykit.core.UserAccessPersistence
import com.spenditos.android.R
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.PremiumAccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
import com.paykit.core.PurchaseResult as PaykitPurchaseResult

/**
 * Paykit (Play Billing). Pro itself comes from the backend (`settings.premium`); this class only drives
 * the store flow, then has the backend pull the result (`POST /payments/sync`) so Pro shows without
 * waiting for the `access.changed` webhook. Inert unless BuildConfig.PAYKIT_BASE_URL is set.
 */
class PaykitPurchasesService internal constructor(
    override val isAvailable: Boolean,
    private val backend: PaymentsBackend,
    private val scope: CoroutineScope,
    private val packageName: String,
    private val store: () -> StoreProviding,
    private val newPaykit: (tokenProvider: suspend () -> String, onAccessChanged: (UserAccess) -> Unit) -> Paykit,
) : PurchasesService {
    /** The SDK caches the ownership token and access per instance, so an instance never outlives its user. */
    private class Bound(val userId: String, val paykit: Paykit) {
        var started = false
        var reconcile: Job? = null
    }

    private var bound: Bound? = null
    @Volatile private var products: Map<String, PaykitProduct> = emptyMap()

    /** Purchase and restore sync for themselves; only a delivery nobody is waiting for syncs on access change. */
    private val foregroundFlows = AtomicInteger()

    @Synchronized
    private fun bound(): Bound? {
        if (!isAvailable) return null
        val userId = backend.currentUserId() ?: return null
        bound?.takeIf { it.userId == userId }?.let { return it }
        unbind()
        return Bound(userId, newPaykit(backend::token, ::onAccessChanged)).also { bound = it }
    }

    @Synchronized
    private fun unbind() {
        bound?.let {
            it.reconcile?.cancel()
            it.paykit.stopPurchaseUpdatesListener()
        }
        bound = null
        products = emptyMap()
    }

    private fun onAccessChanged(access: UserAccess) {
        if (foregroundFlows.get() == 0) scope.launch { backend.syncIfBehind(access.version.toLong()) }
    }

    override suspend fun logIn(userId: String) = startListening(userId)

    /**
     * Completes what an interrupted link left behind: the server acknowledges on link, and Play refunds
     * a purchase left unacknowledged. Runs on its own so a slow store never holds up session start.
     */
    @Synchronized
    private fun startListening(userId: String) {
        val current = bound()?.takeIf { it.userId == userId && !it.started } ?: return
        current.started = true
        current.paykit.startPurchaseUpdatesListener(scope)
        current.reconcile = scope.launch {
            try {
                current.paykit.reconcileOutstandingPurchases()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Still unacknowledged, so Play keeps it; retried at next launch.
            }
        }
    }

    override suspend fun logOut() {
        unbind()
        backend.clear()
    }

    override suspend fun packages(): List<ProPackage> {
        val paykit = bound()?.paykit ?: return emptyList()
        backend.token() // Tells "payments off" apart: the SDK hides why its token provider failed.
        val list = paykit.products()
        products = list.associateBy(::packageId)
        return mapProducts(list)
    }

    override suspend fun purchase(activity: Activity, packageId: String): PurchaseResult {
        val paykit = bound()?.paykit ?: return PurchaseResult.Unavailable
        val product = products[packageId] ?: return PurchaseResult.Failed(PLAN_GONE)
        foregroundFlows.incrementAndGet()
        try {
            readyToSell(paykit)?.let { return it }
            return try {
                when (paykit.purchase(activity as Any?, product)) {
                    is PaykitPurchaseResult.Active -> confirmWithServer()
                    PaykitPurchaseResult.Pending -> PurchaseResult.Pending
                    PaykitPurchaseResult.Cancelled -> PurchaseResult.Cancelled
                    is PaykitPurchaseResult.Failed -> PurchaseResult.Failed(GENERIC)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PaykitError) {
                afterStoreFailure(e)
            } catch (_: Exception) {
                PurchaseResult.Failed(NOT_CONFIRMED)
            }
        } finally {
            foregroundFlows.decrementAndGet()
        }
    }

    /** Fails before the store sheet opens: after it, an error may mean the user already paid. Null when ready. */
    private suspend fun readyToSell(paykit: Paykit): PurchaseResult? = try {
        backend.token()
        paykit.client.getIdentity()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (_: PaymentsUnavailableException) {
        PurchaseResult.Unavailable
    } catch (e: Exception) {
        PurchaseResult.Failed(if (e is PaykitError.Network || e is ApiError.Network) OFFLINE else GENERIC)
    }

    override suspend fun restore(): PurchaseResult {
        val paykit = bound()?.paykit ?: return PurchaseResult.Unavailable
        foregroundFlows.incrementAndGet()
        return try {
            backend.token()
            when (paykit.restore()) {
                is RestoreResult.Restored -> confirmWithServer()
                RestoreResult.NothingToRestore -> PurchaseResult.NothingToRestore
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentsUnavailableException) {
            PurchaseResult.Unavailable
        } catch (e: PaykitError) {
            crossProviderMessage(e)?.let { PurchaseResult.Failed(it) } ?: PurchaseResult.Failed(RESTORE_FAILED)
        } catch (_: Exception) {
            PurchaseResult.Failed(RESTORE_FAILED)
        } finally {
            foregroundFlows.decrementAndGet()
        }
    }

    override suspend fun changePlan(activity: Activity, packageId: String, currentPeriod: String?): PurchaseResult {
        val paykit = bound()?.paykit ?: return PurchaseResult.Unavailable
        val product = products[packageId] ?: return PurchaseResult.Failed(PLAN_GONE)
        foregroundFlows.incrementAndGet()
        try {
            readyToSell(paykit)?.let { return it }
            return try {
                // The SDK replaces this device's current purchase. Without one it would sell a second subscription.
                if (store().queryCurrentPurchases().none { it.state == PurchaseState.Purchased }) {
                    return PurchaseResult.Failed(Strings.get(R.string.data_purchases_change_plan_other_account))
                }
                paykit.changePlan(activity as Any?, product, replacementMode(currentPeriod, product.plan?.period))
                confirmWithServer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PaykitError.NotSupported) {
                planChangeNotCompleted(e)
            } catch (e: PaykitError) {
                afterStoreFailure(e)
            } catch (_: Exception) {
                PurchaseResult.Failed(NOT_CONFIRMED)
            }
        } finally {
            foregroundFlows.decrementAndGet()
        }
    }

    override suspend fun manageUrl(premium: PremiumAccess?): ManageResult = when (paidProvider(premium)) {
        Provider.GooglePlay -> ManageResult.Open(playSubscriptionsUrl(packageName, premium?.plan?.productId))
        Provider.AppleAppStore -> ManageResult.Open(APP_STORE_SUBSCRIPTIONS_URL)
        Provider.Stripe -> stripePortal()
        else -> ManageResult.Failed(PORTAL_FAILED)
    }

    private suspend fun stripePortal(): ManageResult {
        val paykit = bound()?.paykit ?: return ManageResult.Unavailable
        return try {
            backend.token()
            ManageResult.Open(paykit.manageSubscriptionRoute(Provider.Stripe, STRIPE_RETURN_URL).url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaymentsUnavailableException) {
            ManageResult.Unavailable
        } catch (_: Exception) {
            ManageResult.Failed(PORTAL_FAILED)
        }
    }

    private suspend fun confirmWithServer(): PurchaseResult =
        if (backend.sync() == PaymentsSyncResult.SYNCED) PurchaseResult.Success else PurchaseResult.Processing

    companion object {
        const val APP_ID = "spenditos"
        val GENERIC: String get() = Strings.get(R.string.data_purchases_the_purchase_didn_t_go)
        val OFFLINE: String get() = Strings.get(R.string.common_you_re_offline_check_your)
        val PLAN_GONE: String get() = Strings.get(R.string.data_purchases_that_plan_isn_t_available)
        val RESTORE_FAILED: String get() = Strings.get(R.string.data_purchases_couldn_t_restore_purchases_try)
        val NOT_CONFIRMED: String get() = Strings.get(R.string.data_purchases_could_not_confirm)
        val PORTAL_FAILED: String get() = Strings.get(R.string.data_purchases_portal_failed)

        private const val APP_STORE_SUBSCRIPTIONS_URL = "https://apps.apple.com/account/subscriptions"
        private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
        private const val STRIPE_RETURN_URL = "https://spenditos.com"

        /** Only builds with a Paykit URL, outside the test flavors, can sell anything. */
        internal fun isConfigurable(baseUrl: String, isHarness: Boolean, stage: String): Boolean =
            baseUrl.isNotBlank() && !isHarness && stage != "local"

        internal fun packageId(product: PaykitProduct): String =
            listOfNotNull(product.id, product.basePlanId ?: product.plan?.basePlanId).joinToString(":")

        /** The tenant plan's order and highlight; without a highlight, yearly is best value next to monthly. */
        internal fun mapProducts(products: List<PaykitProduct>): List<ProPackage> {
            val sorted = products.sortedBy { it.plan?.displayOrder ?: Int.MAX_VALUE }
            val hasHighlight = sorted.any { it.plan?.highlight == HIGHLIGHT_BEST_VALUE }
            val hasMonthly = sorted.any { periodName(it.plan?.period) == "month" }
            return sorted.map { product ->
                val period = periodName(product.plan?.period)
                ProPackage(
                    id = packageId(product),
                    title = when (period) {
                        "month" -> Strings.get(R.string.pro_plan_monthly)
                        "year" -> Strings.get(R.string.pro_plan_yearly)
                        else -> product.displayName.ifBlank { product.id }
                    },
                    price = product.displayPrice,
                    period = period,
                    isBestValue = if (hasHighlight) product.plan?.highlight == HIGHLIGHT_BEST_VALUE else hasMonthly && period == "year",
                )
            }
        }

        /** The Play page for this app's subscription, or the account's list without a known product. */
        internal fun playSubscriptionsUrl(packageName: String, productId: String?): String =
            if (productId.isNullOrEmpty()) PLAY_SUBSCRIPTIONS_URL
            else "$PLAY_SUBSCRIPTIONS_URL?package=$packageName&sku=${URLEncoder.encode(productId, "UTF-8")}"

        /** A shorter billing period waits for the end of the paid one; anything else applies now, prorated. */
        internal fun replacementMode(fromPeriod: String?, toPeriod: String?): PaykitReplacementMode {
            val from = periodMonths(fromPeriod)
            val to = periodMonths(toPeriod)
            return if (from != null && to != null && to < from) PaykitReplacementMode.Deferred else PaykitReplacementMode.Prorated
        }

        private fun periodMonths(period: String?): Int? = when (period) {
            "P1M" -> 1
            "P2M" -> 2
            "P3M" -> 3
            "P6M" -> 6
            "P1Y" -> 12
            else -> null
        }

        /** The SDK reports every unfinished plan change as NotSupported; its text is the only difference. */
        internal fun planChangeNotCompleted(error: PaykitError.NotSupported): PurchaseResult = when {
            error.what == "plan change was not completed" -> PurchaseResult.Cancelled
            error.what.contains("payment pending") -> PurchaseResult.Pending
            else -> PurchaseResult.Failed(GENERIC)
        }

        /**
         * The store sheet finished, so the user may have paid. A link that can't reach the server is still
         * unacknowledged: Play redelivers it and the next launch completes it, so this is not a failure.
         */
        internal fun afterStoreFailure(error: PaykitError): PurchaseResult {
            crossProviderMessage(error)?.let { return PurchaseResult.Failed(it) }
            val status = error.status
            return if (status != null && status in 400..499) PurchaseResult.Failed(NOT_CONFIRMED) else PurchaseResult.Processing
        }

        private fun crossProviderMessage(error: PaykitError): String? {
            if (error.code != "cross_provider_active") return null
            val provider = (error as? PaykitError.Http)?.details?.activeProvider
            val where = provider?.takeIf { it is Provider.AppleAppStore || it is Provider.GooglePlay || it is Provider.Stripe }?.displayName
            return if (where != null) Strings.get(R.string.data_purchases_already_subscribed_through, where) else Strings.get(R.string.data_purchases_already_subscribed)
        }

        private const val HIGHLIGHT_BEST_VALUE = "best_value"
    }
}

/** Reports every access write. The SDK's copy gates nothing here, so nothing is stored. */
private class AccessChangeListener(private val onChange: (UserAccess) -> Unit) : UserAccessPersistence {
    override fun load(): UserAccess? = null
    override fun persist(access: UserAccess?) {
        access?.let(onChange)
    }
}

/**
 * The SDK's transport lets an IOException escape while writing a POST body (offline during a link), which
 * would end its purchase-updates listener. It handles [PaykitError.Network] everywhere.
 */
private class NetworkErrorMappingTransport(private val delegate: PaykitTransport) : PaykitTransport {
    override suspend fun send(request: PaykitHttpRequest): PaykitHttpResponse = try {
        delegate.send(request)
    } catch (e: IOException) {
        throw PaykitError.Network(e::class.simpleName ?: "IOException")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PurchasesModule {
    @Binds abstract fun paymentsBackend(impl: PaymentsRepository): PaymentsBackend

    companion object {
        @Provides @Singleton
        fun paymentsApi(retrofit: Retrofit): PaymentsApi = retrofit.create(PaymentsApi::class.java)

        @Provides @Singleton
        fun purchasesService(
            @ApplicationContext context: Context,
            environment: AppEnvironment,
            backend: PaymentsBackend,
        ): PurchasesService {
            // One BillingClient for the process; created only when a user first needs it.
            val store by lazy { PlayBillingStoreProvider(context) }
            return PaykitPurchasesService(
                isAvailable = PaykitPurchasesService.isConfigurable(environment.paykitBaseUrl, environment.isHarness, environment.stage),
                backend = backend,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                packageName = context.packageName,
                store = { store },
            ) { tokenProvider, onAccessChanged ->
                Paykit(
                    configuration = PaykitConfiguration(environment.paykitBaseUrl, PaykitPurchasesService.APP_ID, androidPackageName = context.packageName),
                    tokenProvider = tokenProvider,
                    storeProvider = store,
                    transport = NetworkErrorMappingTransport(UrlConnectionTransport()),
                    accessPersistence = AccessChangeListener(onAccessChanged),
                )
            }
        }
    }
}
