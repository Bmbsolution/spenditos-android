package com.spenditos.android.data.purchases

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.PurchasesTransactionException
import com.spenditos.android.BuildConfig
import com.spenditos.android.core.config.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** One subscription choice shown on the Pro screen. */
data class ProPackage(
    val id: String,
    val title: String,
    val price: String,
    /** "month", "year", ... as the store reports it; null when unknown. */
    val period: String?,
    val isBestValue: Boolean,
)

sealed interface PurchaseResult {
    /** The store accepted it; the server entitles the user (webhook) a moment later. */
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    /** Waiting on a payment method (for example a cash payment); Pro turns on when it clears. */
    data object Pending : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/** Port in front of RevenueCat so the Pro screen is testable without the SDK. */
interface PurchasesService {
    /** False in builds without a RevenueCat key, and always in the harness/local test flavors. */
    val isAvailable: Boolean
    suspend fun logIn(userId: String)
    suspend fun logOut()
    suspend fun packages(): List<ProPackage>
    suspend fun purchase(activity: Activity, packageId: String): PurchaseResult
    suspend fun restore(): PurchaseResult
}

/**
 * RevenueCat (Play Billing). The entitlement itself comes from the backend (`premium.active`), fed by the
 * RevenueCat webhook; this class only drives the store flow. Inert unless BuildConfig.REVENUECAT_API_KEY is set.
 */
@Singleton
class RevenueCatPurchasesService @Inject constructor(
    @ApplicationContext private val context: Context,
    environment: AppEnvironment,
) : PurchasesService {
    private val apiKey = BuildConfig.REVENUECAT_API_KEY
    override val isAvailable: Boolean = isConfigurable(apiKey, environment.isHarness, environment.stage)
    private var configured = false
    private var lastPackages: Map<String, Package> = emptyMap()

    @Synchronized
    private fun ensureConfigured(): Boolean {
        if (!isAvailable) return false
        if (!configured) {
            if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.WARN
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
            configured = true
        }
        return true
    }

    override suspend fun logIn(userId: String) {
        if (!ensureConfigured()) return
        try { Purchases.sharedInstance.awaitLogIn(userId) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* retried at next launch */ }
    }

    override suspend fun logOut() {
        if (!ensureConfigured()) return
        try { Purchases.sharedInstance.awaitLogOut() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* anonymous id stays; harmless */ }
    }

    override suspend fun packages(): List<ProPackage> {
        if (!ensureConfigured()) return emptyList()
        val offering = Purchases.sharedInstance.awaitOfferings().current ?: return emptyList()
        lastPackages = offering.availablePackages.associateBy { it.identifier }
        return mapPackages(offering.availablePackages.map { it.toInfo() })
    }

    override suspend fun purchase(activity: Activity, packageId: String): PurchaseResult {
        if (!ensureConfigured()) return PurchaseResult.Failed(UNAVAILABLE)
        val target = lastPackages[packageId] ?: return PurchaseResult.Failed(Strings.get(R.string.data_purchases_that_plan_isn_t_available))
        return try {
            Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, target).build())
            PurchaseResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: PurchasesTransactionException) {
            classify(e.error, e.userCancelled)
        } catch (_: Exception) {
            PurchaseResult.Failed(GENERIC)
        }
    }

    override suspend fun restore(): PurchaseResult {
        if (!ensureConfigured()) return PurchaseResult.Failed(UNAVAILABLE)
        return try {
            Purchases.sharedInstance.awaitRestore()
            PurchaseResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PurchaseResult.Failed(Strings.get(R.string.data_purchases_couldn_t_restore_purchases_try))
        }
    }

    private fun classify(error: PurchasesError, userCancelled: Boolean): PurchaseResult = when {
        userCancelled || error.code == PurchasesErrorCode.PurchaseCancelledError -> PurchaseResult.Cancelled
        error.code == PurchasesErrorCode.PaymentPendingError -> PurchaseResult.Pending
        error.code == PurchasesErrorCode.NetworkError -> PurchaseResult.Failed(Strings.get(R.string.common_you_re_offline_check_your))
        error.code == PurchasesErrorCode.ProductAlreadyPurchasedError -> PurchaseResult.Success
        else -> PurchaseResult.Failed(GENERIC)
    }

    private fun Package.toInfo() = PackageInfo(
        id = identifier,
        title = product.title.substringBefore(" (").ifBlank { identifier },
        price = product.price.formatted,
        period = when (packageType) {
            PackageType.MONTHLY -> "month"
            PackageType.ANNUAL -> "year"
            PackageType.WEEKLY -> "week"
            PackageType.TWO_MONTH -> "2 months"
            PackageType.THREE_MONTH -> "3 months"
            PackageType.SIX_MONTH -> "6 months"
            PackageType.LIFETIME -> null
            else -> null
        },
    )

    companion object {
        const val UNAVAILABLE = "Subscriptions aren't available in this build."
        const val GENERIC = "The purchase didn't go through. You haven't been charged. Try again."

        /** Only builds with a RevenueCat key, outside the test flavors, can sell anything. */
        internal fun isConfigurable(apiKey: String, isHarness: Boolean, stage: String): Boolean =
            apiKey.isNotBlank() && !isHarness && stage != "local"

        internal data class PackageInfo(val id: String, val title: String, val price: String, val period: String?)

        /** The yearly plan is the best value when a monthly one exists next to it. */
        internal fun mapPackages(infos: List<PackageInfo>): List<ProPackage> {
            val hasMonthly = infos.any { it.period == "month" }
            return infos.map { ProPackage(it.id, it.title, it.price, it.period, isBestValue = hasMonthly && it.period == "year") }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PurchasesModule {
    @Provides @Singleton
    fun purchasesService(impl: RevenueCatPurchasesService): PurchasesService = impl
}
