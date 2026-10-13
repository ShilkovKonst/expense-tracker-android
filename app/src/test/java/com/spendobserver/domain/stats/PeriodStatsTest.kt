package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.testing.TestPart
import com.spendobserver.testing.taggedRecord
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodStatsTest {

    private val auchan = Tag(id = 1, trackerId = 1, name = "Ашан", isGlobal = true)
    private val food = Tag(id = 2, trackerId = 1, name = "продукты")
    private val salary = Tag(id = 3, trackerId = 1, name = "зарплата")
    private val allTags = listOf(auchan, food, salary)

    private val records = listOf(
        taggedRecord(
            id = 1,
            date = LocalDate(2026, 3, 1),
            tags = listOf(auchan),
            parts = listOf(TestPart(1000, listOf(food)), TestPart(500)),
        ),
        taggedRecord(id = 2, date = LocalDate(2026, 4, 1), parts = listOf(TestPart(200, listOf(food)))),
        taggedRecord(
            id = 3,
            date = LocalDate(2026, 3, 5),
            type = RecordType.INCOME,
            parts = listOf(TestPart(9000, listOf(salary))),
        ),
        taggedRecord(id = 4, date = LocalDate(2025, 3, 1), parts = listOf(TestPart(700))),
    )

    @Test
    fun countsBothTypes_butSumsAndTagsOnlySelectedType() {
        val stats = records.periodStats(StatsPeriod.Year(2026), RecordType.COST, allTags)

        assertEquals(2, stats.costCount)
        assertEquals(1, stats.incomeCount)
        assertEquals(1700L, stats.amountCents)
        assertEquals(listOf(1, 2, 0), stats.tags.map { it.recordCount })
    }

    @Test
    fun topsAreSplitByLevel() {
        val stats = records.periodStats(StatsPeriod.AllTime, RecordType.COST, allTags)

        assertEquals(listOf(auchan), stats.globalTops.byCount.map { it.tag })
        assertEquals(listOf(food), stats.localTops.byAmount.map { it.tag })
    }

    @Test
    fun periodNarrowsEverything() {
        val stats = records.periodStats(StatsPeriod.Month(2026, 4), RecordType.COST, allTags)

        assertEquals(1, stats.costCount)
        assertEquals(0, stats.incomeCount)
        assertEquals(200L, stats.amountCents)
        assertEquals(emptyList<TagStats>(), stats.globalTops.byCount)
    }
}
