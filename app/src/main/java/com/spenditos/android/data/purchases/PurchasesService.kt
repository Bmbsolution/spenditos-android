package com.spenditos.android.data.purchases

import android.app.Activity
import com.paykit.core.Paykit
import com.paykit.core.Provider
import com.spenditos.android.data.model.PremiumAccess

/** One subscription choice shown on the Pro screen. */
data class ProPackage(
    val id: String,
    val title: String,
    val price: String,
    /** "month", "year", ... from the tenant plan; null when unknown. */
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

/** The calm line shown wherever payments are off. */
const val SUBSCRIPTIONS_UNAVAILABLE = "Subscriptions aren't available right now."

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
}

/**
 * Deleting the account doesn't stop store billing, so the user has to be told. Stripe is left out:
 * the backend cancels it on deletion (`DELETE /user/account`).
 */
fun storeSubscriptionNotice(premium: PremiumAccess?): String? {
    if (premium?.active != true) return null
    return when (val provider = premium.activeProvider?.let(Provider::from)) {
        Provider.AppleAppStore, Provider.GooglePlay -> Paykit.accountDeletionNotice(provider)
        else -> null
    }
}
