package com.spendobserver.ui.trackers

import android.content.Context
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
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    private var savedCount = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        trackerRepository = TrackerRepository(db.trackerDao())
        tagRepository = TagRepository(db.tagDao())

        trackerId = trackerRepository.insertTracker(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        savedCount = 0
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
                onSaved = { savedCount++ },
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

        assertEquals(1, savedCount)
        val tracker = runBlocking { trackerRepository.getTracker(trackerId) }
        assertEquals("Home", tracker?.title)
    }

    @Test
    fun blankName_showsErrorAndDoesNotSave() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Введите название").assertExists()
        assertEquals(0, savedCount)
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
}
