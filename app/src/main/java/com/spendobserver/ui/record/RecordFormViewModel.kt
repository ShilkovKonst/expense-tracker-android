package com.spendobserver.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.calculator.CalculatorMode
import com.spendobserver.domain.calculator.amountCentsFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Backs the create/edit record form (README step 6). [recordId] is `null`
 * when creating a new record; when set, existing field values are loaded
 * from [recordRepository] on init.
 *
 * The calculator mode is hardcoded to [CalculatorMode.LEFT_TO_RIGHT] (the
 * web app's default) until a settings screen exists to choose it.
 */
class RecordFormViewModel(
    private val trackerId: Long,
    private val recordId: Long?,
    private val recordRepository: RecordRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    val isEditing: Boolean get() = recordId != null

    private val _amountExpression = MutableStateFlow("")
    val amountExpression: StateFlow<String> = _amountExpression.asStateFlow()

    private val _type = MutableStateFlow(RecordType.COST)
    val type: StateFlow<RecordType> = _type.asStateFlow()

    private val _date = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val record = recordRepository.getRecord(recordId) ?: return@launch
                _amountExpression.value = formatAmount(record.amountCents)
                _type.value = record.type
                _date.value = record.date
                _description.value = record.description.orEmpty()
                _selectedTagIds.value = recordRepository.getTagIds(recordId).toSet()
            }
        }
    }

    fun setAmountExpression(value: String) {
        _amountExpression.value = value
        _saveError.value = null
    }

    fun setType(value: RecordType) {
        _type.value = value
    }

    fun setDate(value: LocalDate) {
        _date.value = value
    }

    fun setDescription(value: String) {
        _description.value = value
    }

    fun toggleTag(tagId: Long) {
        _selectedTagIds.value = if (tagId in _selectedTagIds.value) {
            _selectedTagIds.value - tagId
        } else {
            _selectedTagIds.value + tagId
        }
    }

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = tagRepository.insertTag(Tag(trackerId = trackerId, name = trimmed))
            _selectedTagIds.value = _selectedTagIds.value + id
        }
    }

    fun renameTag(tag: Tag, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == tag.name) return
        viewModelScope.launch {
            tagRepository.updateTag(tag.copy(name = trimmed))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            tagRepository.deleteTag(tag)
            _selectedTagIds.value = _selectedTagIds.value - tag.id
        }
    }

    fun save(onSaved: () -> Unit) {
        val amountCents = amountCentsFrom(_amountExpression.value)
        if (amountCents == null) {
            _saveError.value = "Введите сумму больше нуля"
            return
        }
        viewModelScope.launch {
            val record = Record(
                id = recordId ?: 0,
                trackerId = trackerId,
                date = _date.value,
                type = _type.value,
                description = _description.value.trim().ifEmpty { null },
                amountCents = amountCents,
            )
            if (recordId == null) {
                recordRepository.insertRecord(record, _selectedTagIds.value)
            } else {
                recordRepository.updateRecord(record, _selectedTagIds.value)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            val record = recordRepository.getRecord(id) ?: return@launch
            recordRepository.deleteRecord(record)
            onDeleted()
        }
    }
}

private fun formatAmount(cents: Long): String {
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$whole.$fraction"
}
