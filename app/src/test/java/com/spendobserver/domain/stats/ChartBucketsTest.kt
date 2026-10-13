package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.testing.TestPart
import com.spendobserver.testing.taggedRecord
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartBucketsTest {

    private val food = Tag(id = 1, trackerId = 1, name = "продукты")
    private val today = LocalDate(2026, 10, 13)

    private fun cost(id: Long, date: LocalDate, vararg parts: TestPart) =
        taggedRecord(id = id, date = date, parts = parts.toList())

    @Test
    fun allTime_coversFirstRecordYearThroughToday_withZeroGaps() {
        val records = listOf(
            cost(1, LocalDate(2023, 5, 1), TestPart(100)),
            cost(2, LocalDate(2025, 1, 1), TestPart(200)),
        )

        val buckets = records.chartBuckets(StatsPeriod.AllTime, RecordType.COST, StatsSubject.Overview, today)

        assertEquals(listOf(2023, 2024, 2025, 2026), buckets.map { it.key })
        assertEquals(listOf(1, 0, 1, 0), buckets.map { it.recordCount })
        assertEquals(listOf(100L, 0L, 200L, 0L), buckets.map { it.amountCents })
        assertEquals(StatsPeriod.Year(2023), buckets[0].drillDown)
    }

    @Test
    fun allTime_yearRangeIgnoresTypeToggle() {
        val records = listOf(
            taggedRecord(id = 1, date = LocalDate(2024, 1, 1), type = RecordType.INCOME, parts = listOf(TestPart(100))),
        )

        val buckets = records.chartBuckets(StatsPeriod.AllTime, RecordType.COST, StatsSubject.Overview, today)

        assertEquals(listOf(2024, 2025, 2026), buckets.map { it.key })
        assertEquals(listOf(0, 0, 0), buckets.map { it.recordCount })
    }

    @Test
    fun allTime_withNoRecords_showsOnlyCurrentYear() {
        val buckets = emptyList<RecordWithParts>()
            .chartBuckets(StatsPeriod.AllTime, RecordType.COST, StatsSubject.Overview, today)

        assertEquals(listOf(2026), buckets.map { it.key })
    }

    @Test
    fun year_hasTwelveMonths_drillingIntoMonths() {
        val records = listOf(cost(1, LocalDate(2026, 3, 10), TestPart(100)), cost(2, LocalDate(2025, 3, 10), TestPart(1)))

        val buckets = records.chartBuckets(StatsPeriod.Year(2026), RecordType.COST, StatsSubject.Overview, today)

        assertEquals((1..12).toList(), buckets.map { it.key })
        assertEquals(100L, buckets[2].amountCents)
        assertEquals(100L, buckets.sumOf { it.amountCents })
        assertEquals(StatsPeriod.Month(2026, 3), buckets[2].drillDown)
    }

    @Test
    fun month_hasOneBucketPerDay_andNoDrillDown() {
        assertEquals(29, emptyList<RecordWithParts>().chartBuckets(StatsPeriod.Month(2024, 2), RecordType.COST, StatsSubject.Overview, today).size)
        assertEquals(28, emptyList<RecordWithParts>().chartBuckets(StatsPeriod.Month(2026, 2), RecordType.COST, StatsSubject.Overview, today).size)

        val december = emptyList<RecordWithParts>()
            .chartBuckets(StatsPeriod.Month(2026, 12), RecordType.COST, StatsSubject.Overview, today)
        assertEquals(31, december.size)
        assertNull(december.last().drillDown)
    }

    @Test
    fun tagSubject_countsOnlyWhatTheTagCovers() {
        val records = listOf(
            cost(1, LocalDate(2026, 3, 1), TestPart(1000, listOf(food)), TestPart(500)),
            cost(2, LocalDate(2026, 3, 2), TestPart(300)),
        )

        val buckets = records.chartBuckets(StatsPeriod.Year(2026), RecordType.COST, StatsSubject.OfTag(food), today)

        assertEquals(1, buckets[2].recordCount)
        assertEquals(1000L, buckets[2].amountCents)
    }
}
