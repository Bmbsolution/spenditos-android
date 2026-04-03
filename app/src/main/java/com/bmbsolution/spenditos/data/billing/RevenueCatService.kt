package com.bmbsolution.spenditos.data.billing

import android.app.Application
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.getCustomerInfoWith
import com.bmbsolution.spenditos.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Product identifiers for RevenueCat
 */
object ProductIdentifiers {
    const val MONTHLY_PRO = "com.spenditos.pro.monthly"
    const val YEARLY_PRO = "com.spenditos.pro.yearly"
    const val LIFETIME_PRO = "com.spenditos.pro.lifetime"
}

/**
 * Entitlement identifiers
 */
object Entitlements {
    const val PRO = "pro"
}

/**
 * Subscription tier information
 */
data class SubscriptionTier(
    val isPro: Boolean = false,
    val expirationDate: java.util.Date? = null,
    val willRenew: Boolean = false,
    val isTrial: Boolean = false
)

/**
 * RevenueCat billing service for managing subscriptions
 */
@Singleton
class RevenueCatService @Inject constructor(
    private val application: Application
) {
    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _purchaseSuccess = MutableStateFlow(false)
    val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    /**
     * Initialize RevenueCat with API key from build configuration
     */
    fun configure() {
        val apiKey = BuildConfig.REVENUECAT_API_KEY

        if (apiKey.isBlank()) {
            android.util.Log.w("RevenueCatService", "RevenueCat API key not configured. Set REVENUECAT_API_KEY in build configuration.")
            return
        }

        Purchases.configure(
            PurchasesConfiguration.Builder(application, apiKey).build()
        )

        // Enable debug logs in development
        if (BuildConfig.DEBUG) {
            Purchases.logLevel = LogLevel.DEBUG
        }

        // Sync initial customer info
        refreshCustomerInfo()
    }

    /**
     * Refresh customer subscription status
     */
    fun refreshCustomerInfo() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                _errorMessage.value = error.message
            },
            onSuccess = { customerInfo ->
                _customerInfo.value = customerInfo
            }
        )
    }

    /**
     * Check if user has Pro tier
     */
    val isPro: Boolean
        get() = _customerInfo.value?.entitlements?.get(Entitlements.PRO)?.isActive == true

    /**
     * Get current subscription tier details
     */
    val currentTier: SubscriptionTier
        get() {
            val entitlement = _customerInfo.value?.entitlements?.get(Entitlements.PRO)
                ?: return SubscriptionTier(isPro = false)

            return SubscriptionTier(
                isPro = entitlement.isActive,
                expirationDate = entitlement.expirationDate,
                willRenew = entitlement.willRenew,
                isTrial = entitlement.periodType == com.revenuecat.purchases.PeriodType.TRIAL
            )
        }

    /**
     * Check if Pro features are available
     */
    fun checkProAccess(): Boolean = isPro

    /**
     * Fetch available subscription offerings
     */
    fun fetchOfferings() {
        _isLoading.value = true
        _errorMessage.value = null

        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                _isLoading.value = false
                _errorMessage.value = "Failed to load products: ${error.message}"
            },
            onSuccess = { offerings ->
                _isLoading.value = false
                _offerings.value = offerings
            }
        )
    }

    /**
     * Purchase a package
     */
    fun purchasePackage(
        activity: android.app.Activity,
        packageToPurchase: Package,
        onSuccess: (Boolean) -> Unit
    ) {
        _isLoading.value = true
        _errorMessage.value = null
        _purchaseSuccess.value = false

        Purchases.sharedInstance.purchaseWith(
            purchaseParams = com.revenuecat.purchases.PurchaseParams.Builder(activity, packageToPurchase).build(),
            onError = { error, _ ->
                _isLoading.value = false
                _errorMessage.value = error.message
                onSuccess(false)
            },
            onSuccess = { purchase, customerInfo, _ ->
                _isLoading.value = false
                _customerInfo.value = customerInfo
                _purchaseSuccess.value = true
                onSuccess(true)
            }
        )
    }

    /**
     * Restore previous purchases
     */
    fun restorePurchases(onSuccess: (Boolean) -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null

        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                _isLoading.value = false
                _errorMessage.value = error.message
                onSuccess(false)
            },
            onSuccess = { customerInfo ->
                _isLoading.value = false
                _customerInfo.value = customerInfo
                _purchaseSuccess.value = true
                onSuccess(true)
            }
        )
    }

    // MARK: - Pro Feature Gates

    /**
     * Gate receipt scanning (Pro feature)
     */
    fun canUseReceiptScanning(): Boolean = isPro

    /**
     * Gate CSV import (Pro feature)
     */
    fun canUseCSVImport(): Boolean = isPro

    /**
     * Gate bank statement import (Pro feature)
     */
    fun canUseStatementImport(): Boolean = isPro

    /**
     * Gate advanced reports (Pro feature)
     */
    fun canUseAdvancedReports(): Boolean = isPro

    /**
     * Gate unlimited transactions
     */
    fun canUseUnlimitedTransactions(): Boolean = isPro

    // MARK: - Free Tier Limits

    private val PREFS_NAME = "spenditos_prefs"
    private val KEY_SCANS_USED = "receipt_scans_used"
    private val KEY_SCANS_RESET_DATE = "scans_reset_date"

    /**
     * Get remaining free scans for non-Pro users
     */
    fun remainingFreeScans(): Int {
        if (isPro) return Int.MAX_VALUE

        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

        // Check if we need to reset counter (monthly)
        val resetDate = prefs.getLong(KEY_SCANS_RESET_DATE, 0)
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val resetMonth = java.util.Calendar.getInstance().apply { timeInMillis = resetDate }.get(java.util.Calendar.MONTH)

        if (currentMonth != resetMonth) {
            prefs.edit().putInt(KEY_SCANS_USED, 0).putLong(KEY_SCANS_RESET_DATE, System.currentTimeMillis()).apply()
        }

        val scansUsed = prefs.getInt(KEY_SCANS_USED, 0)
        return maxOf(0, 3 - scansUsed) // 3 free scans per month
    }

    /**
     * Record a scan usage
     */
    fun recordScanUsage() {
        if (isPro) return

        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_SCANS_USED, 0)
        prefs.edit().putInt(KEY_SCANS_USED, current + 1).apply()
    }

    /**
     * Get current offering (default)
     */
    val currentOffering
        get() = _offerings.value?.current
}
