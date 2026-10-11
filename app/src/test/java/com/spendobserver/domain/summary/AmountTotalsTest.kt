package com.spendobserver.domain.summary

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.testing.recordWithParts
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTotalsTest {

    private fun record(type: RecordType, amountCents: Long) = recordWithParts(type = type, partAmountsCents = longArrayOf(amountCents))

    @Test
    fun emptyList_totalsAreZero() {
        val totals = emptyList<RecordWithParts>().amountTotals()

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

    @Test
    fun multiPartRecord_countsTheSumOfItsParts() {
        // One receipt split into groceries, alcohol, and snacks.
        val receipt = recordWithParts(type = RecordType.COST, partAmountsCents = longArrayOf(235000, 80000, 30000))

        assertEquals(345000L, receipt.amountCents)
        assertEquals(345000L, listOf(receipt).amountTotals().costCents)
    }
}
