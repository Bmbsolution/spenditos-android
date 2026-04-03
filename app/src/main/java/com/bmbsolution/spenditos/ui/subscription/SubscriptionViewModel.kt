package com.bmbsolution.spenditos.ui.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmbsolution.spenditos.data.billing.RevenueCatService
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for subscription/paywall screen
 */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val revenueCatService: RevenueCatService
) : ViewModel() {

    val offerings: StateFlow<Offerings?> = revenueCatService.offerings
    val isLoading: StateFlow<Boolean> = revenueCatService.isLoading
    val errorMessage: StateFlow<String?> = revenueCatService.errorMessage
    val purchaseSuccess: StateFlow<Boolean> = revenueCatService.purchaseSuccess

    init {
        loadOfferings()
    }

    /**
     * Load available subscription offerings
     */
    private fun loadOfferings() {
        revenueCatService.fetchOfferings()
    }

    /**
     * Purchase a subscription package
     */
    fun purchasePackage(activity: Activity, packageToPurchase: Package) {
        revenueCatService.purchasePackage(activity, packageToPurchase) { success ->
            if (success) {
                // Purchase successful
            }
        }
    }

    /**
     * Restore previous purchases
     */
    fun restorePurchases() {
        revenueCatService.restorePurchases { success ->
            // Handle restore result
        }
    }

    /**
     * Check if user has Pro access
     */
    fun hasProAccess(): Boolean = revenueCatService.checkProAccess()
}
