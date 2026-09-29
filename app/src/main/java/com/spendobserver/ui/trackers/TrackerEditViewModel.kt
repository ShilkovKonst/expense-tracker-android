package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the tracker-edit screen: renaming the tracker and full tag CRUD for it (create, rename, delete). */
class TrackerEditViewModel(
    private val trackerId: Long,
    private val trackerRepository: TrackerRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        viewModelScope.launch {
            _title.value = trackerRepository.getTracker(trackerId)?.title.orEmpty()
        }
    }

    fun setTitle(value: String) {
        _title.value = value
        _saveError.value = null
    }

    fun save(onSaved: () -> Unit) {
        val trimmed = _title.value.trim()
        if (trimmed.isEmpty()) {
            _saveError.value = "Введите название"
            return
        }
        viewModelScope.launch {
            val tracker = trackerRepository.getTracker(trackerId) ?: return@launch
            trackerRepository.updateTracker(tracker.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
            onSaved()
        }
    }

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { tagRepository.insertTag(Tag(trackerId = trackerId, name = trimmed)) }
    }

    fun renameTag(tag: Tag, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == tag.name) return
        viewModelScope.launch { tagRepository.updateTag(tag.copy(name = trimmed)) }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch { tagRepository.deleteTag(tag) }
    }
}
