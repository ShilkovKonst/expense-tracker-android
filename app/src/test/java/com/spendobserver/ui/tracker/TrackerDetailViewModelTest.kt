package com.spendobserver.ui.tracker

import android.database.sqlite.SQLiteException
import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.TrackerEntity
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tracker
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.TrackerStats
import com.spendobserver.testing.FakeRecordRepository
import com.spendobserver.testing.recordWithParts
import com.spendobserver.ui.navigation.RecordChange
import com.spendobserver.ui.trackers.FakeTrackerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var trackerDao: FakeTrackerDao
    private lateinit var trackers: TrackerRepository
    private lateinit var records: FakeRecordRepository
    private var trackerId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trackerDao = FakeTrackerDao()
        trackers = TrackerRepository(trackerDao)
        records = FakeRecordRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(trackerRepository: TrackerRepository = trackers): TrackerDetailViewModel {
        // The fixtures' records belong to tracker 1: the first one the fake DAO hands out.
        trackerId = trackers.insertTracker(Tracker(title = "Дом", createdAt = 0, updatedAt = 0))
        return TrackerDetailViewModel(trackerId, trackerRepository, records, defaultDispatcher = dispatcher).also { vm ->
            // Every state is WhileSubscribed: collect them all, as the screen does.
            merge(vm.trackerTitle, vm.yearGroups, vm.summary, vm.isLoaded, vm.items, vm.focusTarget).launchIn(backgroundScope)
        }
    }

    private fun TrackerDetailViewModel.keys() = items.value.map {
        when (it) {
            is AccordionItem.Year -> yearKey(it.group.year)
            is AccordionItem.Month -> monthKey(it.group.year, it.group.month)
            is AccordionItem.RecordEntry -> recordKey(it.record.id)
        }
    }

    @Test
    fun isLoaded_waitsForTheTitleAndTheRecords() = runTest(dispatcher) {
        val vm = viewModel()

        assertFalse(vm.isLoaded.value)
        advanceUntilIdle()

        assertTrue(vm.isLoaded.value)
        assertEquals("Дом", vm.trackerTitle.value)
    }

    @Test
    fun goneTracker_neverCountsAsLoaded() = runTest(dispatcher) {
        val vm = viewModel()
        trackerDao.delete(checkNotNull(trackerDao.getById(trackerId)))
        advanceUntilIdle()

        assertFalse("an untitled tracker would show as an empty one", vm.isLoaded.value)
    }

    @Test
    fun rename_reachesTheTitle() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        trackers.updateTracker(checkNotNull(trackers.getTracker(trackerId)).copy(title = "Семья"))
        advanceUntilIdle()

        assertEquals("Семья", vm.trackerTitle.value)
    }

    @Test
    fun records_areGroupedNewestYearFirst_andSummed() = runTest(dispatcher) {
        records.add(recordWithParts(date = LocalDate(2025, 3, 1), partAmountsCents = longArrayOf(1000)))
        records.add(recordWithParts(date = LocalDate(2026, 1, 5), type = RecordType.INCOME, partAmountsCents = longArrayOf(5000)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(2026, 2025), vm.yearGroups.value.map { it.year })
        assertEquals(TrackerStats(recordCount = 2, totals = AmountTotals(incomeCents = 5000, costCents = 1000)), vm.summary.value)
    }

    @Test
    fun collapsedAtFirst_thenToggledOpenAndShut() = runTest(dispatcher) {
        val record = records.add(recordWithParts(date = LocalDate(2026, 3, 15)))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(yearKey(2026)), vm.keys())

        vm.toggleYear(2026)
        vm.toggleMonth(2026, 3)
        advanceUntilIdle()
        assertEquals(listOf(yearKey(2026), monthKey(2026, 3), recordKey(record.id)), vm.keys())

        vm.toggleYear(2026)
        advanceUntilIdle()
        assertEquals(listOf(yearKey(2026)), vm.keys())
    }

    @Test
    fun toggleRecordParts_flipsOnlyThatRecord() = runTest(dispatcher) {
        val split = records.add(recordWithParts(partAmountsCents = longArrayOf(600, 400)))
        val other = records.add(recordWithParts(partAmountsCents = longArrayOf(300, 200)))
        val vm = viewModel()
        vm.toggleYear(2026)
        vm.toggleMonth(2026, 3)

        vm.toggleRecordParts(split.id)
        advanceUntilIdle()

        val expanded = vm.items.value.filterIsInstance<AccordionItem.RecordEntry>().associate { it.record.id to it.partsExpanded }
        assertEquals(mapOf(split.id to true, other.id to false), expanded)
    }

    @Test
    fun savedRecord_opensItsMonth_andIsFocusedOnceItArrives() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        // The form reports the save before the record list has caught up.
        vm.onRecordChanged(RecordChange(recordId = 1, year = 2026, month = 3, deleted = false))
        advanceUntilIdle()
        assertNull(vm.focusTarget.value)

        records.add(recordWithParts(id = 1, date = LocalDate(2026, 3, 15)))
        advanceUntilIdle()

        assertEquals(FocusTarget.Item(recordKey(1)), vm.focusTarget.value)
        assertEquals(listOf(yearKey(2026), monthKey(2026, 3), recordKey(1)), vm.keys())
    }

    @Test
    fun onFocusHandled_clearsTheTarget_butKeepsTheMonthOpen() = runTest(dispatcher) {
        records.add(recordWithParts(id = 1, date = LocalDate(2026, 3, 15)))
        val vm = viewModel()
        vm.onRecordChanged(RecordChange(recordId = 1, year = 2026, month = 3, deleted = false))
        advanceUntilIdle()

        vm.onFocusHandled()
        advanceUntilIdle()

        assertNull(vm.focusTarget.value)
        assertEquals(listOf(yearKey(2026), monthKey(2026, 3), recordKey(1)), vm.keys())
    }

    @Test
    fun deleteTracker_deletesIt_thenCallsBack() = runTest(dispatcher) {
        val vm = viewModel()
        var deleted = false

        vm.deleteTracker { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        assertNull(trackers.getTracker(trackerId))
        assertFalse(vm.writeFailed.value)
    }

    @Test
    fun deleteTracker_thatTheDatabaseRefuses_reportsIt_andStays() = runTest(dispatcher) {
        val refusing = object : TrackerDao by trackerDao {
            override suspend fun delete(tracker: TrackerEntity) = throw SQLiteException("fake: write refused")
        }
        val vm = viewModel(TrackerRepository(refusing))
        var deleted = false

        vm.deleteTracker { deleted = true }
        advanceUntilIdle()

        assertFalse(deleted)
        assertTrue(vm.writeFailed.value)
        assertEquals("Дом", trackers.getTracker(trackerId)?.title)

        vm.clearWriteFailed()
        assertFalse(vm.writeFailed.value)
    }
}
