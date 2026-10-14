package com.spenditos.android.data.quota

import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.entitlement.ProAccessState
import com.spenditos.android.data.entitlement.resolveEffectivePro
import com.spenditos.android.data.model.PremiumStatus
import com.spenditos.android.data.model.QuotaDefaults
import com.spenditos.android.data.model.QuotaOverview
import com.spenditos.android.data.model.UserQuotaPayload
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.feature.settings.SettingsPlanLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** Port of iOS `FreeTierQuotaTests`: decoding, effective Pro, the sentence counter, the amount parser. */
class FreeTierQuotaTest {
    private fun settings(extra: String = ""): UserSettings =
        SpenditosJson.decodeFromString(UserSettings.serializer(), """{"userId":"u1","currency":"USD","timezone":"UTC","setupCompleted":true$extra}""")

    private fun quota(body: String): QuotaOverview = SpenditosJson.decodeFromString(UserQuotaPayload.serializer(), """{"quota":$body}""").quota

    // --- premiumStatus decoding ---

    @Test
    fun `premiumStatus present`() {
        val s = settings(""","premiumStatus":{"isPremium":true,"expiresAt":"2026-11-12T00:00:00.000Z","reason":"welcome"},"welcomePremium":{"grantedAt":"2026-10-12T00:00:00.000Z","expiresAt":"2026-11-12T00:00:00.000Z","days":31}""")
        assertEquals(true, s.premiumStatus?.isPremium)
        assertEquals("welcome", s.premiumStatus?.reason)
        assertTrue(s.premiumStatus!!.isWelcome)
        assertNotNull(s.premiumStatus!!.expiresAtInstant)
        assertEquals(31, s.welcomePremium?.days)
    }

    @Test
    fun `premiumStatus absent from an old server`() {
        val s = settings()
        assertNull(s.premiumStatus)
        assertNull(s.welcomePremium)
    }

    @Test
    fun `a malformed premiumStatus does not break settings`() {
        val s = settings(""","premiumStatus":{"isPremium":"yes"},"welcomePremium":"oops"""")
        assertNull(s.premiumStatus)
        assertNull(s.welcomePremium)
        assertEquals("USD", s.currency)
    }

    @Test
    fun `unknown reason and null expiry decode`() {
        val s = settings(""","premiumStatus":{"isPremium":true,"expiresAt":null,"reason":"future"}""")
        assertEquals(true, s.premiumStatus?.isPremium)
        assertEquals("future", s.premiumStatus?.reason)
        assertFalse(s.premiumStatus!!.isWelcome)
        assertNull(s.premiumStatus!!.expiresAtInstant)
    }

    @Test
    fun `expiry parses with and without fractional seconds`() {
        assertNotNull(PremiumStatus.parseInstant("2026-11-12T12:00:00Z"))
        assertNotNull(PremiumStatus.parseInstant("2026-11-12T12:00:00.123Z"))
        assertNull(PremiumStatus.parseInstant("not a date"))
        assertNull(PremiumStatus.parseInstant(null))
    }

    // --- Effective Pro ---

    @Test
    fun `effective pro matrix`() {
        val premium = PremiumStatus(true, reason = "welcome")
        val notPremium = PremiumStatus(false, reason = "none")
        assertTrue("store only", resolveEffectivePro(true, null))
        assertTrue("server only", resolveEffectivePro(false, premium))
        assertTrue("both", resolveEffectivePro(true, premium))
        assertFalse("neither", resolveEffectivePro(false, notPremium))
        assertFalse("neither, old server", resolveEffectivePro(false, null))
    }

    @Test
    fun `an old server's raw premium flag stands in only while premiumStatus is missing`() {
        assertTrue(resolveEffectivePro(false, null, legacyPremiumActive = true))
        assertFalse("premiumStatus wins when present", resolveEffectivePro(false, PremiumStatus(false), legacyPremiumActive = true))
    }

    @Test
    fun `welcome only premium still lets the pro screen sell`() {
        val welcome = ProAccessState(serverStatus = PremiumStatus(true, "2026-11-12T12:00:00Z", "welcome"))
        assertTrue(welcome.effectiveIsPro)
        assertTrue(welcome.isWelcomeOnly)
        assertFalse(welcome.hasLastingPro)
        val grant = ProAccessState(serverStatus = PremiumStatus(true, null, "grant"))
        assertTrue(grant.hasLastingPro)
        assertFalse(grant.isWelcomeOnly)
        assertFalse("a store entitlement beats welcome", ProAccessState(storeIsPro = true, serverStatus = welcome.serverStatus).isWelcomeOnly)
    }

    @Test
    fun `welcome row text`() {
        val locale = Locale.US
        val utc = ZoneId.of("UTC")
        val welcome = ProAccessState(serverStatus = PremiumStatus(true, "2026-11-12T12:00:00Z", "welcome"))
        assertEquals("Welcome Premium · ends Nov 12, 2026", SettingsPlanLabel.welcomePremiumText(welcome, locale, utc))
        assertEquals("Welcome Premium", SettingsPlanLabel.welcomePremiumText(ProAccessState(serverStatus = PremiumStatus(true, null, "welcome")), locale, utc))
        assertNull(SettingsPlanLabel.welcomePremiumText(ProAccessState(serverStatus = PremiumStatus(true, null, "store")), locale, utc))
        assertNull(SettingsPlanLabel.welcomePremiumText(ProAccessState(serverStatus = PremiumStatus(false, null, "welcome")), locale, utc))
        assertNull(SettingsPlanLabel.welcomePremiumText(ProAccessState(), locale, utc))
    }

    // --- Quota decoding ---

    @Test
    fun `old server payload decodes and scans fall back to the legacy bucket`() {
        val q = quota("""{"month":"2026-10","is_pro":false,
            "usage":{"voice_entries":1,"receipt_scans":2,"imports":0},
            "quotas":{
              "voice_entries":{"limit":3,"used":1,"remaining":2,"unlimited":false,"source":"default"},
              "receipt_scans":{"limit":3,"used":2,"remaining":1,"unlimited":false,"source":"default"},
              "imports":{"limit":3,"used":0,"remaining":3,"unlimited":false,"source":"default"}}}""")
        assertNull(q.usage.sentenceEntries)
        assertNull(q.quotas.scanCredits)
        assertEquals(2, q.remainingVoiceEntries)
        assertEquals("old server: legacy receipt-scan bucket", 1, q.remainingScanCredits)
        assertEquals("old server does not meter sentences", QuotaOverview.UNLIMITED, q.remainingSentenceEntries)
        assertEquals(0, q.sentencesUsed)
    }

    @Test
    fun `new server payload decodes sentence and scan credit buckets`() {
        val q = quota("""{"month":"2026-10","is_pro":false,
            "usage":{"voice_entries":4,"receipt_scans":0,"imports":0,"sentence_entries":12,"scan_credits":7},
            "quotas":{
              "voice_entries":{"limit":10,"used":4,"remaining":6,"unlimited":false,"source":"freeTier"},
              "receipt_scans":{"limit":3,"used":0,"remaining":3,"unlimited":false,"source":"envDefault"},
              "imports":{"limit":null,"used":0,"remaining":null,"unlimited":true,"source":"unlimited"},
              "sentence_entries":{"limit":30,"used":12,"remaining":18,"unlimited":false,"source":"freeTier"},
              "scan_credits":{"limit":10,"used":7,"remaining":3,"unlimited":false,"source":"userOverride"}}}""")
        assertEquals(18, q.remainingSentenceEntries)
        assertEquals(6, q.remainingVoiceEntries)
        assertEquals("scan credits win over the legacy bucket", 3, q.remainingScanCredits)
        assertEquals(12, q.sentencesUsed)
    }

    @Test
    fun `pro is unlimited and a missing remaining falls back to limit minus used`() {
        val pro = quota("""{"month":"2026-10","is_pro":true,
            "usage":{"voice_entries":0,"receipt_scans":0,"imports":0,"sentence_entries":99,"scan_credits":99},
            "quotas":{"voice_entries":{"limit":10,"used":0,"remaining":10,"unlimited":false}}}""")
        assertEquals(QuotaOverview.UNLIMITED, pro.remainingSentenceEntries)
        assertEquals(QuotaOverview.UNLIMITED, pro.remainingScanCredits)

        val noRemaining = quota("""{"month":"2026-10","is_pro":false,
            "usage":{"voice_entries":0,"receipt_scans":0,"imports":0,"sentence_entries":5,"scan_credits":0},
            "quotas":{
              "voice_entries":{"limit":10,"used":0,"unlimited":false},
              "sentence_entries":{"limit":30,"used":5,"unlimited":false}}}""")
        assertEquals(25, noRemaining.remainingSentenceEntries)
        assertEquals(10, noRemaining.remainingVoiceEntries)
        assertEquals("no scan bucket at all: the default", QuotaOverview.UNLIMITED, noRemaining.remainingScanCredits)
    }

    @Test
    fun `defaults are the new free tier`() {
        assertEquals(30, QuotaDefaults.SENTENCE_ENTRIES)
        assertEquals(10, QuotaDefaults.VOICE_ENTRIES)
        assertEquals(10, QuotaDefaults.SCAN_CREDITS)
    }

    // --- Sentence counter ---

    @Test
    fun `counter is hidden until three uses`() {
        assertFalse(SentenceMeter(0, 30).showsCounter)
        assertFalse(SentenceMeter(2, 28).showsCounter)
        assertTrue(SentenceMeter(3, 27).showsCounter)
        assertEquals("27 smart entries left this month", SentenceMeter(3, 27).counterText)
        assertEquals("Smart entries are a Pro feature — 1 left this month.", SentenceMeter(29, 1).bannerText)
    }

    @Test
    fun `banner at five or fewer replaces the counter`() {
        val six = SentenceMeter(24, 6)
        assertTrue(six.showsCounter)
        assertFalse(six.showsBanner)
        val five = SentenceMeter(25, 5)
        assertFalse(five.showsCounter)
        assertTrue(five.showsBanner)
        assertEquals("Smart entries are a Pro feature — 5 left this month.", five.bannerText)
        assertTrue(SentenceMeter(30, 0).isExhausted)
    }

    @Test
    fun `an unlimited meter shows nothing`() {
        val unlimited = SentenceMeter(50, null)
        assertFalse(unlimited.showsCounter)
        assertFalse(unlimited.showsBanner)
        assertFalse(unlimited.isExhausted)
        assertNull(unlimited.counterText)
        assertNull(unlimited.bannerText)
    }

    @Test
    fun `singular counter copy`() {
        assertEquals("1 smart entry left this month", SentenceMeter.counterCopy(1))
    }

    @Test
    fun `meter from an overview`() {
        val q = quota("""{"month":"2026-10","is_pro":false,
            "usage":{"voice_entries":0,"receipt_scans":0,"imports":0,"sentence_entries":10,"scan_credits":0},
            "quotas":{"sentence_entries":{"limit":30,"used":10,"remaining":20,"unlimited":false}}}""")
        assertEquals(SentenceMeter(10, 20), SentenceMeter.from(q))
    }

    // --- Amount parser ---

    @Test
    fun `amount parser finds the first currency-like number`() {
        assertEquals("4.50", SentenceAmountParser.firstAmount("coffee 4.50 at blue bottle"))
        assertEquals("50.00", SentenceAmountParser.firstAmount("Spent 50 on groceries yesterday, 10 coffee today"))
        assertEquals("1250.00", SentenceAmountParser.firstAmount("rent $1,250.00 paid"))
        assertEquals("a currency symbol wins over a bare number", "8.00", SentenceAmountParser.firstAmount("2 coffees for $8"))
        assertEquals("12.50", SentenceAmountParser.firstAmount("lunch 12,50"))
        assertEquals("1250.75", SentenceAmountParser.firstAmount("bonus 1.250,75"))
        assertEquals("4.50", SentenceAmountParser.firstAmount("paid 4.50."))
    }

    @Test
    fun `amount parser ignores dates words and zero`() {
        assertNull(SentenceAmountParser.firstAmount("coffee with Sam"))
        assertNull(SentenceAmountParser.firstAmount("on 2026-10-13"))
        assertNull(SentenceAmountParser.firstAmount("7eleven run"))
        assertNull(SentenceAmountParser.firstAmount("free sample 0"))
        assertEquals("20.00", SentenceAmountParser.firstAmount("on 2026-10-13 paid 20"))
        assertNull(SentenceAmountParser.firstAmount(""))
    }

    @Test
    fun `manual fallback keeps the trimmed text and the parsed amount`() {
        assertEquals(SentenceManualFallback("groceries 42.10 at Costco", "42.10"), SentenceManualFallback.from("  groceries 42.10 at Costco  "))
        assertEquals(SentenceManualFallback("coffee with Sam", null), SentenceManualFallback.from("coffee with Sam"))
        assertEquals("18.00", SentenceManualFallback.from("taxi £18").amount)
    }

    @Test
    fun `only the sentence bucket or an unlabeled quota error routes a typed parse to the manual form`() {
        assertTrue(isSentenceQuota("sentenceEntries"))
        assertTrue(isSentenceQuota(null))
        assertFalse(isSentenceQuota("voiceEntries"))
        assertFalse(isSentenceQuota("scanCredits"))
    }
}
