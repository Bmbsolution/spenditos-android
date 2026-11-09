package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.network.serverMessage
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.entitlement.ProAccessState
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionMood
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.UserSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the backend's canonical fixtures (exported by scripts/sync-contract-fixtures.sh)
 * with the production Json instance. A failure here means the Kotlin models drifted from
 * packages/types.
 */
class ContractFixtureTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/$name.json")) { "missing fixture $name; run scripts/sync-contract-fixtures.sh" }.readText()

    private fun <T> decode(name: String, serializer: KSerializer<T>): T = SpenditosJson.decodeFromString(serializer, fixture(name))

    private fun <T> envelope(name: String, serializer: KSerializer<T>): ApiEnvelope<T> = decode(name, ApiEnvelope.serializer(serializer))

    @Test
    fun `transactions decode`() {
        val actual = decode("actualTransactionFixture", Transaction.serializer())
        assertEquals("txn-001", actual.id)
        decode("projectedTransactionFixture", Transaction.serializer())
        assertNull(decode("transactionWithoutMoodFixture", Transaction.serializer()).mood)
        assertEquals(TransactionMood.NECESSARY, envelope("createTransactionSuccessFixture", Transaction.serializer()).data!!.mood)
        envelope("updateTransactionSuccessFixture", Transaction.serializer())
    }

    @Test
    fun `transaction list pages decode`() {
        val page = envelope("listTransactionsSuccessFixture", TransactionsPage.serializer()).data!!
        assertTrue(page.transactions.isNotEmpty())
        val empty = envelope("listTransactionsEmptyFixture", TransactionsPage.serializer()).data!!
        assertTrue(empty.transactions.isEmpty())
        assertEquals(false, empty.pagination.hasMore)
    }

    @Test
    fun `bulk create response decodes including per-item errors`() {
        val result = envelope("bulkCreateTransactionsFixture", BulkCreateResult.serializer()).data!!
        assertEquals("txn-001", result.results.first().transaction!!.id)
        assertTrue(result.results.any { it.error != null })
    }

    @Test
    fun `categories and settings decode`() {
        decode("categoryFixture", Category.serializer())
        assertTrue(decode("incomeCategoryFixture", Category.serializer()).isIncome)
        decode("systemSuggestionCategoryFixture", Category.serializer())
        assertTrue(envelope("listCategoriesSuccessFixture", ListSerializer(Category.serializer())).data!!.isNotEmpty())
        decode("userSettingsFixture", UserSettings.serializer())
        decode("incompleteUserSettingsFixture", UserSettings.serializer())
        assertTrue(decode("premiumUserSettingsFixture", UserSettings.serializer()).premium!!.active)
    }

    @Test
    fun `revoked Pro reads as Free, whatever the tier and expiry still say`() {
        val access = decode("revokedPremiumAccessFixture", PremiumAccess.serializer())
        assertEquals("pro", access.tier)
        assertFalse(access.active)
        assertFalse("without premiumStatus", ProAccessState(legacyPremiumActive = access.active, premium = access).effectiveIsPro)

        val settings = decode("revokedPremiumUserSettingsFixture", UserSettings.serializer())
        val state = ProAccessState(serverStatus = settings.premiumStatus, legacyPremiumActive = settings.premium!!.active, premium = settings.premium)
        assertFalse("with premiumStatus", state.effectiveIsPro)
        assertFalse(state.hasLastingPro)
    }

    @Test
    fun `an unknown tier decodes and active decides access`() {
        val access = decode("unknownTierPremiumAccessFixture", PremiumAccess.serializer())
        assertEquals("plus_family", access.tier)
        assertTrue(access.entitlements!!.getValue("pro").active)
        assertTrue(access.entitlements!!.containsKey("family_sharing"))
        assertEquals("stripe", access.activeProvider)
        assertEquals("P1M", access.plan!!.period)
        assertEquals(3L, access.version)
        assertTrue(ProAccessState(legacyPremiumActive = access.active, premium = access).effectiveIsPro)
    }

    @Test
    fun `a pending plan change decodes and stays Pro`() {
        val access = decode("pendingChangePremiumAccessFixture", PremiumAccess.serializer())
        assertTrue(access.active)
        assertEquals("P1Y", access.pendingChange!!.period)
        assertEquals("2026-12-01T00:00:00.000Z", access.pendingChange!!.effectiveAt)
        assertEquals("crossgrade", access.pendingChange!!.kind)
        assertEquals("sandbox", access.environment)
        assertEquals(12L, access.version)
    }

    @Test
    fun `malformed premium fields never break the settings response`() {
        val base = "\"userId\":\"u\""
        val badPending = SpenditosJson.decodeFromString(UserSettings.serializer(), """{$base,"premium":{"tier":"pro","active":true,"pendingChange":"soon","entitlements":[1],"plan":3}}""")
        assertTrue(badPending.premium!!.active)
        assertNull(badPending.premium!!.pendingChange)
        assertNull(badPending.premium!!.entitlements)
        assertNull(badPending.premium!!.plan)
        assertNull(SpenditosJson.decodeFromString(UserSettings.serializer(), """{$base,"premium":"yes"}""").premium)
    }

    @Test
    fun `error envelopes yield the server message`() {
        assertEquals("Validation failed", serverMessage(fixture("validationErrorFixture")))
        assertEquals("Unauthorized", serverMessage(fixture("unauthorizedFixture")))
        assertEquals("Internal server error", serverMessage(fixture("serverErrorFixture")))
    }

    @Test
    fun `unknown fields and unknown enum values are tolerated`() {
        val raw = SpenditosJson.parseToJsonElement(fixture("actualTransactionFixture")).jsonObject.toMutableMap()
        raw["brandNewField"] = kotlinx.serialization.json.JsonPrimitive("x")
        raw["mood"] = kotlinx.serialization.json.JsonPrimitive("ecstatic")
        val decoded = SpenditosJson.decodeFromString(Transaction.serializer(), kotlinx.serialization.json.JsonObject(raw).toString())
        assertNull(decoded.mood)
    }

    @Test
    fun `manifest lists every exported fixture`() {
        val manifest = SpenditosJson.parseToJsonElement(fixture("MANIFEST")).jsonObject
        assertTrue(manifest["fixtures"]!!.jsonArray.size > 50)
    }

    @Test
    fun `plan checkup decodes with its suggestion and evidence`() {
        val data = envelope("monthlyPlanCheckupSuccessFixture", com.spenditos.android.data.model.MonthlyPlanCheckupData.serializer()).data!!
        assertEquals("2026-02", data.month)
        assertEquals(4, data.monthsWithExpenseHistory)
        val suggestion = data.suggestions.single()
        assertEquals("Mobile Carrier", suggestion.description)
        assertEquals("likely", suggestion.strength)
        assertEquals(3, suggestion.evidence.size)
    }
}
