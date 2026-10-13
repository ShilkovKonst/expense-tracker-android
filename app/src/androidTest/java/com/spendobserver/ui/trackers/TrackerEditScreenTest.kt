package com.spendobserver.ui.trackers

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.record.PartDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerEditScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        trackerRepository = TrackerRepository(db.trackerDao())
        tagRepository = TagRepository(db.tagDao())

        trackerId = trackerRepository.insertTracker(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setContent() {
        composeRule.setContent {
            TrackerEditScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                tagRepository = tagRepository,
                onBackClick = {},
            )
        }
    }

    @Test
    fun nameField_prefillsWithCurrentTitle() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").assertExists()
        composeRule.onNodeWithText("Family").assertExists()
    }

    @Test
    fun renamingTracker_savesNewTitle() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput("Home")
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForIdle()

        val tracker = runBlocking { trackerRepository.getTracker(trackerId) }
        assertEquals("Home", tracker?.title)
        // Saved and still on screen: nothing left to save or revert.
        composeRule.onNodeWithTag("saveTrackerButton").assertIsNotEnabled()
    }

    @Test
    fun unchangedName_disablesSaveAndRevert() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saveTrackerButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("revertTitleButton").assertIsNotEnabled()
    }

    @Test
    fun revert_restoresTheSavedName() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput("Home")
        composeRule.onNodeWithTag("revertTitleButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Family").assertExists()
        composeRule.onNodeWithTag("revertTitleButton").assertIsNotEnabled()
    }

    @Test
    fun blankName_showsErrorAndDoesNotSave() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Введите название").assertExists()
        assertEquals("Family", runBlocking { trackerRepository.getTracker(trackerId)?.title })
    }

    @Test
    fun creatingTag_appearsInList() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("newTagButton").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("groceries")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("groceries").assertExists()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.any { it.name == "groceries" })
    }

    @Test
    fun renamingTag_updatesItsName() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("fuel & gas")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("fuel & gas").assertExists()
    }

    @Test
    fun deletingTag_removesItFromList() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("deleteTagButton-fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("fuel").assertDoesNotExist()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.isEmpty())
    }

    @Test
    fun creatingGlobalTag_storesItAsGlobal() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("newGlobalTagButton").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Ашан")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForIdle()

        val tag = runBlocking { tagRepository.observeTags(trackerId).first().single() }
        assertTrue(tag.isGlobal)
    }

    @Test
    fun usedTag_showsInHowManyRecords() {
        runBlocking {
            val tagId = tagRepository.insertTag(Tag(trackerId = trackerId, name = "алко"))
            RecordRepository(db).insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 1), type = RecordType.COST, description = null),
                emptySet(),
                listOf(PartDraft(100, setOf(tagId))),
            )
        }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("в 1 записи").assertExists()
    }

    @Test
    fun renamingToAnotherTrackersTitle_showsErrorAndDoesNotSave() {
        runBlocking { trackerRepository.insertTracker(Tracker(title = "Work", createdAt = 0, updatedAt = 0)) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput(" Work​")
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Трекер с таким названием уже есть").assertExists()
        assertEquals("Family", runBlocking { trackerRepository.getTracker(trackerId)?.title })
    }

    @Test
    fun ownTitle_withStraySpaces_doesNotCountAsAChange() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput("  Family  ")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saveTrackerButton").assertIsNotEnabled()
    }

    @Test
    fun renamingTagToAnotherTagsName_showsErrorAndKeepsItsName() {
        runBlocking {
            tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel"))
            tagRepository.insertTag(Tag(trackerId = trackerId, name = "food"))
        }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("FOOD")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Тег с таким названием уже есть").assertExists()
        val names = runBlocking { tagRepository.observeTags(trackerId).first() }.map { it.name }.toSet()
        assertEquals(setOf("fuel", "food"), names)
    }

    @Test
    fun recasingATagsOwnName_isAllowed() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Fuel")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForIdle()

        assertEquals("Fuel", runBlocking { tagRepository.observeTags(trackerId).first() }.single().name)
    }

    @Test
    fun renamingTrackerToAnotherTitleInADifferentCase_isRefused() {
        runBlocking { trackerRepository.insertTracker(Tracker(title = "Work", createdAt = 0, updatedAt = 0)) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput("work")
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Трекер с таким названием уже есть").assertExists()
        assertEquals("Family", runBlocking { trackerRepository.getTracker(trackerId)?.title })
    }
}
