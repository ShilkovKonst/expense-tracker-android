package com.spendobserver.ui.record

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.amount
import com.spendobserver.testing.waitFor
import com.spendobserver.testing.waitForNode
import com.spendobserver.testing.waitForText
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
        composeRule.waitFor { savedCount == 1 }

        assertEquals(1, savedCount)
        val list = runBlocking { recordRepository.observeAll(trackerId).first() }
        assertEquals(1, list.size)
        assertEquals(1250L, list[0].amountCents)
        assertEquals(RecordType.INCOME, list[0].record.type)
        assertEquals("salary", list[0].record.description)
    }

    @Test
    fun invalidAmount_showsErrorAndDoesNotSave() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitForText("Введите сумму больше нуля")

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
                ),
                emptySet(),
                listOf(PartDraft(45000, setOf(tagId))),
            )
        }

        setContent(recordId = recordId)
        composeRule.waitForText("450.00")
        composeRule.waitForNode(hasText("rent") and isSelected())

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
                ),
                emptySet(),
                listOf(PartDraft(1000, emptySet())),
            )
        }

        setContent(recordId = recordId)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("deleteRecordButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitFor { savedCount == 1 }

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
        composeRule.waitForNode(hasText("groceries") and isSelected())

        composeRule.onNodeWithText("groceries").assertIsSelected()
        val tags = runBlocking { tagRepository.observeTags(trackerId).first() }
        assertTrue(tags.any { it.name == "groceries" })
    }

    @Test
    fun togglingTagSelection_deselectsIt() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "fuel")) }

        setContent()
        composeRule.waitForText("fuel")

        composeRule.onNodeWithText("fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fuel").assertIsSelected()

        composeRule.onNodeWithText("fuel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("fuel").assertIsNotSelected()
    }

    @Test
    fun splitReceipt_savesOneRecordWithTypedPartsAndComputedRemainder() {
        val (shop, groceries, alcohol) = runBlocking {
            Triple(
                tagRepository.insertTag(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true)),
                tagRepository.insertTag(Tag(trackerId = trackerId, name = "продукты")),
                tagRepository.insertTag(Tag(trackerId = trackerId, name = "алко")),
            )
        }
        setContent()
        composeRule.waitForText("алко")

        composeRule.onNodeWithTag("amountInput").performTextInput("3450")
        composeRule.onNodeWithTag("splitButton").performClick()
        composeRule.onNodeWithText("Ашан").performScrollTo().performClick()
        composeRule.onNodeWithTag("partAmountInput-0").performScrollTo().performTextInput("800")
        // Part 0's chips come first, the remainder's second.
        composeRule.onAllNodesWithText("алко")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText("продукты")[1].performScrollTo().performClick()
        composeRule.onNodeWithTag("remainderAmount").performScrollTo().assertTextEquals(amount(265000))
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitFor { savedCount == 1 }

        assertEquals(1, savedCount)
        val saved = runBlocking { recordRepository.observeAll(trackerId).first().single() }
        assertEquals(listOf(shop), saved.tags.map { it.id })
        assertEquals(
            listOf(80000L to setOf(alcohol), 265000L to setOf(groceries)),
            saved.parts.map { p -> p.part.amountCents to p.tags.map { it.id }.toSet() },
        )
    }

    @Test
    fun partsExceedingReceiptTotal_showErrorAndDoNotSave() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("amountInput").performTextInput("100")
        composeRule.onNodeWithTag("splitButton").performClick()
        composeRule.onNodeWithTag("partAmountInput-0").performScrollTo().performTextInput("150")
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitForText("Сумма частей должна быть меньше итога чека")

        composeRule.onNodeWithText("Сумма частей должна быть меньше итога чека").assertExists()
        assertEquals(0, savedCount)
    }

    @Test
    fun editingMultiPartRecord_showsEveryPartAndTheTotal() {
        val recordId = runBlocking {
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 15), type = RecordType.COST, description = null),
                emptySet(),
                listOf(PartDraft(80000, emptySet()), PartDraft(235000, emptySet())),
            )
        }
        setContent(recordId = recordId)
        composeRule.waitForText("800.00")

        composeRule.onNodeWithTag("partAmountInput-0").assertTextContains("800.00")
        composeRule.onNodeWithTag("partAmountInput-1").performScrollTo().assertTextContains("2350.00")
        composeRule.onNodeWithTag("recordTotal").performScrollTo().assertTextEquals("Итого: " + amount(315000))
    }

    @Test
    fun newGlobalTag_isCreatedGlobalAndSelectedOnTheRecord() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("newGlobalTagChip").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Ашан")
        composeRule.onNodeWithText("Создать").performClick()
        composeRule.waitForNode(hasText("Ашан") and isSelected())
        composeRule.onNodeWithTag("amountInput").performTextInput("100")
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.waitFor { savedCount == 1 }

        val saved = runBlocking { recordRepository.observeAll(trackerId).first().single() }
        assertEquals(listOf("Ашан"), saved.tags.map { it.name })
        assertTrue(saved.tags.single().isGlobal)
        assertTrue(saved.parts.single().tags.isEmpty())
    }

    @Test
    fun creatingTagWithAnExistingName_onEitherLevelOrCase_showsErrorAndKeepsDialogOpen() {
        runBlocking { tagRepository.insertTag(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true)) }
        setContent()
        composeRule.waitForText("Ашан")

        // A local tag can't reuse a global tag's name, even in another case.
        composeRule.onNodeWithTag("newTagChip").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput(" АШАН ")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        composeRule.waitForText("Тег с таким названием уже есть")

        composeRule.onNodeWithText("Тег с таким названием уже есть").assertExists()
        composeRule.onNodeWithTag("tagNameInput").assertExists()
        assertEquals(1, runBlocking { tagRepository.observeTags(trackerId).first() }.size)
    }

    @Test
    fun noTagsYet_eachLevelShowsAPlaceholder_untilATagIsCreated() {
        setContent()
        composeRule.waitForIdle()

        assertEquals(2, composeRule.onAllNodesWithText("Тегов пока нет").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("newGlobalTagChip").performClick()
        composeRule.onNodeWithTag("tagNameInput").performTextInput("Ашан")
        composeRule.onNodeWithTag("tagNameConfirmButton").performClick()
        // The chip, not the dialog's input that also holds "Ашан": a new tag comes selected.
        composeRule.waitForNode(hasText("Ашан") and isSelected())

        // Only the local level is still empty.
        assertEquals(1, composeRule.onAllNodesWithText("Тегов пока нет").fetchSemanticsNodes().size)
    }
}
