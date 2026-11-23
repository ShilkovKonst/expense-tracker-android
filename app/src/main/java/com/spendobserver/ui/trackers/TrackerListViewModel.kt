package com.spendobserver.ui.trackers

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.domain.model.Tracker
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

    /**
     * How far the list is scrolled: its first visible row and that row's hidden part.
     * Plain values, not the list's own state: on a wide screen the list shows both on
     * the start screen and beside a tracker, two places that each pick up from here.
     */
    var scrollIndex = 0
        private set
    var scrollOffset = 0
        private set

    fun saveScroll(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    /** The database refused a write (a full disk, say). */
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    fun clearWriteFailed() {
        _writeFailed.value = false
    }

    private suspend fun reportingWriteFailure(write: suspend () -> Unit) {
        try {
            write()
        } catch (_: SQLiteException) {
            _writeFailed.value = true
        }
    }

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
            // A name clash stays in the dialog; any other refusal is reported over it, which stays open.
            reportingWriteFailure {
                catchingTitleClash {
                    trackerRepository.insertTracker(Tracker(title = normalized, createdAt = now, updatedAt = now))
                }.onSuccess { id ->
                    _createError.value = null
                    onCreated(id)
                }.onFailure { _createError.value = NameError.TAKEN }
            }
        }
    }

    fun deleteTracker(tracker: Tracker) {
        viewModelScope.launch {
            reportingWriteFailure { trackerRepository.deleteTracker(tracker) }
        }
    }
}
