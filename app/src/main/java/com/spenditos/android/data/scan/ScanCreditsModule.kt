package com.spenditos.android.data.scan

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The injection point for the scan picker's "You have N scans left — pick up to N" message.
 *
 * This default knows nothing about quotas: `null` = unknown or unlimited, so the picker shows no cap. The quota /
 * premium model (a separate change) replaces this provider with one that reads the user's remaining scan credits
 * (`remaining = null` for Premium / unlimited, `max(0, limit - used)` otherwise), e.g. by deleting this file and
 * binding its own [ScanCreditsProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object ScanCreditsModule {
    @Provides
    @Singleton
    fun scanCreditsProvider(): ScanCreditsProvider = ScanCreditsProvider { null }
}
