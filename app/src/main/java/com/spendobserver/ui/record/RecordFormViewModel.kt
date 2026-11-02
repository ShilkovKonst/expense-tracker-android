package com.spendobserver.ui.record

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.calculator.CalculatorMode
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.record.PartsEvaluation
import com.spendobserver.domain.record.PartsInput
import com.spendobserver.domain.text.trimInvisible
import com.spendobserver.ui.common.NameError
import com.spendobserver.ui.common.tagNameError
import com.spendobserver.ui.navigation.RecordChange
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the form keeps its [FormDraft] in the [SavedStateHandle]. */
internal const val FORM_DRAFT_KEY = "recordFormDraft"

/** What the user has entered so far, kept across the system killing the app in the background. */
@Serializable
private data class FormDraft(
    val type: RecordType,
    val date: LocalDate,
    val description: String,
    val parts: PartsInput,
    val globalTagIds: Set<Long>,
)

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
 * The calculator mode is hardcoded to [CalculatorMode.ALGEBRAIC] — the
 * precedence everyone knows from school and other calculators — until a
 * settings screen exists to choose it.
 */
class RecordFormViewModel(
    private val trackerId: Long,
    private val recordId: Long?,
    private val recordRepository: RecordRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val draft = savedStateHandle.get<String>(FORM_DRAFT_KEY)?.let { Json.decodeFromString<FormDraft>(it) }

    /** How amount expressions are evaluated; the form's "= …" previews use it too. */
    val calculatorMode = CalculatorMode.ALGEBRAIC

    val isEditing: Boolean get() = recordId != null

    private val _type = MutableStateFlow(draft?.type ?: RecordType.COST)
    val type: StateFlow<RecordType> = _type.asStateFlow()

    private val _date = MutableStateFlow(draft?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _description = MutableStateFlow(draft?.description.orEmpty())
    val description: StateFlow<String> = _description.asStateFlow()

    private val _parts = MutableStateFlow(draft?.parts ?: PartsInput())
    val parts: StateFlow<PartsInput> = _parts.asStateFlow()

    val evaluation: StateFlow<PartsEvaluation> = _parts
        .map { it.evaluate(calculatorMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), PartsInput().evaluate())

    private val _globalTagIds = MutableStateFlow(draft?.globalTagIds ?: emptySet())
    val globalTagIds: StateFlow<Set<Long>> = _globalTagIds.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags(trackerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Errors stay hidden until the first save attempt, then follow every edit. */
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    /** The record as loaded, before any edits — [delete] reports where it *was* in the accordion. */
    private var loadedRecord: Record? = null

    init {
        viewModelScope.launch {
            recordId?.let { loadRecord(it) }
            // Only from here on: a draft saved before the record loaded would restore an empty form.
            combine(_type, _date, _description, _parts, _globalTagIds, ::FormDraft).collect {
                savedStateHandle[FORM_DRAFT_KEY] = Json.encodeToString(it)
            }
        }
    }

    /** A restored [draft] keeps its fields; the record is still needed for [delete]. */
    private suspend fun loadRecord(id: Long) {
        val details = recordRepository.getRecord(id) ?: return
        loadedRecord = details
        if (draft != null) return
        _type.value = details.type
        _date.value = details.date
        _description.value = details.description.orEmpty()
        _globalTagIds.value = details.tags.map { it.id }.toSet()
        _parts.value = PartsInput.fromSaved(
            details.parts.map { p -> PartDraft(p.amountCents, p.tags.map { it.id }.toSet()) },
        )
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

    fun unsplit() = _parts.update { it.unsplit() }

    fun addPart() = _parts.update { it.addPart() }

    fun removePart(partId: Int) = _parts.update { it.removePart(partId) }

    fun setReceiptTotal(expression: String) = _parts.update { it.setReceiptTotal(expression) }

    fun clearReceiptTotal() = _parts.update { it.clearReceiptTotal(calculatorMode) }

    /** A record has at most one global tag: another one replaces it, the selected one clears it. */
    fun toggleGlobalTag(tagId: Long) = _globalTagIds.update { if (tagId in it) emptySet() else setOf(tagId) }

    /** Why the open create/rename tag dialog's name was refused; the dialog stays open meanwhile. */
    private val _tagNameError = MutableStateFlow<NameError?>(null)
    val tagNameError: StateFlow<NameError?> = _tagNameError.asStateFlow()

    fun clearTagNameError() {
        _tagNameError.value = null
    }

    /** A second tap must not pass the name check while the first is still between it and the insert. */
    private var isCreatingTag = false

    /**
     * Creates a tag on the level it was asked for from: a global one is
     * selected on the record, a local one ([forPartId] set) on that part.
     * An empty or already-used name (see [tagNameError]) sets
     * [tagNameError] instead; [onCreated] runs only on success.
     */
    fun createTag(name: String, isGlobal: Boolean, forPartId: Int? = null, onCreated: () -> Unit = {}) {
        if (isCreatingTag) return
        isCreatingTag = true
        val normalized = name.trimInvisible()
        viewModelScope.launch {
            try {
                tagRepository.tagNameError(trackerId, normalized)?.let {
                    _tagNameError.value = it
                    return@launch
                }
                val id = tagRepository.insertTag(Tag(trackerId = trackerId, name = normalized, isGlobal = isGlobal))
                _tagNameError.value = null
                onCreated()
                if (isGlobal) {
                    _globalTagIds.value = setOf(id)
                } else if (forPartId != null) {
                    _parts.update { it.togglePartTag(forPartId, id) }
                }
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } finally {
                isCreatingTag = false
            }
        }
    }

    /**
     * Set by the first [save] or [delete] and never cleared: the form closes
     * after either, but its buttons take taps until the closing animation
     * ends, and a second tap would store a second record.
     */
    private var isFinishing = false

    /** The database refused a write (a full disk, say): the form stays open with everything entered. */
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    fun clearWriteFailed() {
        _writeFailed.value = false
    }

    private suspend fun finishing(write: suspend () -> Unit) {
        try {
            write()
        } catch (_: SQLiteException) {
            isFinishing = false
            _writeFailed.value = true
        }
    }

    fun save(onSaved: (RecordChange) -> Unit) {
        if (isFinishing) return
        val drafts = _parts.value.toDrafts(calculatorMode)
        if (drafts == null) {
            _showErrors.value = true
            return
        }
        isFinishing = true
        viewModelScope.launch {
            finishing {
                val record = RecordDraft(
                    id = recordId ?: 0,
                    trackerId = trackerId,
                    date = _date.value,
                    type = _type.value,
                    description = _description.value.trimInvisible().ifEmpty { null },
                )
                val savedId = if (recordId == null) {
                    recordRepository.insertRecord(record, _globalTagIds.value, drafts)
                } else {
                    recordRepository.updateRecord(record, _globalTagIds.value, drafts)
                    recordId
                }
                onSaved(recordChange(savedId, record.date, deleted = false))
            }
        }
    }

    /** Does nothing until the record has loaded — the form is empty until then anyway. */
    fun delete(onDeleted: (RecordChange) -> Unit) {
        val record = loadedRecord ?: return
        if (isFinishing) return
        isFinishing = true
        viewModelScope.launch {
            finishing {
                recordRepository.deleteRecord(record.id)
                onDeleted(recordChange(record.id, record.date, deleted = true))
            }
        }
    }
}

private fun recordChange(id: Long, date: LocalDate, deleted: Boolean) =
    RecordChange(recordId = id, year = date.year, month = date.month.number, deleted = deleted)
