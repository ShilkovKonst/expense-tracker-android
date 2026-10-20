package com.spendobserver.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.backup.BackupParseResult
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.ImportedTracker
import com.spendobserver.domain.backup.copyTitle
import com.spendobserver.domain.backup.parseBackup
import com.spendobserver.domain.text.isSameNameAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One tracker of the file, as the preview shows it. */
data class ImportCandidate(
    val tracker: ImportedTracker,
    /** The existing tracker with the same title, if any — then [resolution] is required. */
    val clashesWith: String?,
    /** The name a [ClashResolution.COPY] would get. */
    val copyTitle: String?,
    val resolution: ClashResolution?,
)

sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Preview(val candidates: List<ImportCandidate>) : ImportState
    data class Failed(val failure: ImportFailure) : ImportState
    data class Done(val trackerCount: Int) : ImportState
}

/** File picked → [onFileLoaded] → preview with clash choices → [confirm] → done. */
class ImportViewModel(
    private val backupRepository: BackupRepository,
    private val trackerRepository: TrackerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun onFileLoaded(text: String?) {
        if (text == null) {
            _state.value = ImportState.Failed(ImportFailure.Unreadable)
            return
        }
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = when (val result = withContext(Dispatchers.Default) { parseBackup(text) }) {
                is BackupParseResult.Failure -> ImportState.Failed(ImportFailure.Invalid(result.error))
                is BackupParseResult.Success -> ImportState.Preview(candidates(result.trackers))
            }
        }
    }

    private suspend fun candidates(trackers: List<ImportedTracker>): List<ImportCandidate> {
        val existing = trackerRepository.observeTrackers().first().map { it.title }
        // A copy's name must also dodge the file's other trackers, which are about to exist too.
        val taken = existing + trackers.map { it.title }
        return trackers.map { tracker ->
            val clash = existing.firstOrNull { it.isSameNameAs(tracker.title) }
            ImportCandidate(
                tracker = tracker,
                clashesWith = clash,
                copyTitle = clash?.let { copyTitle(tracker.title, taken) },
                // A copy by default: the choice that can't destroy anything.
                resolution = clash?.let { ClashResolution.COPY },
            )
        }
    }

    fun setResolution(index: Int, resolution: ClashResolution) {
        _state.update { state ->
            if (state !is ImportState.Preview) return@update state
            ImportState.Preview(state.candidates.mapIndexed { i, c -> if (i == index) c.copy(resolution = resolution) else c })
        }
    }

    fun confirm() {
        val preview = _state.value as? ImportState.Preview ?: return
        _state.value = ImportState.Working
        viewModelScope.launch {
            _state.value = runCatching {
                backupRepository.import(preview.candidates.map { ImportItem(it.tracker, it.resolution) })
            }.fold(
                onSuccess = { ImportState.Done(it.size) },
                onFailure = { ImportState.Failed(ImportFailure.WriteFailed) },
            )
        }
    }

    fun dismiss() {
        _state.value = ImportState.Idle
    }
}
