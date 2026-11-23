package com.spendobserver.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendobserver.domain.model.RecordType
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.stats.ChartBucket
import com.spendobserver.domain.stats.PeriodStats
import com.spendobserver.domain.stats.StatsPeriod
import com.spendobserver.domain.stats.StatsSubject
import com.spendobserver.domain.stats.chartBuckets
import com.spendobserver.domain.stats.periodStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Everything the statistics screen draws, recomputed whenever data or a selection changes. */
data class StatsUiState(
    val period: StatsPeriod,
    val type: RecordType,
    val subject: StatsSubject,
    val stats: PeriodStats,
    val buckets: List<ChartBucket>,
    /** Types the tracker has records of at all; the toggle disables the others. */
    val availableTypes: Set<RecordType>,
)

/** The device's current date — the default "now" of [StatsViewModel] and [StatsScreen]. */
fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Everything the user picks on the statistics screen, as one value. */
data class StatsSelection(
    val period: StatsPeriod = StatsPeriod.AllTime,
    val type: RecordType = RecordType.COST,
    val tagId: Long? = null,
    val metric: ChartMetric = ChartMetric.AMOUNT,
)

/**
 * The tracker's statistics ViewModel in [owner]'s store, created there if it isn't yet.
 * The navigation host uses it to share one instance between the full-screen statistics
 * and the column beside the records, so a fold, unfold or rotation keeps the selection.
 */
fun statsViewModel(
    owner: ViewModelStoreOwner,
    trackerId: Long,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    today: () -> LocalDate = ::systemToday,
): StatsViewModel = ViewModelProvider.create(
    owner,
    viewModelFactory { initializer { StatsViewModel(trackerId, recordRepository, tagRepository, today) } },
)[statsViewModelKey(trackerId), StatsViewModel::class]

private fun statsViewModelKey(trackerId: Long) = "stats-$trackerId"

class StatsViewModel(
    trackerId: Long,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    private val today: () -> LocalDate = ::systemToday,
    /** Computes [uiState]; a test passes its own, so the work runs under its virtual clock. */
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.AllTime)
    private val type = MutableStateFlow(RecordType.COST)

    /**
     * The selected tag by id, not as a [com.spendobserver.domain.model.Tag]:
     * the tag list is re-read from Room, and an id survives a rename there.
     * Null means the overview.
     */
    private val selectedTagId = MutableStateFlow<Long?>(null)

    /** Kept apart from [uiState]: it only changes how the chart draws, not what's computed. */
    private val _metric = MutableStateFlow(ChartMetric.AMOUNT)
    val metric: StateFlow<ChartMetric> = _metric

    /** Null until Room's first emission, so the screen can tell "loading" from "no records". */
    val uiState: StateFlow<StatsUiState?> = combine(
        recordRepository.observeAll(trackerId),
        tagRepository.observeTags(trackerId),
        period,
        type,
        selectedTagId,
    ) { records, tags, period, selectedType, tagId ->
        // Every step below walks all the tracker's records: off the main thread.
        // Not flowOn() before stateIn(): the two fuse, and the state would then be
        // set on a background thread instead of the main one the UI collects on.
        withContext(defaultDispatcher) {
            val availableTypes = records.mapTo(HashSet()) { it.type }
            // A type the tracker has no records of (its toggle button is disabled)
            // gives way to the one it has — e.g. after the last income is deleted.
            val type = if (selectedType in availableTypes || availableTypes.isEmpty()) {
                selectedType
            } else {
                availableTypes.single()
            }
            // A tag deleted elsewhere falls back to the overview instead of a dangling selection.
            val subject = tags.firstOrNull { it.id == tagId }
                ?.let { StatsSubject.OfTag(it) }
                ?: StatsSubject.Overview
            StatsUiState(
                period = period,
                type = type,
                subject = subject,
                stats = records.periodStats(period, type, tags),
                buckets = records.chartBuckets(period, type, subject, today()),
                availableTypes = availableTypes,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /** What the user has picked, to carry over to another instance (see [restore]). */
    val selection: StatsSelection
        get() = StatsSelection(period.value, type.value, selectedTagId.value, _metric.value)

    fun restore(value: StatsSelection) {
        period.value = value.period
        type.value = value.type
        selectedTagId.value = value.tagId
        _metric.value = value.metric
    }

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    fun setType(value: RecordType) {
        type.value = value
    }

    fun setMetric(value: ChartMetric) {
        _metric.value = value
    }

    /** Null selects the overview. */
    fun selectTag(tagId: Long?) {
        selectedTagId.value = tagId
    }
}
