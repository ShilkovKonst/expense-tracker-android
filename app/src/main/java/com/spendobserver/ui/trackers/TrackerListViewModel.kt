package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.TrackerStats
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.common.NameError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Why the last [createTracker] call was refused; shown in the create dialog, which stays open. */
    private val _createError = MutableStateFlow<NameError?>(null)
    val createError: StateFlow<NameError?> = _createError.asStateFlow()

    fun clearCreateError() {
        _createError.value = null
    }

    /**
     * Creates a tracker unless its name — compared after [trimInvisible] —
     * is empty or already taken; then [createError] explains why instead.
     * [onCreated] gets the new tracker's id, so the screen can open it.
     */
    fun createTracker(title: String, onCreated: (Long) -> Unit = {}) {
        val normalized = title.trimInvisible()
        viewModelScope.launch {
            trackerRepository.titleError(normalized)?.let {
                _createError.value = it
                return@launch
            }
            val now = System.currentTimeMillis()
            catchingTitleClash {
                trackerRepository.insertTracker(Tracker(title = normalized, createdAt = now, updatedAt = now))
            }.onSuccess { id ->
                _createError.value = null
                onCreated(id)
            }.onFailure { _createError.value = NameError.TAKEN }
        }
    }

    fun deleteTracker(tracker: Tracker) {
        viewModelScope.launch {
            trackerRepository.deleteTracker(tracker)
        }
    }
}
