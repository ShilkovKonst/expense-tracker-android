package com.spendobserver.ui.trackers

import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.common.NameError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var trackerDao: FakeTrackerDao
    private lateinit var tagDao: FakeTagDao
    private var trackerId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trackerDao = FakeTrackerDao()
        tagDao = FakeTagDao()
        runBlocking {
            trackerId = trackerDao.insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
            trackerDao.insert(Tracker(title = "Work", createdAt = 0, updatedAt = 0))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A loaded ViewModel, with its WhileSubscribed state kept subscribed for the test's duration. */
    private fun TestScope.loadedViewModel(): TrackerEditViewModel {
        val viewModel = TrackerEditViewModel(trackerId, TrackerRepository(trackerDao), TagRepository(tagDao))
        viewModel.isTitleChanged.onEach { }.launchIn(backgroundScope)
        viewModel.tags.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun storedTitle() = trackerDao.getById(trackerId)!!.title

    // --- Title ---

    @Test
    fun title_isLoaded_andUnchangedUntilEdited() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        assertEquals("Family", viewModel.title.value)
        assertFalse(viewModel.isTitleChanged.value)

        viewModel.setTitle("Family 2")
        advanceUntilIdle()
        assertTrue(viewModel.isTitleChanged.value)
    }

    @Test
    fun strayWhitespace_doesNotCountAsAChange() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.setTitle("  Family ")
        advanceUntilIdle()

        assertFalse(viewModel.isTitleChanged.value)
    }

    @Test
    fun revertTitle_restoresTheStoredTitle_andClearsTheError() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.setTitle("")
        viewModel.saveTitle()
        advanceUntilIdle()

        viewModel.revertTitle()
        advanceUntilIdle()

        assertEquals("Family", viewModel.title.value)
        assertNull(viewModel.saveError.value)
        assertFalse(viewModel.isTitleChanged.value)
    }

    @Test
    fun saveTitle_storesItTrimmed_andCallsOnSaved() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        var saved = false

        viewModel.setTitle("  Home ")
        viewModel.saveTitle { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals("Home", storedTitle())
        assertEquals("Home", viewModel.title.value)
        assertFalse(viewModel.isTitleChanged.value)
    }

    @Test
    fun saveTitle_refusesEmptyOrAnotherTrackersTitle() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        for ((title, error) in listOf("​ " to NameError.EMPTY, "work" to NameError.TAKEN)) {
            var saved = false
            viewModel.setTitle(title)
            viewModel.saveTitle { saved = true }
            advanceUntilIdle()

            assertEquals(title, error, viewModel.saveError.value)
            assertFalse(title, saved)
            assertEquals(title, "Family", storedTitle())
        }
    }

    @Test
    fun saveTitle_ownTitleInAnotherCase_isNotTaken() = runTest(dispatcher) {
        val viewModel = loadedViewModel()

        viewModel.setTitle("FAMILY")
        viewModel.saveTitle()
        advanceUntilIdle()

        assertNull(viewModel.saveError.value)
        assertEquals("FAMILY", storedTitle())
    }

    // --- Tags ---

    @Test
    fun createTag_storesItTrimmed_onTheRequestedLevel() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        var created = 0

        viewModel.createTag(" Ашан ", isGlobal = true) { created++ }
        viewModel.createTag("продукты", isGlobal = false) { created++ }
        advanceUntilIdle()

        assertEquals(2, created)
        assertEquals(listOf("Ашан" to true, "продукты" to false), viewModel.tags.value.map { it.name to it.isGlobal })
    }

    @Test
    fun createTag_nameTakenOnTheOtherLevel_orEmpty_isRefused() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        viewModel.createTag("Ашан", isGlobal = true)
        advanceUntilIdle()

        for ((name, error) in listOf("ашан" to NameError.TAKEN, " " to NameError.EMPTY)) {
            var created = false
            viewModel.createTag(name, isGlobal = false) { created = true }
            advanceUntilIdle()

            assertEquals(name, error, viewModel.tagNameError.value)
            assertFalse(name, created)
            viewModel.clearTagNameError()
        }
        assertEquals(listOf("Ашан"), viewModel.tags.value.map { it.name })
    }

    @Test
    fun renameTag_unchangedName_closesWithoutWriting() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val tag = tagOf("кино")
        var renamed = false

        viewModel.renameTag(tag, " кино ") { renamed = true }
        advanceUntilIdle()

        assertTrue(renamed)
        assertEquals(0, tagDao.updateCount)
    }

    @Test
    fun renameTag_toItsOwnNameInAnotherCase_isAllowed_toAnotherTagsName_isNot() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val cinema = tagOf("кино")
        tagOf("алко")

        viewModel.renameTag(cinema, "Кино")
        advanceUntilIdle()
        assertNull(viewModel.tagNameError.value)

        var renamed = false
        viewModel.renameTag(cinema.copy(name = "Кино"), "АЛКО") { renamed = true }
        advanceUntilIdle()

        assertEquals(NameError.TAKEN, viewModel.tagNameError.value)
        assertFalse(renamed)
        assertEquals(listOf("алко", "Кино"), viewModel.tags.value.map { it.name }.sortedBy { it.lowercase() })
    }

    @Test
    fun deleteTag_removesIt() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val tag = tagOf("кино")

        viewModel.deleteTag(tag)
        advanceUntilIdle()

        assertTrue(viewModel.tags.value.isEmpty())
    }

    private suspend fun tagOf(name: String): Tag {
        val id = tagDao.insert(Tag(trackerId = trackerId, name = name))
        return tagDao.getForTracker(trackerId).single { it.id == id }
    }
}
