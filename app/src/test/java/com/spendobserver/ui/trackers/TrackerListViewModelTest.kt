package com.spendobserver.ui.trackers

import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.common.NameError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun createTracker_addsItToTrackers() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("Family")
        advanceUntilIdle()

        assertEquals(listOf("Family"), viewModel.trackers.value.map { it.title })
    }

    @Test
    fun createTracker_storesTheTitleWithoutSurroundingWhitespace() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("  Family ​")
        advanceUntilIdle()

        assertEquals(listOf("Family"), viewModel.trackers.value.map { it.title })
    }

    @Test
    fun createTracker_titleMatchingAnExistingOne_isRefusedWithAnError() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)
        viewModel.createTracker("Семья")
        advanceUntilIdle()

        // The same title, another case, and invisible edges all count as "Семья" (see TrimInvisibleTest).
        for (title in listOf("Семья", "СЕМЬЯ", "﻿ Семья​")) {
            var createdId: Long? = null
            viewModel.createTracker(title) { createdId = it }
            advanceUntilIdle()

            assertEquals(title, 1, viewModel.trackers.value.size)
            assertEquals(title, NameError.TAKEN, viewModel.createError.value)
            assertNull(title, createdId)
            viewModel.clearCreateError()
        }
    }

    @Test
    fun createTracker_onlyInvisibleCharacters_isRefusedAsEmpty() = runTest(dispatcher) {
        viewModel.trackers.onEach { }.launchIn(backgroundScope)

        viewModel.createTracker("​ ")
        advanceUntilIdle()

        assertTrue(viewModel.trackers.value.isEmpty())
        assertEquals(NameError.EMPTY, viewModel.createError.value)
    }

    @Test
    fun clearCreateError_resetsIt() = runTest(dispatcher) {
        viewModel.createTracker("")
        advanceUntilIdle()

        viewModel.clearCreateError()

        assertNull(viewModel.createError.value)
    }

}
