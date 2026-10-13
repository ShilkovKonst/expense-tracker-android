package com.spendobserver.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.common.tagNameError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the tracker-edit screen: renaming the tracker and full tag CRUD
 * for it (create, rename, delete). Tag changes are stored at once; the
 * title only on an explicit save, so it can be edited and reverted.
 */
class TrackerEditViewModel(
    private val trackerId: Long,
    private val trackerRepository: TrackerRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    /** The title as stored — what revert goes back to, and what "changed" is measured against. */
    private val savedTitle = MutableStateFlow("")

    /** Whether saving would change anything; stray spaces don't count, saving trims them anyway. */
    val isTitleChanged: StateFlow<Boolean> = combine(_title, savedTitle) { title, saved ->
        title.trimInvisible() != saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), false)

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Records using each tag; a tag missing from the map is unused. */
    val tagUsage: StateFlow<Map<Long, Int>> = tagRepository.observeUsage(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyMap())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        viewModelScope.launch {
            val title = trackerRepository.getTracker(trackerId)?.title.orEmpty()
            savedTitle.value = title
            _title.value = title
        }
    }

    fun setTitle(value: String) {
        _title.value = value
        _saveError.value = null
    }

    fun revertTitle() {
        _title.value = savedTitle.value
        _saveError.value = null
    }

    /** Refuses an empty name or one another tracker already has (compared after [trimInvisible]). */
    fun saveTitle(onSaved: () -> Unit = {}) {
        val title = _title.value.trimInvisible()
        viewModelScope.launch {
            trackerRepository.titleError(title, exceptTrackerId = trackerId)?.let {
                _saveError.value = it
                return@launch
            }
            val tracker = trackerRepository.getTracker(trackerId) ?: return@launch
            catchingTitleClash {
                trackerRepository.updateTracker(tracker.copy(title = title, updatedAt = System.currentTimeMillis()))
            }.onSuccess {
                savedTitle.value = title
                _title.value = title
                onSaved()
            }
                .onFailure { _saveError.value = TITLE_TAKEN_ERROR }
        }
    }

    /** Why the open create/rename tag dialog's name was refused; the dialog stays open meanwhile. */
    private val _tagNameError = MutableStateFlow<String?>(null)
    val tagNameError: StateFlow<String?> = _tagNameError.asStateFlow()

    fun clearTagNameError() {
        _tagNameError.value = null
    }

    /** Refuses an empty or already-used name (see [tagNameError]); [onCreated] runs only on success. */
    fun createTag(name: String, isGlobal: Boolean, onCreated: () -> Unit = {}) {
        val normalized = name.trimInvisible()
        viewModelScope.launch {
            tagRepository.tagNameError(trackerId, normalized)?.let {
                _tagNameError.value = it
                return@launch
            }
            tagRepository.insertTag(Tag(trackerId = trackerId, name = normalized, isGlobal = isGlobal))
            _tagNameError.value = null
            onCreated()
        }
    }

    /** [onRenamed] runs once the name is accepted (or unchanged), so the dialog can close. */
    fun renameTag(tag: Tag, newName: String, onRenamed: () -> Unit = {}) {
        val name = newName.trimInvisible()
        if (name == tag.name) {
            onRenamed()
            return
        }
        viewModelScope.launch {
            tagRepository.tagNameError(trackerId, name, exceptTagId = tag.id)?.let {
                _tagNameError.value = it
                return@launch
            }
            tagRepository.updateTag(tag.copy(name = name))
            _tagNameError.value = null
            onRenamed()
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch { tagRepository.deleteTag(tag) }
    }
}
