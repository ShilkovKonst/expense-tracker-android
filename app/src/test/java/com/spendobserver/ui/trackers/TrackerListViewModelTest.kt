package com.spendobserver.ui.trackers

import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeTrackerDao
    private lateinit var viewModel: TrackerListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeTrackerDao()
        viewModel = TrackerListViewModel(TrackerRepository(dao), emptyFlow())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun trackers_startsEmpty() = runTest(dispatcher) {
        assertTrue(viewModel.trackers.value.isEmpty())
    }

    @Test
    fun createTracker_addsItToTrackers() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("Family")
        advanceUntilIdle()

        assertEquals(listOf("Family"), viewModel.trackers.value.map { it.title })
    }

    @Test
    fun trackers_areSortedByTitle() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("Work")
        viewModel.createTracker("Family")
        advanceUntilIdle()

        assertEquals(listOf("Family", "Work"), viewModel.trackers.value.map { it.title })
    }

    @Test
    fun deleteTracker_removesIt() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("Family")
        advanceUntilIdle()
        val tracker = viewModel.trackers.value.first()

        viewModel.deleteTracker(tracker)
        advanceUntilIdle()

        assertTrue(viewModel.trackers.value.isEmpty())
    }

    @Test
    fun statsByTracker_surfacesWhatTheProvidedFlowEmits() = runTest(dispatcher) {
        val stats = mapOf(1L to TrackerStats(3, AmountTotals(5000, 2000)))
        val statsViewModel = TrackerListViewModel(TrackerRepository(dao), flowOf(stats))
        statsViewModel.statsByTracker.onEach { }.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(stats, statsViewModel.statsByTracker.value)
    }
}
