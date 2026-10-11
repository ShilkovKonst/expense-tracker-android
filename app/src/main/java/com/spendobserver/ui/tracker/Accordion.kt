package com.spendobserver.ui.tracker

import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.domain.summary.MonthGroup
import com.spendobserver.domain.summary.YearGroup
import com.spendobserver.ui.navigation.RecordChange

/** A month's identity within the accordion: (year, month 1..12). */
typealias YearMonth = Pair<Int, Int>

/**
 * One visible row of the tracker-detail accordion. The nested
 * year → month → record structure is flattened into a single list so the
 * [androidx.compose.foundation.lazy.LazyColumn] renders it with one
 * `items(...)` call, and so "scroll to X" is just an index lookup by [key].
 */
sealed interface AccordionItem {
    val key: String

    data class Year(val group: YearGroup, val expanded: Boolean) : AccordionItem {
        override val key get() = yearKey(group.year)
    }

    data class Month(val group: MonthGroup, val expanded: Boolean) : AccordionItem {
        override val key get() = monthKey(group.year, group.month)
    }

    /** [partsExpanded] only matters for a split record: whether its part lines are shown. */
    data class RecordEntry(val record: RecordWithParts, val partsExpanded: Boolean = false) : AccordionItem {
        override val key get() = recordKey(record.record.id)
    }
}

fun yearKey(year: Int) = "year-$year"
fun monthKey(year: Int, month: Int) = "month-$year-$month"
fun recordKey(recordId: Long) = "record-$recordId"

/** Only children of expanded nodes are included. */
fun accordionItems(
    yearGroups: List<YearGroup>,
    expandedYears: Set<Int>,
    expandedMonths: Set<YearMonth>,
    expandedRecords: Set<Long> = emptySet(),
): List<AccordionItem> = buildList {
    for (yearGroup in yearGroups) {
        val yearExpanded = yearGroup.year in expandedYears
        add(AccordionItem.Year(yearGroup, yearExpanded))
        if (!yearExpanded) continue
        for (monthGroup in yearGroup.months) {
            val monthExpanded = (monthGroup.year to monthGroup.month) in expandedMonths
            add(AccordionItem.Month(monthGroup, monthExpanded))
            if (monthExpanded) {
                monthGroup.records.forEach { add(AccordionItem.RecordEntry(it, it.record.id in expandedRecords)) }
            }
        }
    }
}

/**
 * Where to scroll after [change], or `null` while [yearGroups] doesn't
 * reflect it yet — Room re-emits asynchronously, so right after returning
 * from the form the list can still be the pre-change one.
 *
 * - Saved: the record's row, once it shows up in its (possibly new) month.
 * - Deleted: once the record is gone, its month's header; if that was the
 *   month's last record, the year's header; if the year is gone too,
 *   [FocusTarget.None].
 */
fun resolveFocus(change: RecordChange, yearGroups: List<YearGroup>): FocusTarget? {
    val year = yearGroups.find { it.year == change.year }
    val month = year?.months?.find { it.month == change.month }
    return if (change.deleted) {
        val stillPresent = yearGroups.any { yg -> yg.months.any { mg -> mg.records.any { it.record.id == change.recordId } } }
        when {
            stillPresent -> null
            month != null -> FocusTarget.Item(monthKey(change.year, change.month))
            year != null -> FocusTarget.Item(yearKey(change.year))
            else -> FocusTarget.None
        }
    } else {
        val arrived = month?.records?.any { it.record.id == change.recordId } == true
        if (arrived) FocusTarget.Item(recordKey(change.recordId)) else null
    }
}

sealed interface FocusTarget {
    data class Item(val key: String) : FocusTarget

    /** Nothing left to scroll to — the change is handled. */
    data object None : FocusTarget
}
