package com.spenditos.android.data.scan

import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.data.receipt.ReceiptUploadClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Why a rate could not be had; the review only needs to know that the row cannot be converted. */
class ExchangeRateUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** How many units of [to] one unit of [from] is worth (iOS `TransactionReviewListView.exchangeRate`). */
interface ExchangeRates {
    /** Throws [ExchangeRateUnavailable] when there is no usable rate (offline, unknown code, bad answer). */
    suspend fun rate(from: String, to: String): Double
}

/**
 * Same public rate feed as the iOS review (`api.exchangerate-api.com/v4/latest/{from}`). Only the two currency codes
 * leave the device: no amount, account or user data. Uses the bare client (no auth header, no interceptors).
 */
@Singleton
class OkHttpExchangeRates @Inject constructor(
    @ReceiptUploadClient client: OkHttpClient,
    @ReceiptIoDispatcher private val io: CoroutineDispatcher,
) : ExchangeRates {
    private val client = client.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    /** The feed's address; tests point it at a local server. */
    internal var baseUrl: String = BASE_URL

    override suspend fun rate(from: String, to: String): Double {
        val source = from.trim().uppercase()
        val target = to.trim().uppercase()
        // Currency codes come from the scan: never put anything but three letters in the URL.
        if (!CODE.matches(source) || !CODE.matches(target)) throw ExchangeRateUnavailable("bad currency code")
        val request = Request.Builder().url("$baseUrl$source").build()
        return try {
            withContext(io) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ExchangeRateUnavailable("status ${response.code}")
                    val body = response.body?.string() ?: throw ExchangeRateUnavailable("empty body")
                    parseRate(body, target)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ExchangeRateUnavailable) {
            throw error
        } catch (error: IOException) {
            throw ExchangeRateUnavailable("network", error)
        } catch (error: RuntimeException) {
            throw ExchangeRateUnavailable("unreadable answer", error)
        }
    }

    companion object {
        internal const val BASE_URL = "https://api.exchangerate-api.com/v4/latest/"
        private const val TIMEOUT_SECONDS = 15L
        private val CODE = Regex("[A-Z]{3}")

        /** `{"rates": {"CAD": 1.37, ...}}` -> the target's rate, which must be a positive number. */
        internal fun parseRate(body: String, target: String): Double {
            val rates = (Json.parseToJsonElement(body) as? JsonObject)?.get("rates")?.jsonObject
                ?: throw ExchangeRateUnavailable("no rates")
            val rate = rates[target]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
            if (rate == null || !rate.isFinite() || rate <= 0) throw ExchangeRateUnavailable("no rate for $target")
            return rate
        }
    }
}
