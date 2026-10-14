package com.spenditos.android.testing

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.data.entitlement.ProAccess
import com.spenditos.android.data.entitlement.ProAccessState
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.data.model.QuotaBreakdown
import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.model.QuotaStatus
import com.spenditos.android.data.model.QuotaUsage
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.quota.QuotaSource
import com.spenditos.android.data.quota.SentenceMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [ProAccess] for ViewModel tests. [apply] takes a settings response like the real one, minus the session check. */
class FakeProAccess(initial: ProAccessState = ProAccessState()) : ProAccess {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<ProAccessState> = _state.asStateFlow()
    var refreshes = 0
    override fun current(): ProAccessState = _state.value
    fun set(state: ProAccessState) { _state.value = state }
    fun setPro(pro: Boolean) { _state.value = ProAccessState(serverStatus = PremiumStatus(isPremium = pro, reason = if (pro) "store" else "none")) }
    override fun apply(settings: UserSettings, binding: SessionBinding?) {
        _state.value = ProAccessState(serverStatus = settings.premiumStatus, legacyPremiumActive = settings.premium?.active == true)
    }
    override suspend fun refresh(force: Boolean) { refreshes++ }
    override fun clear() { _state.value = ProAccessState() }
}

/** [QuotaSource] for ViewModel tests; set [fetched] to what the server would return (null = unreachable). */
class FakeQuotaSource(var fetched: QuotaOverview? = null, private val access: ProAccess = FakeProAccess()) : QuotaSource {
    private val cache = MutableStateFlow<QuotaOverview?>(null)
    override val overview: StateFlow<QuotaOverview?> = cache.asStateFlow()
    var refreshes = 0
    var invalidations = 0
    override fun current(): QuotaOverview? = cache.value
    override suspend fun refresh(force: Boolean): QuotaOverview? {
        refreshes++
        cache.value = fetched
        return fetched
    }
    override fun invalidate() { invalidations++ }
    private suspend fun load(): QuotaOverview? = refresh()
    override suspend fun remainingScanCredits() = if (access.effectiveIsPro) QuotaOverview.UNLIMITED else load()?.remainingScanCredits ?: QuotaOverview.UNLIMITED
    override suspend fun remainingSentenceEntries() = if (access.effectiveIsPro) QuotaOverview.UNLIMITED else load()?.remainingSentenceEntries ?: QuotaOverview.UNLIMITED
    override suspend fun remainingVoiceEntries() = if (access.effectiveIsPro) QuotaOverview.UNLIMITED else load()?.remainingVoiceEntries ?: QuotaOverview.UNLIMITED
    override suspend fun sentenceMeter(forceRefresh: Boolean): SentenceMeter? {
        if (access.effectiveIsPro) return null
        val overview = (if (forceRefresh) refresh(true) else load()) ?: return null
        return SentenceMeter.from(overview)
    }

    companion object {
        fun overview(
            sentenceLeft: Int? = 30, sentenceUsed: Int = 0,
            scanLeft: Int? = 10, scanUsed: Int = 0,
            isPro: Boolean = false,
        ) = QuotaOverview(
            month = "2026-10",
            isPro = isPro,
            usage = QuotaUsage(sentenceEntries = sentenceLeft?.let { sentenceUsed }, scanCredits = scanLeft?.let { scanUsed }),
            quotas = QuotaBreakdown(
                voiceEntries = QuotaStatus(limit = 10, used = 0, remaining = 10),
                sentenceEntries = sentenceLeft?.let { QuotaStatus(limit = 30, used = sentenceUsed, remaining = it) },
                scanCredits = scanLeft?.let { QuotaStatus(limit = 10, used = scanUsed, remaining = it) },
            ),
        )
    }
}
