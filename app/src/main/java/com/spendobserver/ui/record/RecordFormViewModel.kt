package com.spendobserver.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.calculator.CalculatorMode
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.record.PartsEvaluation
import com.spendobserver.domain.record.PartsInput
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.common.tagNameError
import com.spendobserver.ui.navigation.RecordChange
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

/**
 * Backs the create/edit record form. [recordId] is `null` when creating a
 * new record; when set, existing field values are loaded from
 * [recordRepository] on init.
 *
 * The record's parts — amounts, the receipt total, the remainder — are a
 * [PartsInput]; this ViewModel only forwards edits to it and saves what it
 * produces, so all the arithmetic and validation stays in pure, JVM-tested
 * domain code.
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

    private val calculatorMode = CalculatorMode.LEFT_TO_RIGHT

    val isEditing: Boolean get() = recordId != null

    private val _type = MutableStateFlow(RecordType.COST)
    val type: StateFlow<RecordType> = _type.asStateFlow()

    private val _date = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _parts = MutableStateFlow(PartsInput())
    val parts: StateFlow<PartsInput> = _parts.asStateFlow()

    val evaluation: StateFlow<PartsEvaluation> = _parts
        .map { it.evaluate(calculatorMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), PartsInput().evaluate())

    private val _globalTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val globalTagIds: StateFlow<Set<Long>> = _globalTagIds.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Errors stay hidden until the first save attempt, then follow every edit. */
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val details = recordRepository.getRecord(recordId) ?: return@launch
                val record = details.record
                _type.value = record.type
                _date.value = record.date
                _description.value = record.description.orEmpty()
                _globalTagIds.value = details.tags.map { it.id }.toSet()
                _parts.value = PartsInput.fromSaved(
                    details.parts.map { p -> PartDraft(p.part.amountCents, p.tags.map { it.id }.toSet()) },
                )
            }
        }
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

    fun setPartAmount(partId: Int, expression: String) = _parts.update { it.setPartAmount(partId, expression) }

    fun togglePartTag(partId: Int, tagId: Long) = _parts.update { it.togglePartTag(partId, tagId) }

    fun split() = _parts.update { it.split() }

    fun addPart() = _parts.update { it.addPart() }

    fun removePart(partId: Int) = _parts.update { it.removePart(partId) }

    fun setReceiptTotal(expression: String) = _parts.update { it.setReceiptTotal(expression) }

    fun clearReceiptTotal() = _parts.update { it.clearReceiptTotal(calculatorMode) }

    fun toggleGlobalTag(tagId: Long) = _globalTagIds.update { if (tagId in it) it - tagId else it + tagId }

    /** Why the open create/rename tag dialog's name was refused; the dialog stays open meanwhile. */
    private val _tagNameError = MutableStateFlow<String?>(null)
    val tagNameError: StateFlow<String?> = _tagNameError.asStateFlow()

    fun clearTagNameError() {
        _tagNameError.value = null
    }

    /**
     * Creates a tag on the level it was asked for from: a global one is
     * selected on the record, a local one ([forPartId] set) on that part.
     * An empty or already-used name (see [tagNameError]) sets
     * [tagNameError] instead; [onCreated] runs only on success.
     */
    fun createTag(name: String, isGlobal: Boolean, forPartId: Int? = null, onCreated: () -> Unit = {}) {
        val normalized = name.trimInvisible()
        viewModelScope.launch {
            tagRepository.tagNameError(trackerId, normalized)?.let {
                _tagNameError.value = it
                return@launch
            }
            val id = tagRepository.insertTag(Tag(trackerId = trackerId, name = normalized, isGlobal = isGlobal))
            _tagNameError.value = null
            onCreated()
            if (isGlobal) {
                _globalTagIds.update { it + id }
            } else if (forPartId != null) {
                _parts.update { it.togglePartTag(forPartId, id) }
            }
        }
    }

    fun save(onSaved: (RecordChange) -> Unit) {
        val drafts = _parts.value.toDrafts(calculatorMode)
        if (drafts == null) {
            _showErrors.value = true
            return
        }
        viewModelScope.launch {
            val record = Record(
                id = recordId ?: 0,
                trackerId = trackerId,
                date = _date.value,
                type = _type.value,
                description = _description.value.trim().ifEmpty { null },
            )
            val savedId = if (recordId == null) {
                recordRepository.insertRecord(record, _globalTagIds.value, drafts)
            } else {
                recordRepository.updateRecord(record, _globalTagIds.value, drafts)
                recordId
            }
            onSaved(record.change(savedId, deleted = false))
        }
    }

    fun delete(onDeleted: (RecordChange) -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            val record = recordRepository.getRecord(id)?.record ?: return@launch
            recordRepository.deleteRecord(record)
            onDeleted(record.change(id, deleted = true))
        }
    }
}

private fun Record.change(id: Long, deleted: Boolean) =
    RecordChange(recordId = id, year = date.year, month = date.month.number, deleted = deleted)
