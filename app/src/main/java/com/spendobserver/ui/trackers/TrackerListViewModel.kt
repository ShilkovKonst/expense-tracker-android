package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerListViewModel(private val repository: TrackerRepository) : ViewModel() {

    val trackers: StateFlow<List<Tracker>> = repository.observeTrackers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    fun createTracker(title: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.insertTracker(Tracker(title = title, createdAt = now, updatedAt = now))
        }
    }

    fun renameTracker(tracker: Tracker, newTitle: String) {
        viewModelScope.launch {
            repository.updateTracker(tracker.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteTracker(tracker: Tracker) {
        viewModelScope.launch {
            repository.deleteTracker(tracker)
        }
    }
}
