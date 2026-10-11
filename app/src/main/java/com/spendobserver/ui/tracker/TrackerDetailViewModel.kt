package com.spendobserver.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.YearGroup
import com.spendobserver.domain.summary.groupByYearMonth
import com.spendobserver.ui.navigation.RecordChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the accordion's expanded years/months here rather than in the
 * screen's `remember`: this ViewModel is scoped to the tracker-detail
 * back-stack entry, so it outlives the screen leaving composition while
 * the record form is on top — returning without changes finds the
 * accordion exactly as it was left.
 */
class TrackerDetailViewModel(
    private val trackerId: Long,
    private val trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
) : ViewModel() {

    /** Observed rather than read once: this ViewModel outlives a rename on the tracker-edit screen. */
    val trackerTitle: StateFlow<String?> = trackerRepository.observeTracker(trackerId)
        .map { it?.title }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    val yearGroups: StateFlow<List<YearGroup>> = recordRepository.observeAll(trackerId)
        .map { it.groupByYearMonth() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    private val expandedYears = MutableStateFlow(emptySet<Int>())
    private val expandedMonths = MutableStateFlow(emptySet<YearMonth>())

    /** Split records whose part lines are shown; collapsed by default to keep the list compact. */
    private val expandedRecords = MutableStateFlow(emptySet<Long>())

    val items: StateFlow<List<AccordionItem>> =
        combine(yearGroups, expandedYears, expandedMonths, expandedRecords, ::accordionItems)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /** The last save/delete from the record form, until the screen has scrolled to it. */
    private val pendingChange = MutableStateFlow<RecordChange?>(null)

    /** `null` when there's nothing to scroll to (or the data hasn't caught up yet). */
    val focusTarget: StateFlow<FocusTarget?> = combine(pendingChange, yearGroups) { change, groups ->
        change?.let { resolveFocus(it, groups) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    fun toggleYear(year: Int) {
        expandedYears.update { if (year in it) it - year else it + year }
    }

    fun toggleMonth(year: Int, month: Int) {
        val key = year to month
        expandedMonths.update { if (key in it) it - key else it + key }
    }

    fun toggleRecordParts(recordId: Long) {
        expandedRecords.update { if (recordId in it) it - recordId else it + recordId }
    }

    /** Opens the change's year and month, and queues a scroll to it. */
    fun onRecordChanged(change: RecordChange) {
        expandedYears.update { it + change.year }
        expandedMonths.update { it + (change.year to change.month) }
        pendingChange.value = change
    }

    fun onFocusHandled() {
        pendingChange.value = null
    }

    /** Deletes the tracker with all its records, then calls [onDeleted] — only once the delete has finished, since navigating away clears this ViewModel and would cancel it. */
    fun deleteTracker(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val tracker = trackerRepository.getTracker(trackerId) ?: return@launch
            trackerRepository.deleteTracker(tracker)
            onDeleted()
        }
    }
}
