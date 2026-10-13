package com.spendobserver.domain.stats

import com.spendobserver.data.local.relation.RecordWithParts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The stretch of time the statistics screen looks at. Each level also
 * fixes the chart's granularity one step finer: all time → years,
 * a year → its months, a month → its days.
 */
sealed interface StatsPeriod {
    data object AllTime : StatsPeriod
    data class Year(val year: Int) : StatsPeriod
    data class Month(val year: Int, val month: Int) : StatsPeriod

    operator fun contains(date: LocalDate): Boolean = when (this) {
        AllTime -> true
        is Year -> date.year == year
        is Month -> date.year == year && date.month.number == month
    }
}

fun List<RecordWithParts>.inPeriod(period: StatsPeriod): List<RecordWithParts> =
    filter { it.record.date in period }
