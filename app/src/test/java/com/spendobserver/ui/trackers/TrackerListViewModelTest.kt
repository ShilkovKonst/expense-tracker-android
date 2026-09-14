package com.spendobserver.ui.trackers

import com.spendobserver.data.repository.TrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        viewModel = TrackerListViewModel(TrackerRepository(dao))
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
    fun renameTracker_updatesTitle() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("Family")
        advanceUntilIdle()
        val tracker = viewModel.trackers.value.first()

        viewModel.renameTracker(tracker, "Home")
        advanceUntilIdle()

        assertEquals(listOf("Home"), viewModel.trackers.value.map { it.title })
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
}
