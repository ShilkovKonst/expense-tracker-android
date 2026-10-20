package com.spendobserver.ui.trackers

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.plural
import com.spendobserver.testing.signedAmount
import com.spendobserver.testing.string
import com.spendobserver.testing.waitForNoText
import com.spendobserver.testing.waitForTag
import com.spendobserver.testing.waitForText
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerListScreenTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val db get() = dbRule.db
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var recordRepository: RecordRepository

    @Before
    fun setUp() {
        trackerRepository = TrackerRepository(db.trackerDao())
        recordRepository = RecordRepository(db)
    }

    private fun setContent(
        onEditTrackerClick: (Tracker) -> Unit = {},
        onHelpClick: () -> Unit = {},
        onFaqClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            TrackerListScreen(
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onEditTrackerClick = onEditTrackerClick,
                onHelpClick = onHelpClick,
                onFaqClick = onFaqClick,
            )
        }
    }

    private fun createTracker(title: String) {
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_create)).performClick()
        composeRule.waitForTag("editTrackerButton-$title")
    }

    @Test
    fun emptyState_showsPlaceholder() {
        setContent()

        composeRule.onNodeWithText(string(R.string.tracker_list_empty)).assertExists()
    }

    @Test
    fun createTracker_appearsInListAndClearsEmptyState() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithText("Family").assertExists()
        composeRule.onNodeWithText(string(R.string.tracker_list_empty)).assertDoesNotExist()
    }

    @Test
    fun createButton_disabledForBlankTitle() {
        setContent()
        composeRule.onNodeWithText("+").performClick()

        composeRule.onNodeWithText(string(R.string.action_create)).assertIsNotEnabled()
    }

    @Test
    fun newTracker_showsZeroRecordsAndZeroTotal() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithText(plural(R.plurals.records_count, 0) + " · " + signedAmount(0)).assertExists()
    }

    @Test
    fun trackerWithRecords_showsCountAndNetTotal() {
        setContent()
        createTracker("Family")
        val trackerId = runBlocking { db.trackerDao().getByTitle("Family")!!.id }
        runBlocking {
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 1), type = RecordType.INCOME, description = null),
                emptySet(),
                listOf(PartDraft(5000, emptySet())),
            )
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 2), type = RecordType.COST, description = null),
                emptySet(),
                listOf(PartDraft(2000, emptySet())),
            )
        }
        composeRule.waitForText(plural(R.plurals.records_count, 2) + " · " + signedAmount(3000))
    }

    @Test
    fun editButton_invokesOnEditTrackerClickWithTheTracker() {
        var clicked: Tracker? = null
        setContent(onEditTrackerClick = { clicked = it })
        createTracker("Family")

        composeRule.onNodeWithTag("editTrackerButton-Family").performClick()
        composeRule.waitForIdle()

        assertEquals("Family", clicked?.title)
    }

    @Test
    fun deleteTracker_withConfirmation_removesItAndRestoresEmptyState() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithTag("deleteTrackerButton-Family").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.delete_tracker_title, "Family")).assertExists()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeRule.waitForNoText("Family")
        composeRule.onNodeWithText(string(R.string.tracker_list_empty)).assertExists()
    }

    @Test
    fun deleteTracker_dismissingConfirmation_keepsTracker() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithTag("deleteTrackerButton-Family").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        composeRule.onNodeWithText("Family").assertExists()
    }

    @Test
    fun creatingTracker_reportsItsIdSoItCanBeOpened() {
        var createdId: Long? = null
        composeRule.setContent {
            TrackerListScreen(
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onTrackerCreated = { createdId = it },
            )
        }

        createTracker("Family")

        val tracker = runBlocking { db.trackerDao().getByTitle("Family") }
        assertEquals(tracker?.id, createdId)
    }

    @Test
    fun creatingTrackerWithExistingTitle_showsErrorInsteadOfCrashing() {
        setContent()
        createTracker("Family")

        // Same name with stray spaces: still a duplicate once trimmed.
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput("  Family ")
        composeRule.onNodeWithText(string(R.string.action_create)).performClick()

        composeRule.waitForText(string(R.string.error_tracker_name_taken))
        composeRule.onNodeWithTag("trackerTitleInput").assertExists() // dialog stays open
        val trackers = runBlocking { trackerRepository.observeTrackers().first() }
        assertEquals(1, trackers.size)
    }

    @Test
    fun menu_offersImport_andExportOnlyWhenThereAreTrackers() {
        setContent()

        composeRule.onNodeWithTag("trackerListMenuButton").performClick()

        composeRule.onNodeWithTag("importItem").assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.menu_save_all)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.menu_share_all)).assertIsNotEnabled()
    }

    @Test
    fun menu_helpAndFaqItems_openTheirScreens() {
        val opened = mutableListOf<String>()
        setContent(onHelpClick = { opened += "help" }, onFaqClick = { opened += "faq" })

        composeRule.onNodeWithTag("trackerListMenuButton").performClick()
        composeRule.onNodeWithTag("helpItem").performClick()
        composeRule.onNodeWithTag("helpItem").assertDoesNotExist() // menu closed
        composeRule.onNodeWithTag("trackerListMenuButton").performClick()
        composeRule.onNodeWithTag("faqItem").performClick()

        assertEquals(listOf("help", "faq"), opened)
    }
}
