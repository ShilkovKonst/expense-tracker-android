package com.spendobserver.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.YearGroup
import com.spendobserver.domain.summary.groupByYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerDetailViewModel(
    trackerId: Long,
    private val trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
) : ViewModel() {

    private val _trackerTitle = MutableStateFlow<String?>(null)
    val trackerTitle: StateFlow<String?> = _trackerTitle.asStateFlow()

    val yearGroups: StateFlow<List<YearGroup>> = recordRepository.observeAll(trackerId)
        .map { it.groupByYearMonth() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    val tagNamesByRecordId: StateFlow<Map<Long, List<String>>> = recordRepository.observeTagNamesByRecord(trackerId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyMap(),
        )

    init {
        viewModelScope.launch {
            _trackerTitle.value = trackerRepository.getTracker(trackerId)?.title
        }
    }
}
