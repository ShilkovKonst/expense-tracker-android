package com.spendobserver.ui.backup

import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.backup.BACKUP_FORMAT
import com.spendobserver.domain.backup.BACKUP_VERSION
import com.spendobserver.domain.backup.BackupError
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.model.Tracker
import com.spendobserver.testing.FakeBackupRepository
import com.spendobserver.ui.trackers.FakeTrackerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var trackers: TrackerRepository
    private lateinit var backup: FakeBackupRepository
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trackers = TrackerRepository(FakeTrackerDao())
        backup = FakeBackupRepository()
        viewModel = ImportViewModel(backup, trackers, defaultDispatcher = dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A valid backup holding one empty tracker per title. */
    private fun file(vararg titles: String) = """
        {
          "format": "$BACKUP_FORMAT",
          "version": $BACKUP_VERSION,
          "exportedAt": "2026-10-13T19:05:00",
          "trackers": [${titles.joinToString { """{"title": "$it", "createdAt": "2025-12-01T02:05:41", "tags": [], "records": []}""" }}]
        }
    """.trimIndent()

    private suspend fun existing(vararg titles: String) {
        titles.forEach { trackers.insertTracker(Tracker(title = it, createdAt = 0, updatedAt = 0)) }
    }

    private fun TestScope.loaded(text: String?): ImportState {
        viewModel.onFileLoaded(text)
        advanceUntilIdle()
        return viewModel.state.value
    }

    private fun preview() = viewModel.state.value as ImportState.Preview

    @Test
    fun fileThatCouldNotBeRead_failsAsUnreadable() = runTest(dispatcher) {
        assertEquals(ImportState.Failed(ImportFailure.Unreadable), loaded(null))
    }

    @Test
    fun damagedFile_failsAsInvalid() = runTest(dispatcher) {
        assertEquals(ImportState.Failed(ImportFailure.Invalid(BackupError.Damaged)), loaded("{ not json"))
    }

    @Test
    fun parsing_showsWorkingUntilItEnds() = runTest(dispatcher) {
        viewModel.onFileLoaded(file("Дом"))

        // Only the test's dispatcher runs the parse, so it hasn't happened yet.
        assertEquals(ImportState.Working, viewModel.state.value)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is ImportState.Preview)
    }

    @Test
    fun newTitles_arePreviewedWithoutClashOrResolution() = runTest(dispatcher) {
        existing("Работа")

        val candidates = (loaded(file("Дом", "Отпуск")) as ImportState.Preview).candidates

        assertEquals(listOf("Дом", "Отпуск"), candidates.map { it.tracker.title })
        assertTrue(candidates.all { it.clashesWith == null && it.copyTitle == null && it.resolution == null })
    }

    @Test
    fun takenTitle_defaultsToCopy_underANameFreeOfTheFileToo() = runTest(dispatcher) {
        existing("дом")

        // "Дом (2)" comes in with the same file, so the copy has to skip it as well.
        val (clashing, other) = (loaded(file("Дом", "Дом (2)")) as ImportState.Preview).candidates

        assertEquals("дом", clashing.clashesWith)
        assertEquals("Дом (3)", clashing.copyTitle)
        assertEquals(ClashResolution.COPY, clashing.resolution)
        assertEquals(null, other.resolution)
    }

    @Test
    fun setResolution_changesOnlyThatCandidate() = runTest(dispatcher) {
        existing("Дом", "Отпуск")
        loaded(file("Дом", "Отпуск"))

        viewModel.setResolution(1, ClashResolution.REPLACE)

        assertEquals(listOf(ClashResolution.COPY, ClashResolution.REPLACE), preview().candidates.map { it.resolution })
    }

    @Test
    fun confirm_importsEachTrackerWithItsResolution_andReportsTheCount() = runTest(dispatcher) {
        existing("Дом")
        loaded(file("Дом", "Отпуск"))
        viewModel.setResolution(0, ClashResolution.REPLACE)
        val candidates = preview().candidates

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(
            listOf(listOf(ImportItem(candidates[0].tracker, ClashResolution.REPLACE), ImportItem(candidates[1].tracker, null))),
            backup.imports,
        )
        assertEquals(ImportState.Done(2), viewModel.state.value)
    }

    @Test
    fun confirm_thatTheDatabaseRefuses_failsAsWriteFailed() = runTest(dispatcher) {
        loaded(file("Дом"))
        backup.failImport = true

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(ImportState.Failed(ImportFailure.WriteFailed), viewModel.state.value)
        assertTrue(backup.imports.isEmpty())
    }

    @Test
    fun confirm_withoutAPreview_importsNothing() = runTest(dispatcher) {
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(ImportState.Idle, viewModel.state.value)
        assertTrue(backup.imports.isEmpty())
    }

    @Test
    fun dismiss_returnsToIdle() = runTest(dispatcher) {
        loaded(file("Дом"))

        viewModel.dismiss()

        assertEquals(ImportState.Idle, viewModel.state.value)
    }
}
