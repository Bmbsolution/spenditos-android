package com.spenditos.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Free-tier defaults, used only when the server omits a limit (display only; the server gates). */
object QuotaDefaults {
    const val SENTENCE_ENTRIES = 30
    const val VOICE_ENTRIES = 10
    const val SCAN_CREDITS = 10
}

/** `GET /user/quota` body `data` (handler getUserQuotaHandler, packages/functions/src/user.ts:705). snake_case keys. */
@Serializable
data class UserQuotaPayload(val quota: QuotaOverview)

@Serializable
data class QuotaOverview(
    val month: String = "",
    @SerialName("is_pro") val isPro: Boolean = false,
    val usage: QuotaUsage = QuotaUsage(),
    val quotas: QuotaBreakdown = QuotaBreakdown(),
) {
    /** Sentences left this month; [UNLIMITED] when Pro or when the server does not meter sentences (old server). */
    val remainingSentenceEntries: Int
        get() {
            if (isPro) return UNLIMITED
            val status = quotas.sentenceEntries ?: return UNLIMITED
            return status.remainingUnits(QuotaDefaults.SENTENCE_ENTRIES)
        }

    val remainingVoiceEntries: Int
        get() = if (isPro) UNLIMITED else (quotas.voiceEntries ?: QuotaStatus()).remainingUnits(QuotaDefaults.VOICE_ENTRIES)

    /** Scan credits left. Falls back to the legacy `receipt_scans` bucket on a server without scan credits. */
    val remainingScanCredits: Int
        get() {
            if (isPro) return UNLIMITED
            quotas.scanCredits?.let { return it.remainingUnits(QuotaDefaults.SCAN_CREDITS) }
            return (quotas.receiptScans ?: QuotaStatus(unlimited = true)).remainingUnits(QuotaDefaults.SCAN_CREDITS)
        }

    /** Sentences used this month (0 on an old server). */
    val sentencesUsed: Int get() = usage.sentenceEntries ?: quotas.sentenceEntries?.used ?: 0

    companion object {
        /** "No limit": Pro, unmetered bucket. */
        const val UNLIMITED = Int.MAX_VALUE
    }
}

@Serializable
data class QuotaUsage(
    @SerialName("voice_entries") val voiceEntries: Int = 0,
    @SerialName("receipt_scans") val receiptScans: Int = 0,
    val imports: Int = 0,
    /** Absent on servers that predate sentence / scan-credit metering. */
    @SerialName("sentence_entries") val sentenceEntries: Int? = null,
    @SerialName("scan_credits") val scanCredits: Int? = null,
)

@Serializable
data class QuotaBreakdown(
    @SerialName("voice_entries") val voiceEntries: QuotaStatus? = null,
    @SerialName("receipt_scans") val receiptScans: QuotaStatus? = null,
    val imports: QuotaStatus? = null,
    @SerialName("sentence_entries") val sentenceEntries: QuotaStatus? = null,
    @SerialName("scan_credits") val scanCredits: QuotaStatus? = null,
)

@Serializable
data class QuotaStatus(
    val limit: Int? = null,
    val used: Int = 0,
    val remaining: Int? = null,
    val unlimited: Boolean = false,
    val source: String? = null,
) {
    /** Remaining units: the server's `remaining`, else `limit - used`, else `fallbackLimit - used`. */
    fun remainingUnits(fallbackLimit: Int): Int {
        if (unlimited) return QuotaOverview.UNLIMITED
        return remaining ?: maxOf(0, (limit ?: fallbackLimit) - used)
    }
}
