package com.spendobserver.ui.trackers

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var recordRepository: RecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        trackerRepository = TrackerRepository(db.trackerDao())
        recordRepository = RecordRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setContent(onEditTrackerClick: (Tracker) -> Unit = {}) {
        composeRule.setContent {
            TrackerListScreen(
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onEditTrackerClick = onEditTrackerClick,
            )
        }
    }

    private fun createTracker(title: String) {
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput(title)
        composeRule.onNodeWithText("Создать").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun emptyState_showsPlaceholder() {
        setContent()

        composeRule.onNodeWithText("Пока нет трекеров").assertExists()
    }

    @Test
    fun createTracker_appearsInListAndClearsEmptyState() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithText("Family").assertExists()
        composeRule.onNodeWithText("Пока нет трекеров").assertDoesNotExist()
    }

    @Test
    fun createButton_disabledForBlankTitle() {
        setContent()
        composeRule.onNodeWithText("+").performClick()

        composeRule.onNodeWithText("Создать").assertIsNotEnabled()
    }

    @Test
    fun newTracker_showsZeroRecordsAndZeroTotal() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithText("0 записей · 0.00").assertExists()
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
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 записи · +30.00").assertExists()
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
        composeRule.onNodeWithText("Удалить «Family»?").assertExists()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Family").assertDoesNotExist()
        composeRule.onNodeWithText("Пока нет трекеров").assertExists()
    }

    @Test
    fun deleteTracker_dismissingConfirmation_keepsTracker() {
        setContent()
        createTracker("Family")

        composeRule.onNodeWithTag("deleteTrackerButton-Family").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Отмена").performClick()

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

        val tracker = runBlocking { trackerRepository.findByTitle("Family") }
        assertEquals(tracker?.id, createdId)
    }

    @Test
    fun creatingTrackerWithExistingTitle_showsErrorInsteadOfCrashing() {
        setContent()
        createTracker("Family")

        // Same name with stray spaces: still a duplicate once trimmed.
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput("  Family ")
        composeRule.onNodeWithText("Создать").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Трекер с таким названием уже есть").assertExists()
        composeRule.onNodeWithTag("trackerTitleInput").assertExists() // dialog stays open
        val trackers = runBlocking { trackerRepository.observeTrackers().first() }
        assertEquals(1, trackers.size)
    }

    @Test
    fun menu_offersImport_andExportOnlyWhenThereAreTrackers() {
        setContent()

        composeRule.onNodeWithTag("trackerListMenuButton").performClick()

        composeRule.onNodeWithTag("importItem").assertIsEnabled()
        composeRule.onNodeWithText("Сохранить все в файл").assertIsNotEnabled()
        composeRule.onNodeWithText("Поделиться всеми").assertIsNotEnabled()
    }
}
