package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [statsFlow] is taken as a plain `Flow` (rather than a `RecordRepository`)
 * so this ViewModel stays testable with an in-memory [TrackerRepository]
 * fake alone — `RecordRepository` needs a real Room `AppDatabase` for its
 * transactional methods, which a pure JVM unit test can't construct.
 */
class TrackerListViewModel(
    private val trackerRepository: TrackerRepository,
    statsFlow: Flow<Map<Long, TrackerStats>>,
) : ViewModel() {

    val trackers: StateFlow<List<Tracker>> = trackerRepository.observeTrackers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /** Record count + net total per tracker, for the subtitle under each tracker's name. A tracker absent here has no records yet. */
    val statsByTracker: StateFlow<Map<Long, TrackerStats>> = statsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyMap(),
        )

    /** [onCreated] gets the new tracker's id, so the screen can open it right away. */
    fun createTracker(title: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = trackerRepository.insertTracker(Tracker(title = title, createdAt = now, updatedAt = now))
            onCreated(id)
        }
    }

    fun deleteTracker(tracker: Tracker) {
        viewModelScope.launch {
            trackerRepository.deleteTracker(tracker)
        }
    }
}
