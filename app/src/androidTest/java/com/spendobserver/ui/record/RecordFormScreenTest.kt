package com.spendobserver.ui.record

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var recordRepository: RecordRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0
    private var savedCount = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        recordRepository = RecordRepository(db)
        tagRepository = TagRepository(db.tagDao())

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        savedCount = 0
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setContent(recordId: Long? = null) {
        composeRule.setContent {
            RecordFormScreen(
                trackerId = trackerId,
                recordId = recordId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onSaved = { savedCount++ },
                onBackClick = {},
            )
        }
    }

    @Test
    fun creatingRecord_savesAmountTypeAndDescription() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("amountInput").performTextInput("12.50")
        composeRule.onNodeWithText("Доход").performClick()
        composeRule.onNodeWithTag("descriptionInput").performTextInput("salary")
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitForIdle()

        assertEquals(1, savedCount)
        val list = runBlocking { recordRepository.observeAll(trackerId).first() }
        assertEquals(1, list.size)
        assertEquals(1250L, list[0].amountCents)
        assertEquals(RecordType.INCOME, list[0].type)
        assertEquals("salary", list[0].description)
    }

    @Test
    fun invalidAmount_showsErrorAndDoesNotSave() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Введите сумму больше нуля").assertExists()
        assertEquals(0, savedCount)
    }

    @Test
    fun editingRecord_prefillsAmountDescriptionAndTag() {
        val tagId = runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "rent")) }
        val recordId = runBlocking {
            recordRepository.insertRecord(
                Record(
                    trackerId = trackerId,
                    date = LocalDate(2026, 3, 15),
                    type = RecordType.COST,
                    description = "monthly rent",
                    amountCents = 45000,
                ),
                setOf(tagId),
            )
        }

        setContent(recordId = recordId)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("amountInput").assertExists()
        composeRule.onNodeWithText("450.00").assertExists()
        composeRule.onNodeWithText("monthly rent").assertExists()
        composeRule.onNodeWithText("rent").assertIsSelected()
    }

    @Test
    fun deletingRecord_removesItAndCallsOnSaved() {
        val recordId = runBlocking {
            recordRepository.insertRecord(
                Record(
                    trackerId = trackerId,
                    date = LocalDate(2026, 3, 15),
                    type = RecordType.COST,
                    description = null,
                    amountCents = 1000,
                ),
                emptySet(),
            )
        }

        setContent(recordId = recordId)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("deleteRecordButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()

        assertEquals(1, savedCount)
        val remaining = runBlocking { recordRepository.getRecord(recordId) }
        assertNull(remaining)
    }

    @Test
    fun creatingTagOnTheFly_addsItSelectedAndPersisted() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("newTagChip").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("groceries")
        composeRule.onNodeWithText("Создать").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("groceries").assertIsSelected()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.any { it.name == "groceries" })
    }

    @Test
    fun togglingTagSelection_deselectsIt() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }

        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fuel").assertIsSelected()

        composeRule.onNodeWithText("fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fuel").assertIsNotSelected()
    }

    @Test
    fun managingTags_renamesThroughDialog() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }

        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manageTagsButton").performClick()
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
    fun managingTags_deletesAndUnselectsIt() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }

        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fuel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manageTagsButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("deleteTagButton-fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("fuel").assertDoesNotExist()
        val remainingTags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(remainingTags.isEmpty())

        // The deleted tag's id must also be gone from in-memory selection, or
        // saving would try to link the record to a tag id that no longer
        // exists and violate the record_tags -> tags foreign key.
        composeRule.onNodeWithTag("amountInput").performTextInput("10")
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitForIdle()

        assertEquals(1, savedCount)
        val record = runBlocking { recordRepository.observeAll(trackerId).first().single() }
        assertTrue(runBlocking { recordRepository.getTagIds(record.id) }.isEmpty())
    }
}
