package com.spendobserver.domain.summary

/** Aggregate shown next to a tracker (list row, detail header): how many records it has and their net total. */
data class TrackerStats(val recordCount: Int, val totals: AmountTotals) {
    companion object {
        val EMPTY = TrackerStats(recordCount = 0, totals = AmountTotals(incomeCents = 0, costCents = 0))
    }
}
