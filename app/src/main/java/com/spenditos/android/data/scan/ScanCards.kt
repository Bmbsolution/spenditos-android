package com.spenditos.android.data.scan

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary

/** "1 transaction" / "3 transactions" (iOS `Int.counted`). */
fun Int.counted(noun: String): String =
    countedPlural(noun)?.let { Strings.plural(it, this) } ?: if (this == 1) "$this $noun" else "$this ${noun}s"

/** Plurals resource for the nouns the app counts out loud. */
fun countedPlural(noun: String): Int? = when (noun) {
    "item" -> R.plurals.count_item
    "transaction" -> R.plurals.count_transaction
    "page" -> R.plurals.count_page
    "past transaction" -> R.plurals.count_past_transaction
    "bill" -> R.plurals.count_bill
    "month" -> R.plurals.count_month
    "entry" -> R.plurals.count_entry
    "payment" -> R.plurals.count_payment
    "payment received" -> R.plurals.count_payment_received
    else -> null
}

/** What one Home card shows: a pure mapping from a [ScanSummary] so the copy and ordering are unit-testable (iOS `ScanCardState`). */
data class ScanCardState(
    val scanId: String,
    val phase: Phase,
    val title: String,
    val subtitle: String?,
) {
    enum class Phase { READING, READY }

    companion object {
        /** Most cards Home shows at once. */
        const val MAX_CARDS = 3

        /**
         * processing -> "Reading {sourceLabel}…"; ready -> "{n} transactions ready to review". Anything else (failed,
         * saved, discarded, unknown) and anything in [hidden] produces no card. Ready cards come first.
         */
        fun cards(scans: List<ScanSummary>, hidden: Set<String> = emptySet()): List<ScanCardState> {
            val ready = scans.filter { it.state == ScanStatus.READY && it.scanId !in hidden }.map(::card)
            val reading = scans.filter { it.state == ScanStatus.PROCESSING && it.scanId !in hidden }.map(::card)
            return (ready + reading).take(MAX_CARDS)
        }

        fun card(scan: ScanSummary): ScanCardState {
            val label = scan.sourceLabel.trim()
            return if (scan.state == ScanStatus.READY) {
                ScanCardState(
                    scanId = scan.scanId,
                    phase = Phase.READY,
                    title = Strings.plural(R.plurals.scan_ready_to_review, scan.itemCount),
                    subtitle = label.takeIf { it.isNotEmpty() }?.let { Strings.get(R.string.data_scan_from, it) },
                )
            } else {
                val progress = scan.progress.takeIf { it.total > 0 }?.let { Strings.get(R.string.scan_done_of_total, minOf(it.done, it.total), it.total) }
                ScanCardState(
                    scanId = scan.scanId,
                    phase = Phase.READING,
                    title = if (label.isEmpty()) Strings.get(R.string.common_reading_your_scan) else Strings.get(R.string.common_reading, label),
                    subtitle = progress,
                )
            }
        }
    }
}
