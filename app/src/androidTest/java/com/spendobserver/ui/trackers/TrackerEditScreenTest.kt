package com.spendobserver.ui.trackers

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.plural
import com.spendobserver.testing.string
import com.spendobserver.testing.waitForNoNode
import com.spendobserver.testing.waitForNode
import com.spendobserver.testing.waitForTag
import com.spendobserver.testing.waitForText
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerEditScreenTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val db get() = dbRule.db
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        trackerRepository = TrackerRepository(db.trackerDao())
        tagRepository = TagRepository(db.tagDao())

        trackerId = trackerRepository.insertTracker(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
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

    private fun insertTags(prefix: String, count: Int, isGlobal: Boolean) = runBlocking {
        repeat(count) { tagRepository.insertTag(Tag(trackerId = trackerId, name = "$prefix$it", isGlobal = isGlobal)) }
    }

    private fun sectionHeight(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test
    fun fewTagsOnBothLevels_shareTheHeightEqually() {
        insertTags("place", 2, isGlobal = true)
        insertTags("what", 1, isGlobal = false)

        setContent()
        composeRule.waitForText("what0")

        assertEquals(sectionHeight("globalTagsSection").value, sectionHeight("localTagsSection").value, 1f)
    }

    @Test
    fun manyGlobalAndFewLocal_globalTakesTheRoom_andScrollsInside() {
        insertTags("place", 30, isGlobal = true)
        insertTags("what", 2, isGlobal = false)

        setContent()
        composeRule.waitForText("what1")

        assertTrue(sectionHeight("globalTagsSection") > sectionHeight("localTagsSection"))
        composeRule.onNodeWithText("what1").assertIsDisplayed()
        composeRule.onNodeWithText("place29").assertIsNotDisplayed()

        composeRule.onNodeWithText("place29").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("newGlobalTagButton").assertIsDisplayed()
        composeRule.onNodeWithTag("trackerNameInput").assertIsDisplayed()
    }

    @Test
    fun nameField_prefillsWithCurrentTitle() {
        setContent()
        composeRule.waitForText("Family")

        // The app bar shows "Family" too: check the field itself holds it.
        composeRule.onNodeWithTag("trackerNameInput").assert(hasText("Family"))
    }

    @Test
    fun renamingTracker_savesNewTitle() {
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput("Home")
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForNode(hasTestTag("saveTrackerButton") and isNotEnabled())

        val tracker = runBlocking { trackerRepository.getTracker(trackerId) }
        assertEquals("Home", tracker?.title)
        // Saved and still on screen: nothing left to save or revert.
        composeRule.onNodeWithTag("saveTrackerButton").assertIsNotEnabled()
    }

    @Test
    fun unchangedName_disablesSaveAndRevert() {
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("saveTrackerButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("revertTitleButton").assertIsNotEnabled()
    }

    @Test
    fun revert_restoresTheSavedName() {
        setContent()
        composeRule.waitForText("Family")

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
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForText(string(R.string.error_name_empty))
        assertEquals("Family", runBlocking { trackerRepository.getTracker(trackerId)?.title })
    }

    @Test
    fun creatingTag_appearsInList() {
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("newTagButton").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("groceries")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForTag("renameTagButton-groceries")

        composeRule.onNodeWithText("groceries").assertExists()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.any { it.name == "groceries" })
    }

    @Test
    fun renamingTag_updatesItsName() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForTag("renameTagButton-fuel")

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("fuel & gas")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForTag("renameTagButton-fuel & gas")

        composeRule.onNodeWithText("fuel & gas").assertExists()
    }

    @Test
    fun deletingTag_removesItFromList() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForTag("deleteTagButton-fuel")

        composeRule.onNodeWithTag("deleteTagButton-fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.waitForNoNode(hasTestTag("deleteTagButton-fuel"))

        composeRule.onNodeWithText("fuel").assertDoesNotExist()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.isEmpty())
    }

    @Test
    fun creatingGlobalTag_storesItAsGlobal() {
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("newGlobalTagButton").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Ашан")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForTag("renameTagButton-Ашан")

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
        composeRule.waitForText(plural(R.plurals.tag_usage, 1))

        composeRule.onNodeWithText(plural(R.plurals.tag_usage, 1)).assertExists()
    }

    @Test
    fun renamingToAnotherTrackersTitle_showsErrorAndDoesNotSave() {
        runBlocking { trackerRepository.insertTracker(Tracker(title = "Work", createdAt = 0, updatedAt = 0)) }
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("trackerNameInput").performTextClearance()
        composeRule.onNodeWithTag("trackerNameInput").performTextInput(" Work​")
        composeRule.onNodeWithTag("saveTrackerButton").performClick()
        composeRule.waitForText(string(R.string.error_tracker_name_taken))
        assertEquals("Family", runBlocking { trackerRepository.getTracker(trackerId)?.title })
    }

    @Test
    fun ownTitle_withStraySpaces_doesNotCountAsAChange() {
        setContent()
        composeRule.waitForText("Family")

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
        composeRule.waitForTag("renameTagButton-food")

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("FOOD")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForText(string(R.string.error_tag_name_taken))
        val names = runBlocking { tagRepository.observeTags(trackerId).first() }.map { it.name }.toSet()
        assertEquals(setOf("fuel", "food"), names)
    }

    @Test
    fun recasingATagsOwnName_isAllowed() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }
        setContent()
        composeRule.waitForTag("renameTagButton-fuel")

        composeRule.onNodeWithTag("renameTagButton-fuel").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextClearance()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Fuel")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForTag("renameTagButton-Fuel")

        assertEquals("Fuel", runBlocking { tagRepository.observeTags(trackerId).first() }.single().name)
    }
}
