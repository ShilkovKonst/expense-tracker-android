package com.spendobserver.ui.backup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.testing.plural
import com.spendobserver.testing.string
import com.spendobserver.testing.waitFor
import com.spendobserver.testing.waitForText
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The import dialogs over a real in-memory database. The system file picker
 * is another app a test can't drive, so the file's text is handed to the
 * ViewModel directly — exactly what the picker's callback does.
 */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val db get() = dbRule.db
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var viewModel: ImportViewModel

    private val homeFile = """
        {
          "format": "spendobserver", "version": 1, "exportedAt": "2026-10-13T19:05:00",
          "trackers": [{
            "title": "Дом", "createdAt": "2025-12-01T02:05:41",
            "tags": [{"name": "g20", "global": true}, {"name": "алко", "global": false}],
            "records": [{"date": "2025-10-02", "type": "cost", "tags": ["g20"], "parts": [{"amount": 661, "tags": ["алко"]}]}]
          }]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        trackerRepository = TrackerRepository(db.trackerDao())
        recordRepository = RecordRepository(db)
        viewModel = ImportViewModel(BackupRepository(db, recordRepository), trackerRepository)
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            ImportDialogs(state, viewModel::setResolution, viewModel::confirm, viewModel::dismiss)
        }
    }

    /** Parsing runs off the main thread, which Compose's idling doesn't track — so wait for the text. */
    private fun loadAndWaitFor(text: String?, expected: String) {
        composeRule.runOnIdle { viewModel.onFileLoaded(text) }
        composeRule.waitForText(expected)
    }

    /** The import itself runs in viewModelScope over Room. */
    private fun waitForState(predicate: (ImportState) -> Boolean) = composeRule.waitFor { predicate(viewModel.state.value) }

    private fun titles() = runBlocking { trackerRepository.observeTrackers().first().map { it.title } }

    @Test
    fun newTracker_previewsCounts_andImportsOnConfirm() {
        loadAndWaitFor(homeFile, "Дом")

        composeRule.onNodeWithText(plural(R.plurals.records_count, 1) + " · " + plural(R.plurals.tags_count, 2)).assertExists()
        composeRule.onNodeWithText(string(R.string.import_clash, "Дом")).assertDoesNotExist()
        composeRule.onNodeWithTag("importConfirmButton").performClick()
        waitForState { it is ImportState.Done }

        assertEquals(listOf("Дом"), titles())
    }

    @Test
    fun clash_defaultsToCopy_underAFreeName() {
        runBlocking { trackerRepository.insertTracker(Tracker(title = "дом", createdAt = 0, updatedAt = 0)) }

        loadAndWaitFor(homeFile, string(R.string.import_clash, "дом"))

        composeRule.onNodeWithText(string(R.string.import_copy_note, "Дом (2)")).assertExists()
        composeRule.onNodeWithTag("importConfirmButton").performClick()
        waitForState { it is ImportState.Done }

        assertEquals(listOf("Дом (2)", "дом"), titles().sorted())
    }

    @Test
    fun clash_replace_warnsAndSwapsTheTracker() {
        val oldId = runBlocking { trackerRepository.insertTracker(Tracker(title = "Дом", createdAt = 0, updatedAt = 0)) }

        loadAndWaitFor(homeFile, string(R.string.import_clash, "Дом"))
        composeRule.onNodeWithTag("resolveReplace-Дом").performClick()

        composeRule.onNodeWithText(string(R.string.import_replace_note, "Дом")).assertExists()
        composeRule.onNodeWithTag("importConfirmButton").performClick()
        waitForState { it is ImportState.Done }

        val trackers = runBlocking { trackerRepository.observeTrackers().first() }
        assertEquals(listOf("Дом"), trackers.map { it.title })
        assertEquals(1, runBlocking { recordRepository.observeAll(trackers.single().id).first().size })
        assertEquals(null, runBlocking { trackerRepository.getTracker(oldId) })
    }

    @Test
    fun invalidFile_showsWhereTheProblemIs() {
        loadAndWaitFor(homeFile.replace("\"type\": \"cost\"", "\"type\": \"expense\""), string(R.string.import_failed_title))

        composeRule.onNodeWithTag("importError")
            .assertExists()
        // The place and the problem, joined from their own resources.
        val where = string(R.string.import_where_record, string(R.string.import_where_tracker, "Дом"), "2025-10-02")
        composeRule.onNodeWithText(string(R.string.import_error_at, where, string(R.string.import_issue_unknown_type, "expense"))).assertExists()
        composeRule.onNodeWithText(string(R.string.action_ok)).performClick()

        composeRule.onNodeWithText(string(R.string.import_failed_title)).assertDoesNotExist()
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun unreadableFile_isReported() {
        loadAndWaitFor(null, string(R.string.import_error_unreadable))
    }

    @Test
    fun cancel_importsNothing() {
        loadAndWaitFor(homeFile, "Дом")

        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        composeRule.onNodeWithTag("importConfirmButton").assertDoesNotExist()
        assertEquals(emptyList<String>(), titles())
    }
}
