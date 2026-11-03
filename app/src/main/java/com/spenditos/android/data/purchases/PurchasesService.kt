package com.spenditos.android.data.purchases

import android.app.Activity
import com.paykit.core.Provider
import com.spenditos.android.R
import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.data.model.PremiumAccess

/** One subscription choice shown on the Pro screen. */
data class ProPackage(
    val id: String,
    val title: String,
    val price: String,
    /** "month", "year", ... ([periodName]); a key for the screen's copy, null when unknown. */
    val period: String?,
    val isBestValue: Boolean,
)

sealed interface PurchaseResult {
    /** Linked and synced: `settings.premium` as the server now reports it is already in `ProAccess`. */
    data object Success : PurchaseResult
    /** The store has the purchase but the link or sync hasn't finished; Pro turns on when it does. */
    data object Processing : PurchaseResult
    data object Cancelled : PurchaseResult
    /** Waiting on a payment method (for example a cash payment); Pro turns on when it clears. */
    data object Pending : PurchaseResult
    /** Restore found no purchase in this store account. */
    data object NothingToRestore : PurchaseResult
    /** Payments are off for this build or backend stage (`PaymentsUnavailable`); nothing went wrong. */
    data object Unavailable : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/** The backend answered 503 `PaymentsUnavailable`: Paykit is off on this stage or unreachable. */
class PaymentsUnavailableException : Exception("Payments are not available")

/** Where "Manage subscription" goes. */
sealed interface ManageResult {
    data class Open(val url: String) : ManageResult
    data object Unavailable : ManageResult
    data class Failed(val message: String) : ManageResult
}

/** Port in front of Paykit so the Pro screen is testable without the SDK. */
interface PurchasesService {
    /** False in builds without a Paykit URL, and always in the harness/local test flavors. */
    val isAvailable: Boolean
    suspend fun logIn(userId: String)
    suspend fun logOut()
    /** Throws [PaymentsUnavailableException] when the backend reports payments off. */
    suspend fun packages(): List<ProPackage>
    suspend fun purchase(activity: Activity, packageId: String): PurchaseResult
    suspend fun restore(): PurchaseResult
    /** Changes a Play subscription to [packageId]; [currentPeriod] (`P1M`) decides upgrade or deferred downgrade. */
    suspend fun changePlan(activity: Activity, packageId: String, currentPeriod: String?): PurchaseResult
    /** The provider's own place to manage [premium]: a store page, or Stripe's billing portal. */
    suspend fun manageUrl(premium: PremiumAccess?): ManageResult
}

/** "P1M" to "month"; null for lifetime plans and anything this version doesn't know. */
fun periodName(period: String?): String? = when (period) {
    "P1W", "P7D" -> "week"
    "P1M" -> "month"
    "P2M" -> "2 months"
    "P3M" -> "3 months"
    "P6M" -> "6 months"
    "P1Y" -> "year"
    else -> null
}

/**
 * The paid provider behind active access; null for a grant or Free, so there is nothing to manage. Items
 * from before Paykit have `source` and no `version`; on a Paykit item a null `activeProvider` means manual only.
 */
fun paidProvider(premium: PremiumAccess?): Provider? {
    if (premium?.active != true) return null
    val provider = if (premium.version != null) {
        premium.activeProvider?.let(Provider::from)
    } else {
        when (premium.source) {
            "app_store" -> Provider.AppleAppStore
            "play_store" -> Provider.GooglePlay
            "web", "stripe_web" -> Provider.Stripe
            else -> premium.activeProvider?.let(Provider::from)
        }
    }
    return provider?.takeIf { it is Provider.AppleAppStore || it is Provider.GooglePlay || it is Provider.Stripe }
}

/**
 * Deleting the account doesn't stop store billing, so the user has to be told. Stripe is left out:
 * the backend cancels it on deletion (`DELETE /user/account`).
 */
fun storeSubscriptionNotice(premium: PremiumAccess?): String? = when (paidProvider(premium)) {
    Provider.AppleAppStore -> Strings.get(R.string.settings_store_notice_app_store)
    Provider.GooglePlay -> Strings.get(R.string.settings_store_notice_google_play)
    else -> null
}
