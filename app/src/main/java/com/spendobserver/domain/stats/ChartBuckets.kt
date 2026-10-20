package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.RecordWithParts
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** What the statistics are about: every record, or only what one tag covers. */
sealed interface StatsSubject {
    data object Overview : StatsSubject
    data class OfTag(val tag: Tag) : StatsSubject
}

/**
 * One bar of the chart. [key] is the year, month number or day number,
 * depending on the period; [drillDown] is the period a tap on the bar
 * opens, or null where there's nothing to open: days — the finest level —
 * and empty bars, so a tap that misses its bar doesn't land in an empty period.
 */
data class ChartBucket(
    val key: Int,
    val drillDown: StatsPeriod?,
    val recordCount: Int,
    val amountCents: Long,
)

/**
 * The chart's bars for [period], one per year / month / day with no gaps —
 * empty buckets are zeros, so the X axis never skips.
 *
 * The year range for [StatsPeriod.AllTime] runs from the earliest to the
 * latest record of *either* type, stretched to include [today]'s year, so
 * flipping the type toggle never reshapes the axis.
 */
fun List<RecordWithParts>.chartBuckets(
    period: StatsPeriod,
    type: RecordType,
    subject: StatsSubject,
    today: LocalDate,
): List<ChartBucket> {
    val byKey = inPeriod(period)
        .filter { it.record.type == type }
        .groupBy { period.bucketKey(it.record.date) }

    val keys = when (period) {
        StatsPeriod.AllTime -> {
            val years = map { it.record.date.year } + today.year
            years.min()..years.max()
        }
        is StatsPeriod.Year -> 1..12
        is StatsPeriod.Month -> 1..daysIn(period.year, period.month)
    }

    return keys.map { key ->
        val records = byKey[key].orEmpty()
        val (count, amount) = records.measure(subject)
        ChartBucket(
            key = key,
            drillDown = if (count > 0) period.drillDown(key) else null,
            recordCount = count,
            amountCents = amount,
        )
    }
}

private fun StatsPeriod.bucketKey(date: LocalDate): Int = when (this) {
    StatsPeriod.AllTime -> date.year
    is StatsPeriod.Year -> date.month.number
    is StatsPeriod.Month -> date.day
}

private fun StatsPeriod.drillDown(key: Int): StatsPeriod? = when (this) {
    StatsPeriod.AllTime -> StatsPeriod.Year(key)
    is StatsPeriod.Year -> StatsPeriod.Month(year, key)
    is StatsPeriod.Month -> null
}

private fun daysIn(year: Int, month: Int): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day

private fun List<RecordWithParts>.measure(subject: StatsSubject): Pair<Int, Long> = when (subject) {
    StatsSubject.Overview -> size to sumOf { it.amountCents }
    is StatsSubject.OfTag -> tagStats(listOf(subject.tag)).single().let { it.recordCount to it.amountCents }
}
