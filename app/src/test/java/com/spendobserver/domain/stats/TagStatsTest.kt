package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.testing.TestPart
import com.spendobserver.testing.taggedRecord
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagStatsTest {

    private val auchan = Tag(id = 1, trackerId = 1, name = "Ашан", isGlobal = true)
    private val food = Tag(id = 2, trackerId = 1, name = "продукты")
    private val alco = Tag(id = 3, trackerId = 1, name = "алко")
    private val unused = Tag(id = 4, trackerId = 1, name = "кино")
    private val allTags = listOf(auchan, food, alco, unused)

    private fun TagStats.pair() = recordCount to amountCents

    @Test
    fun globalTag_getsWholeRecordTotal() {
        val records = listOf(
            taggedRecord(id = 1, tags = listOf(auchan), parts = listOf(TestPart(1000), TestPart(500))),
        )

        val stats = records.tagStats(allTags)

        assertEquals(1 to 1500L, stats[0].pair())
    }

    @Test
    fun localTag_getsOnlyItsParts_andCountsEachRecordOnce() {
        val records = listOf(
            taggedRecord(
                id = 1,
                parts = listOf(TestPart(1000, listOf(food)), TestPart(300, listOf(food)), TestPart(200)),
            ),
            taggedRecord(id = 2, parts = listOf(TestPart(400, listOf(food)))),
        )

        val stats = records.tagStats(allTags)

        assertEquals(2 to 1700L, stats[1].pair())
    }

    @Test
    fun partWithTwoLocalTags_countsTowardsBoth() {
        val records = listOf(
            taggedRecord(id = 1, parts = listOf(TestPart(700, listOf(food, alco)))),
        )

        val stats = records.tagStats(allTags)

        assertEquals(1 to 700L, stats[1].pair())
        assertEquals(1 to 700L, stats[2].pair())
    }

    @Test
    fun everyRequestedTag_isReturnedInOrder_unusedWithZeros() {
        val stats = emptyList<RecordWithParts>().tagStats(allTags)

        assertEquals(allTags, stats.map { it.tag })
        assertTrue(stats.all { it.recordCount == 0 && it.amountCents == 0L })
    }

    @Test
    fun tops_skipUnusedTags_andRespectLimit() {
        val stats = listOf(
            TagStats(food, 5, 100),
            TagStats(alco, 0, 0),
            TagStats(unused, 2, 900),
            TagStats(auchan, 1, 50),
        )

        val tops = stats.tops(limit = 2)

        assertEquals(listOf(food, unused), tops.byCount.map { it.tag })
        assertEquals(listOf(unused, food), tops.byAmount.map { it.tag })
        assertFalse(tops.byCount.any { it.tag == alco })
    }

    @Test
    fun topsTies_breakBySecondaryMetricThenName() {
        val stats = listOf(
            TagStats(food, 3, 100), // ties with alco on count, loses on amount
            TagStats(alco, 3, 200),
            TagStats(unused, 1, 200), // ties with alco on amount, loses on count
            TagStats(auchan, 1, 100),
        )
        val nameTie = listOf(TagStats(food, 1, 100), TagStats(alco, 1, 100))

        assertEquals(listOf(alco, food, unused), stats.tops().byCount.map { it.tag })
        assertEquals(listOf(alco, unused, food), stats.tops().byAmount.map { it.tag })
        assertEquals(listOf(alco, food), nameTie.tops().byCount.map { it.tag })
    }

    @Test
    fun period_containsDatesAtItsLevel() {
        val date = LocalDate(2026, 10, 13)

        assertTrue(date in StatsPeriod.AllTime)
        assertTrue(date in StatsPeriod.Year(2026))
        assertFalse(date in StatsPeriod.Year(2025))
        assertTrue(date in StatsPeriod.Month(2026, 10))
        assertFalse(date in StatsPeriod.Month(2026, 9))
        assertFalse(date in StatsPeriod.Month(2025, 10))
    }
}
