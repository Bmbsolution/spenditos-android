package com.spenditos.android.data.scan

import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.quota.QuotaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The scan picker's "You have N scans left — pick up to N" count, read from the user's `scan_credits` quota.
 * `null` = Premium, an unmetered bucket, or not known (offline): the picker shows no cap and the server's
 * 403 `QuotaExhausted` stays the source of truth.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScanCreditsModule {
    @Provides
    @Singleton
    fun scanCreditsProvider(quota: QuotaSource): ScanCreditsProvider = object : ScanCreditsProvider {
        override suspend fun remainingScanCredits(): Int? =
            quota.remainingScanCredits().takeIf { it != QuotaOverview.UNLIMITED }

        override fun spent() = quota.invalidate()
    }
}
