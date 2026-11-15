package com.spendobserver.domain.summary

/** Aggregate shown next to a tracker (list row, detail header): how many records it has and their net total. */
data class TrackerStats(val recordCount: Int, val totals: AmountTotals) {
    companion object {
        val EMPTY = TrackerStats(recordCount = 0, totals = AmountTotals(incomeCents = 0, costCents = 0))
    }
}

/** One year's record count and totals. */
fun YearGroup.stats() = TrackerStats(recordCount = months.sumOf { it.records.size }, totals = totals)

/** One month's record count and totals. */
fun MonthGroup.stats() = TrackerStats(recordCount = records.size, totals = totals)

/** The whole tracker's stats, added up from totals the groups already carry. */
fun List<YearGroup>.trackerStats() = TrackerStats(
    recordCount = sumOf { year -> year.months.sumOf { it.records.size } },
    totals = AmountTotals(incomeCents = sumOf { it.totals.incomeCents }, costCents = sumOf { it.totals.costCents }),
)
