package com.spendobserver.domain.summary

import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordGroupingTest {

    private fun record(date: LocalDate, type: RecordType = RecordType.COST, amountCents: Long = 1000) = Record(
        trackerId = 1,
        date = date,
        type = type,
        description = null,
        amountCents = amountCents,
    )

    @Test
    fun emptyList_producesNoGroups() {
        val groups = emptyList<Record>().groupByYearMonth()

        assertEquals(emptyList<YearGroup>(), groups)
    }

    @Test
    fun recordsInSameMonth_areGroupedTogether() {
        val records = listOf(
            record(LocalDate(2026, 3, 1), amountCents = 1000),
            record(LocalDate(2026, 3, 15), amountCents = 2000),
        )

        val groups = records.groupByYearMonth()

        assertEquals(1, groups.size)
        assertEquals(2026, groups[0].year)
        assertEquals(1, groups[0].months.size)
        assertEquals(3, groups[0].months[0].month)
        assertEquals(3000L, groups[0].months[0].totals.costCents)
        assertEquals(3000L, groups[0].totals.costCents)
    }

    @Test
    fun yearsAndMonths_areOrderedNewestFirst() {
        val records = listOf(
            record(LocalDate(2025, 12, 1)),
            record(LocalDate(2026, 1, 1)),
            record(LocalDate(2026, 6, 1)),
        )

        val groups = records.groupByYearMonth()

        assertEquals(listOf(2026, 2025), groups.map { it.year })
        assertEquals(listOf(6, 1), groups[0].months.map { it.month })
    }

    @Test
    fun recordsWithinAMonth_areOrderedNewestFirst() {
        val records = listOf(
            record(LocalDate(2026, 3, 1)),
            record(LocalDate(2026, 3, 20)),
            record(LocalDate(2026, 3, 10)),
        )

        val groups = records.groupByYearMonth()

        val dates = groups[0].months[0].records.map { it.date }
        assertEquals(
            listOf(LocalDate(2026, 3, 20), LocalDate(2026, 3, 10), LocalDate(2026, 3, 1)),
            dates,
        )
    }

    @Test
    fun totalsAtEveryLevel_netIncomeAgainstCost() {
        val records = listOf(
            record(LocalDate(2026, 3, 1), RecordType.INCOME, 5000),
            record(LocalDate(2026, 3, 2), RecordType.COST, 2000),
        )

        val groups = records.groupByYearMonth()

        val month = groups[0].months[0]
        assertEquals(3000L, month.totals.netCents)
        assertEquals(3000L, groups[0].totals.netCents)
    }
}
