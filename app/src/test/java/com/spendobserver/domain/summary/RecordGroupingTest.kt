package com.spendobserver.domain.summary

import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.testing.recordWithParts
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordGroupingTest {

    private fun record(date: LocalDate, type: RecordType = RecordType.COST, amountCents: Long = 1000) =
        recordWithParts(date = date, type = type, partAmountsCents = longArrayOf(amountCents))

    @Test
    fun emptyList_producesNoGroups() {
        val groups = emptyList<RecordWithParts>().groupByYearMonth()

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
    fun yearsAreNewestFirst_monthsFromJanuary() {
        val records = listOf(
            record(LocalDate(2025, 12, 1)),
            record(LocalDate(2026, 1, 1)),
            record(LocalDate(2026, 6, 1)),
        )

        val groups = records.groupByYearMonth()

        assertEquals(listOf(2026, 2025), groups.map { it.year })
        assertEquals(listOf(1, 6), groups[0].months.map { it.month })
    }

    @Test
    fun recordsWithinAMonth_areOrderedFromStartOfMonth() {
        val records = listOf(
            record(LocalDate(2026, 3, 1)),
            record(LocalDate(2026, 3, 20)),
            record(LocalDate(2026, 3, 10)),
        )

        val groups = records.groupByYearMonth()

        val dates = groups[0].months[0].records.map { it.record.date }
        assertEquals(
            listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 10), LocalDate(2026, 3, 20)),
            dates,
        )
    }

    @Test
    fun sameDayRecords_keepEntryOrder() {
        val day = LocalDate(2026, 3, 5)
        val records = listOf(
            recordWithParts(id = 3, date = day),
            recordWithParts(id = 1, date = day),
            recordWithParts(id = 2, date = day),
        )

        val groups = records.groupByYearMonth()

        assertEquals(listOf(1L, 2L, 3L), groups[0].months[0].records.map { it.record.id })
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
