package com.spendobserver.domain.summary

import com.spendobserver.domain.model.Record
import kotlinx.datetime.number

/** One month's records within a [YearGroup], from the start of the month to its end. */
data class MonthGroup(
    val year: Int,
    val month: Int,
    val records: List<Record>,
    val totals: AmountTotals,
)

/** One year's months within [List<Record>.groupByYearMonth], January first. */
data class YearGroup(
    val year: Int,
    val months: List<MonthGroup>,
    val totals: AmountTotals,
)

/**
 * Groups records by year and month for the tracker-detail accordion
 * (README step 5). Years are ordered newest-first, so the current one is
 * at hand; within a year, months and records read in calendar order,
 * same-day records in the order they were entered (by id). Totals at every level come
 * from [amountTotals], so they can never drift out of sync with the
 * records they summarize — unlike the original web app's denormalized
 * `totalAmount` (see README "Отличия от оригинальной (IndexedDB) модели").
 */
fun List<Record>.groupByYearMonth(): List<YearGroup> =
    groupBy { it.date.year }
        .map { (year, yearRecords) ->
            val months = yearRecords.groupBy { it.date.month.number }
                .map { (month, monthRecords) ->
                    val sorted = monthRecords.sortedWith(compareBy({ it.date }, { it.id }))
                    MonthGroup(year = year, month = month, records = sorted, totals = sorted.amountTotals())
                }
                .sortedBy { it.month }
            YearGroup(year = year, months = months, totals = yearRecords.amountTotals())
        }
        .sortedByDescending { it.year }
