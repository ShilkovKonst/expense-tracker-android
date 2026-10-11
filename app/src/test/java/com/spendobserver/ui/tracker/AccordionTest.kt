package com.spendobserver.ui.tracker

import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.domain.summary.groupByYearMonth
import com.spendobserver.ui.navigation.RecordChange
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccordionTest {

    private fun record(id: Long, date: LocalDate) = Record(
        id = id,
        trackerId = 1,
        date = date,
        type = RecordType.COST,
        description = null,
        amountCents = 1000,
    )

    private val records = listOf(
        record(1, LocalDate(2026, 9, 29)),
        record(2, LocalDate(2026, 9, 10)),
        record(3, LocalDate(2026, 8, 5)),
        record(4, LocalDate(2025, 12, 31)),
    )
    private val groups = records.groupByYearMonth()

    @Test
    fun accordionItems_allCollapsed_showsOnlyYears() {
        val items = accordionItems(groups, expandedYears = emptySet(), expandedMonths = emptySet())

        assertEquals(listOf("year-2026", "year-2025"), items.map { it.key })
    }

    @Test
    fun accordionItems_expandedYearAndMonth_showsTheirChildrenOnly() {
        val items = accordionItems(groups, expandedYears = setOf(2026), expandedMonths = setOf(2026 to 9))

        assertEquals(
            listOf("year-2026", "month-2026-9", "record-1", "record-2", "month-2026-8", "year-2025"),
            items.map { it.key },
        )
    }

    @Test
    fun accordionItems_expandedMonthInCollapsedYear_staysHidden() {
        val items = accordionItems(groups, expandedYears = emptySet(), expandedMonths = setOf(2026 to 9))

        assertEquals(listOf("year-2026", "year-2025"), items.map { it.key })
    }

    @Test
    fun resolveFocus_saved_waitsUntilRecordIsInItsMonth() {
        // Edited record 3 moved from August to September, but Room hasn't re-emitted yet.
        val change = RecordChange(recordId = 3, year = 2026, month = 9, deleted = false)

        assertNull(resolveFocus(change, groups))
    }

    @Test
    fun resolveFocus_saved_targetsTheRecordOnceArrived() {
        val change = RecordChange(recordId = 2, year = 2026, month = 9, deleted = false)

        assertEquals(FocusTarget.Item("record-2"), resolveFocus(change, groups))
    }

    @Test
    fun resolveFocus_deleted_waitsWhileRecordStillPresent() {
        val change = RecordChange(recordId = 1, year = 2026, month = 9, deleted = true)

        assertNull(resolveFocus(change, groups))
    }

    @Test
    fun resolveFocus_deleted_targetsTheMonthItWasRemovedFrom() {
        val after = records.filter { it.id != 1L }.groupByYearMonth()
        val change = RecordChange(recordId = 1, year = 2026, month = 9, deleted = true)

        assertEquals(FocusTarget.Item("month-2026-9"), resolveFocus(change, after))
    }

    @Test
    fun resolveFocus_deletedMonthsLastRecord_fallsBackToYear() {
        val after = records.filter { it.id != 3L }.groupByYearMonth()
        val change = RecordChange(recordId = 3, year = 2026, month = 8, deleted = true)

        assertEquals(FocusTarget.Item("year-2026"), resolveFocus(change, after))
    }

    @Test
    fun resolveFocus_deletedYearsLastRecord_isNone() {
        val after = records.filter { it.id != 4L }.groupByYearMonth()
        val change = RecordChange(recordId = 4, year = 2025, month = 12, deleted = true)

        assertEquals(FocusTarget.None, resolveFocus(change, after))
    }
}
