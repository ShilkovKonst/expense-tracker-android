package com.spendobserver.ui.stats

import com.spendobserver.data.local.toEntity
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.stats.StatsPeriod
import com.spendobserver.domain.stats.StatsSubject
import com.spendobserver.testing.FakeRecordRepository
import com.spendobserver.testing.TestPart
import com.spendobserver.testing.recordWithParts
import com.spendobserver.testing.taggedRecord
import com.spendobserver.ui.trackers.FakeTagDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 11, 23)
    private lateinit var records: FakeRecordRepository
    private lateinit var tagDao: FakeTagDao
    private lateinit var tags: TagRepository
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        tagDao = FakeTagDao()
        // Deleting a tag as the app does: the tag row, then the parts it leaves behind.
        tags = TagRepository(tagDao, deleteTag = { tagDao.delete(it.toEntity()); records.deleteTag(it) })
        viewModel = StatsViewModel(TRACKER_ID, records, tags, today = { today }, defaultDispatcher = dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** [StatsViewModel.uiState] only computes while collected, as the screen does. */
    private fun TestScope.collect() {
        viewModel.uiState.onEach { }.launchIn(backgroundScope)
    }

    private fun TestScope.state(): StatsUiState {
        advanceUntilIdle()
        return checkNotNull(viewModel.uiState.value) { "no state after the first emission" }
    }

    private suspend fun tag(name: String, isGlobal: Boolean = false): Tag {
        val tag = Tag(trackerId = TRACKER_ID, name = name, isGlobal = isGlobal)
        return tag.copy(id = tags.insertTag(tag))
    }

    @Test
    fun beforeTheFirstEmission_stateIsNull_thenTheDefaults() = runTest(dispatcher) {
        records.add(recordWithParts(partAmountsCents = longArrayOf(1000)))
        collect()

        assertNull("loading must differ from no records", viewModel.uiState.value)
        val state = state()
        assertEquals(StatsPeriod.AllTime, state.period)
        assertEquals(RecordType.COST, state.type)
        assertEquals(StatsSubject.Overview, state.subject)
        assertEquals(1000L, state.stats.amountCents)
    }

    @Test
    fun emptyTracker_keepsCost_andShowsTodaysYearOnly() = runTest(dispatcher) {
        collect()

        val state = state()
        assertEquals(RecordType.COST, state.type)
        assertTrue(state.availableTypes.isEmpty())
        assertEquals(listOf(2026), state.buckets.map { it.key })
        assertEquals(0, state.buckets.single().recordCount)
    }

    @Test
    fun allTime_runsFromTheFirstRecordsYearToToday() = runTest(dispatcher) {
        records.add(recordWithParts(date = LocalDate(2024, 5, 1)))
        collect()

        assertEquals(listOf(2024, 2025, 2026), state().buckets.map { it.key })
    }

    @Test
    fun setPeriod_countsOnlyThatPeriod() = runTest(dispatcher) {
        records.add(recordWithParts(date = LocalDate(2025, 3, 1), partAmountsCents = longArrayOf(1000)))
        records.add(recordWithParts(date = LocalDate(2026, 3, 1), partAmountsCents = longArrayOf(700)))
        collect()

        viewModel.setPeriod(StatsPeriod.Year(2025))

        val state = state()
        assertEquals(StatsPeriod.Year(2025), state.period)
        assertEquals(1000L, state.stats.amountCents)
        assertEquals((1..12).toList(), state.buckets.map { it.key })
    }

    @Test
    fun setType_countsOnlyThatType() = runTest(dispatcher) {
        records.add(recordWithParts(type = RecordType.COST, partAmountsCents = longArrayOf(1000)))
        records.add(recordWithParts(type = RecordType.INCOME, partAmountsCents = longArrayOf(5000)))
        collect()

        viewModel.setType(RecordType.INCOME)

        val state = state()
        assertEquals(RecordType.INCOME, state.type)
        assertEquals(5000L, state.stats.amountCents)
        assertEquals(setOf(RecordType.COST, RecordType.INCOME), state.availableTypes)
    }

    @Test
    fun typeWithoutRecords_givesWayToTheOneThere() = runTest(dispatcher) {
        records.add(recordWithParts(type = RecordType.INCOME, partAmountsCents = longArrayOf(5000)))
        collect()

        // COST is selected by default, but its toggle is disabled: there's nothing to show.
        assertEquals(RecordType.INCOME, state().type)
    }

    @Test
    fun selectTag_drawsOnlyThatTagsShare() = runTest(dispatcher) {
        val alcohol = tag("алко")
        records.add(taggedRecord(id = 0, parts = listOf(TestPart(1000, listOf(alcohol)), TestPart(500))))
        collect()

        viewModel.selectTag(alcohol.id)

        val state = state()
        assertEquals(StatsSubject.OfTag(alcohol), state.subject)
        assertEquals(1000L, state.buckets.single { it.key == 2026 }.amountCents)
    }

    @Test
    fun selectedTagDeleted_fallsBackToTheOverview() = runTest(dispatcher) {
        val alcohol = tag("алко")
        records.add(taggedRecord(id = 0, parts = listOf(TestPart(1000, listOf(alcohol)))))
        collect()
        viewModel.selectTag(alcohol.id)
        assertEquals(StatsSubject.OfTag(alcohol), state().subject)

        tags.deleteTag(alcohol)

        assertEquals(StatsSubject.Overview, state().subject)
    }

    @Test
    fun renamedTag_staysSelected() = runTest(dispatcher) {
        val alcohol = tag("алко")
        collect()
        viewModel.selectTag(alcohol.id)
        advanceUntilIdle()

        tags.updateTag(alcohol.copy(name = "алкоголь"))

        assertEquals("алкоголь", (state().subject as StatsSubject.OfTag).tag.name)
    }

    @Test
    fun newRecord_updatesTheState() = runTest(dispatcher) {
        collect()
        assertEquals(0L, state().stats.amountCents)

        records.add(recordWithParts(partAmountsCents = longArrayOf(1234)))

        assertEquals(1234L, state().stats.amountCents)
    }

    @Test
    fun restore_takesOverTheWholeSelection() = runTest(dispatcher) {
        val alcohol = tag("алко")
        records.add(recordWithParts(type = RecordType.INCOME, date = LocalDate(2025, 3, 1)))
        records.add(recordWithParts(type = RecordType.COST, date = LocalDate(2025, 3, 1)))
        collect()
        val picked = StatsSelection(StatsPeriod.Month(2025, 3), RecordType.INCOME, alcohol.id, ChartMetric.COUNT)

        viewModel.restore(picked)

        assertEquals(picked, viewModel.selection)
        assertEquals(ChartMetric.COUNT, viewModel.metric.value)
        val state = state()
        assertEquals(StatsPeriod.Month(2025, 3), state.period)
        assertEquals(RecordType.INCOME, state.type)
        assertEquals(StatsSubject.OfTag(alcohol), state.subject)
    }

    private companion object {
        /** The tracker [recordWithParts] and [taggedRecord] put their records in. */
        const val TRACKER_ID = 1L
    }
}
