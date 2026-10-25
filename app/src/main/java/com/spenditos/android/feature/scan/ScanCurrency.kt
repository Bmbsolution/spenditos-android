package com.spenditos.android.feature.scan

import com.spenditos.android.data.scan.ExchangeRates
import com.spenditos.android.feature.capture.CaptureReviewItem
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What preparing the amounts of a scan found: the rows that changed, and whether a rate was missing. A changed row
 * whose [CaptureReviewItem.reviewCurrency] is set is still in its own currency and cannot be saved until the user
 * fixes the amount or leaves the row out.
 */
internal data class CurrencyPreparation(
    val items: Map<String, CaptureReviewItem>,
    /** At least one currency had no usable rate (offline, unknown code). */
    val rateFailed: Boolean,
)

/**
 * Port of iOS `TransactionReviewListView.prepareCurrencyAmounts`. Rows in another currency are converted into
 * [target] at today's rate and rounded to the cent. A row whose currency has no rate keeps its own amount, labelled
 * in its own currency, and is reported by [CaptureReviewItem.reviewCurrency]: it is never saved as if it were [target].
 * Rows without a currency, in [target], or without an amount are left alone.
 */
internal suspend fun prepareCurrencyAmounts(
    items: List<CaptureReviewItem>,
    target: String,
    rates: ExchangeRates,
): CurrencyPreparation {
    val wanted = target.trim().uppercase()
    fun CaptureReviewItem.source(): String? = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != wanted }

    val fetched = mutableMapOf<String, Double>()
    var rateFailed = false
    for (source in items.mapNotNull { it.source() }.toSet()) {
        try {
            fetched[source] = rates.rate(source, wanted)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            rateFailed = true
        }
    }

    val changed = mutableMapOf<String, CaptureReviewItem>()
    for (item in items) {
        val source = item.source() ?: continue
        val amount = item.amountText.trim().replace(',', '.').toBigDecimalOrNull() ?: continue
        val rate = fetched[source]
        if (rate != null) {
            val converted = amount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP)
            changed[item.idempotencyKeyId] = item.copy(amountText = converted.stripTrailingZeros().toPlainString(), convertedTo = wanted)
        } else {
            changed[item.idempotencyKeyId] = item.copy(convertedTo = wanted, reviewCurrency = source)
        }
    }
    return CurrencyPreparation(changed, rateFailed)
}
