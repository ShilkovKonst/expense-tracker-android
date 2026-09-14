package com.spendobserver.domain.summary

import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTotalsTest {

    private fun record(type: RecordType, amountCents: Long) = Record(
        trackerId = 1,
        date = LocalDate(2026, 3, 15),
        type = type,
        description = null,
        amountCents = amountCents,
    )

    @Test
    fun emptyList_totalsAreZero() {
        val totals = emptyList<Record>().amountTotals()

        assertEquals(0L, totals.incomeCents)
        assertEquals(0L, totals.costCents)
        assertEquals(0L, totals.netCents)
    }

    @Test
    fun onlyIncome_netEqualsIncome() {
        val records = listOf(record(RecordType.INCOME, 1000), record(RecordType.INCOME, 500))

        val totals = records.amountTotals()

        assertEquals(1500L, totals.incomeCents)
        assertEquals(0L, totals.costCents)
        assertEquals(1500L, totals.netCents)
    }

    @Test
    fun onlyCost_netIsNegative() {
        val records = listOf(record(RecordType.COST, 1000), record(RecordType.COST, 500))

        val totals = records.amountTotals()

        assertEquals(0L, totals.incomeCents)
        assertEquals(1500L, totals.costCents)
        assertEquals(-1500L, totals.netCents)
    }

    @Test
    fun mixedIncomeAndCost_netsThemAgainstEachOther() {
        val records = listOf(
            record(RecordType.INCOME, 5000),
            record(RecordType.COST, 2000),
            record(RecordType.COST, 1000),
        )

        val totals = records.amountTotals()

        assertEquals(5000L, totals.incomeCents)
        assertEquals(3000L, totals.costCents)
        assertEquals(2000L, totals.netCents)
    }
}
