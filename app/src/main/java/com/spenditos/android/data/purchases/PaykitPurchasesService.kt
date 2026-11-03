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
import com.paykit.core.PaykitTransport
import com.paykit.core.Provider
import com.paykit.core.RestoreResult
import com.paykit.core.UrlConnectionTransport
import com.paykit.core.UserAccess
import com.paykit.core.UserAccessPersistence
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
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

    private suspend fun confirmWithServer(): PurchaseResult =
        if (backend.sync() == PaymentsSyncResult.SYNCED) PurchaseResult.Success else PurchaseResult.Processing

    companion object {
        const val APP_ID = "spenditos"
        const val GENERIC = "The purchase didn't go through. You haven't been charged. Try again."
        const val OFFLINE = "You're offline. Check your connection and try again."
        const val PLAN_GONE = "That plan isn't available right now. Refresh and try again."
        const val RESTORE_FAILED = "Couldn't restore purchases. Try again."
        const val NOT_CONFIRMED = "We couldn't confirm this purchase. Try Restore Purchases, or contact support if it keeps happening."

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
                        "month" -> "Monthly"
                        "year" -> "Yearly"
                        else -> product.displayName.ifBlank { product.id }
                    },
                    price = product.displayPrice,
                    period = period,
                    isBestValue = if (hasHighlight) product.plan?.highlight == HIGHLIGHT_BEST_VALUE else hasMonthly && period == "year",
                )
            }
        }

        /** "P1M" to "month"; null for lifetime plans and anything this version doesn't know. */
        internal fun periodName(period: String?): String? = when (period) {
            "P1W", "P7D" -> "week"
            "P1M" -> "month"
            "P2M" -> "2 months"
            "P3M" -> "3 months"
            "P6M" -> "6 months"
            "P1Y" -> "year"
            else -> null
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
            return if (where != null) "You're already subscribed through $where. Manage your plan there." else "You're already subscribed. Manage your plan where you signed up."
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
