package com.spendobserver.domain.summary

import com.spendobserver.data.local.relation.RecordWithParts
import kotlinx.datetime.number

/** One month's records within a [YearGroup], newest date first. */
data class MonthGroup(
    val year: Int,
    val month: Int,
    val records: List<RecordWithParts>,
    val totals: AmountTotals,
)

/** One year's months within [List<Record>.groupByYearMonth], newest month first. */
data class YearGroup(
    val year: Int,
    val months: List<MonthGroup>,
    val totals: AmountTotals,
)

/**
 * Groups records by year and month for the tracker-detail accordion
 * (README step 5). Years and months are ordered newest-first; records
 * within a month are ordered newest-first too. Totals at every level come
 * from [amountTotals], so they can never drift out of sync with the
 * records they summarize — unlike the original web app's denormalized
 * `totalAmount` (see README "Отличия от оригинальной (IndexedDB) модели").
 */
fun List<RecordWithParts>.groupByYearMonth(): List<YearGroup> =
    groupBy { it.record.date.year }
        .map { (year, yearRecords) ->
            val months = yearRecords.groupBy { it.record.date.month.number }
                .map { (month, monthRecords) ->
                    val sorted = monthRecords.sortedByDescending { it.record.date }
                    MonthGroup(year = year, month = month, records = sorted, totals = sorted.amountTotals())
                }
                .sortedByDescending { it.month }
            YearGroup(year = year, months = months, totals = yearRecords.amountTotals())
        }
        .sortedByDescending { it.year }
