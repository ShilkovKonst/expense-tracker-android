package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.text.trimInvisible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the tracker-edit screen: renaming the tracker and full tag CRUD
 * for it (create, rename, delete), plus moving an unused tag between the
 * global and local level.
 */
class TrackerEditViewModel(
    private val trackerId: Long,
    private val trackerRepository: TrackerRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Records using each tag; a tag missing from the map is unused. */
    val tagUsage: StateFlow<Map<Long, Int>> = tagRepository.observeUsage(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyMap())

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

    /** Refuses an empty name or one another tracker already has (compared after [trimInvisible]). */
    fun save(onSaved: () -> Unit) {
        val title = _title.value.trimInvisible()
        viewModelScope.launch {
            trackerRepository.titleError(title, exceptTrackerId = trackerId)?.let {
                _saveError.value = it
                return@launch
            }
            val tracker = trackerRepository.getTracker(trackerId) ?: return@launch
            catchingTitleClash {
                trackerRepository.updateTracker(tracker.copy(title = title, updatedAt = System.currentTimeMillis()))
            }.onSuccess { onSaved() }
                .onFailure { _saveError.value = TITLE_TAKEN_ERROR }
        }
    }

    fun createTag(name: String, isGlobal: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { tagRepository.insertTag(Tag(trackerId = trackerId, name = trimmed, isGlobal = isGlobal)) }
    }

    /** No-op for a tag in use — the repository refuses, and the screen disables the action anyway. */
    fun toggleTagLevel(tag: Tag) {
        viewModelScope.launch { tagRepository.setGlobal(tag, !tag.isGlobal) }
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
