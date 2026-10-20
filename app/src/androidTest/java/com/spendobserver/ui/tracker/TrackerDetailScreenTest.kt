package com.spendobserver.ui.tracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.amount
import com.spendobserver.testing.month
import com.spendobserver.testing.signedAmount
import com.spendobserver.testing.string
import com.spendobserver.testing.waitFor
import com.spendobserver.testing.waitForText
import com.spendobserver.ui.navigation.RecordChange
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDetailScreenTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val db get() = dbRule.db
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        trackerRepository = TrackerRepository(db.trackerDao())
        recordRepository = RecordRepository(db)
        tagRepository = TagRepository(db.tagDao())

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
    }

    private suspend fun insertRecord(
        date: LocalDate,
        type: RecordType,
        amountCents: Long,
        description: String?,
    ): Long =
        recordRepository.insertRecord(
            Record(trackerId = trackerId, date = date, type = type, description = description),
            emptySet(),
            listOf(PartDraft(amountCents, emptySet())),
        )

    private fun setContent(recordChange: RecordChange? = null) {
        composeRule.setContent {
            TrackerDetailScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onBackClick = {},
                recordChange = recordChange,
            )
        }
    }

    @Test
    fun noRecords_showsEmptyStatePlaceholder() {
        setContent()
        composeRule.waitForText("Family")

        composeRule.onNodeWithText("Записей пока нет").assertExists()
    }

    @Test
    fun yearAndMonth_areCollapsedByDefault() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, "groceries") }
        setContent()
        composeRule.waitForText("2026")

        composeRule.onNodeWithText("2026").assertExists()
        composeRule.onNodeWithText(month(3)).assertDoesNotExist()
        composeRule.onNodeWithText("Описание: groceries").assertDoesNotExist()
    }

    @Test
    fun expandingYearThenMonth_revealsRecords() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, "groceries") }
        setContent()
        composeRule.waitForText("2026")

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(month(3)).assertExists()
        composeRule.onNodeWithText("Описание: groceries").assertDoesNotExist()

        composeRule.onNodeWithText(month(3)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Описание: groceries").assertExists()
    }

    @Test
    fun monthAndYearTotals_netIncomeAgainstCostIndependently() {
        runBlocking {
            insertRecord(LocalDate(2026, 3, 1), RecordType.INCOME, 5000, "salary")
            insertRecord(LocalDate(2026, 3, 2), RecordType.COST, 2000, "rent")
            insertRecord(LocalDate(2026, 2, 1), RecordType.COST, 1000, "extra")
        }
        setContent()
        composeRule.waitForText(signedAmount(2000))

        // Year nets all three records (5000 - 2000 - 1000 = 2000); only visible before expanding.
        composeRule.onNodeWithText(signedAmount(2000)).assertExists()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()

        // March alone nets just its own two records (5000 - 2000 = 3000).
        composeRule.onNodeWithText(signedAmount(3000)).assertExists()
    }

    @Test
    fun yearAndMonthHeaders_showTheirOwnRecordCount() {
        runBlocking {
            insertRecord(LocalDate(2026, 3, 1), RecordType.COST, 1000, "a")
            insertRecord(LocalDate(2026, 3, 2), RecordType.COST, 1000, "b")
            insertRecord(LocalDate(2026, 2, 1), RecordType.COST, 1000, "c")
        }
        setContent()
        composeRule.waitForText("3 записи")

        // Year header: 3 records total, always visible.
        composeRule.onNodeWithText("3 записи").assertExists()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()

        // March header: only its own 2 records.
        composeRule.onNodeWithText("2 записи").assertExists()
    }

    @Test
    fun recordPreview_showsItsTags() {
        runBlocking {
            val tagId = tagRepository.insertTag(Tag(trackerId = trackerId, name = "groceries"))
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 15), type = RecordType.COST, description = "shopping"),
                emptySet(),
                listOf(PartDraft(1000, setOf(tagId))),
            )
        }
        setContent()
        composeRule.waitForText("2026")

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(month(3)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("groceries").assertExists()
    }

    @Test
    fun recordPreview_blankDescription_showsPlaceholderInsteadOfEmptyLine() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, null) }
        setContent()
        composeRule.waitForText("2026")

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(month(3)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Описание: без описания").assertExists()
    }

    @Test
    fun appBar_showsRecordCountAndNetTotalAcrossAllYears() {
        runBlocking {
            insertRecord(LocalDate(2025, 12, 1), RecordType.INCOME, 5000, "old income")
            insertRecord(LocalDate(2026, 3, 1), RecordType.COST, 2000, "this year")
        }
        setContent()
        composeRule.waitForText("2 записи · " + signedAmount(3000))

        // 2 records total, net across both years: 5000 - 2000 = 3000.
        composeRule.onNodeWithText("2 записи · " + signedAmount(3000)).assertExists()
    }

    @Test
    fun savedRecordChange_expandsItsYearAndMonthAndScrollsToIt() {
        // 30 records in one month push the oldest (bottom-most) one well off screen.
        val targetId = runBlocking {
            for (day in 2..28) insertRecord(LocalDate(2026, 3, day), RecordType.COST, 100, "filler $day")
            insertRecord(LocalDate(2025, 12, 1), RecordType.COST, 100, "last year")
            insertRecord(LocalDate(2026, 3, 1), RecordType.COST, 100, "target")
        }
        setContent(RecordChange(recordId = targetId, year = 2026, month = 3, deleted = false))
        composeRule.waitForText("Описание: target")
        composeRule.waitForIdle() // let the scroll to it settle

        composeRule.onNodeWithText("Описание: target").assertIsDisplayed()
        // Only the change's own year was opened.
        composeRule.onNodeWithText(month(12)).assertDoesNotExist()
    }

    @Test
    fun deletedRecordChange_opensTheMonthItWasRemovedFrom() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, "kept") }
        // The deleted record (id 999) is already gone from the database.
        setContent(RecordChange(recordId = 999, year = 2026, month = 3, deleted = true))
        composeRule.waitForText("Описание: kept")
        composeRule.waitForIdle() // let the scroll to it settle

        composeRule.onNodeWithText(month(3)).assertIsDisplayed()
        composeRule.onNodeWithText("Описание: kept").assertIsDisplayed()
    }

    @Test
    fun splitRecord_partsAreCollapsedAndToggleWithoutOpeningTheRecord() {
        val recordId = runBlocking {
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 15), type = RecordType.COST, description = "receipt"),
                emptySet(),
                listOf(PartDraft(80000, emptySet()), PartDraft(235000, emptySet())),
            )
        }
        var openedCount = 0
        composeRule.setContent {
            TrackerDetailScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onBackClick = {},
                onRecordClick = { openedCount++ },
                recordChange = RecordChange(recordId = recordId, year = 2026, month = 3, deleted = false),
            )
        }
        composeRule.waitForText("▸ 2 части")

        composeRule.onNodeWithText("▸ 2 части").assertExists()
        composeRule.onNodeWithText(amount(80000)).assertDoesNotExist()

        composeRule.onNodeWithTag("partsToggle-$recordId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("▾ 2 части").assertExists()
        composeRule.onNodeWithText(amount(80000)).assertIsDisplayed()
        composeRule.onNodeWithText(amount(235000)).assertIsDisplayed()
        assertEquals(0, openedCount)
    }

    @Test
    fun appBarTitle_followsARenameMadeElsewhere() {
        setContent()
        composeRule.waitForText("Family")

        runBlocking {
            val tracker = trackerRepository.getTracker(trackerId)!!
            trackerRepository.updateTracker(tracker.copy(title = "Семья"))
        }

        composeRule.waitForText("Семья")
    }

    @Test
    fun deletingTrackerFromAppBar_deletesItThenReportsIt() {
        var deletedCount = 0
        composeRule.setContent {
            TrackerDetailScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onBackClick = {},
                onTrackerDeleted = { deletedCount++ },
            )
        }
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.onNodeWithTag("deleteTrackerItem").performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.waitFor { deletedCount == 1 }

        assertEquals(1, deletedCount)
        assertNull(runBlocking { trackerRepository.getTracker(trackerId) })
    }

    @Test
    fun menu_holdsStatsExportEditAndDelete() {
        var statsOpened = false
        composeRule.setContent {
            TrackerDetailScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = BackupRepository(db),
                onBackClick = {},
                onStatsClick = { statsOpened = true },
            )
        }
        composeRule.waitForText("Family")

        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        listOf("statsItem", "exportSaveItem", "exportShareItem", "editTrackerItem", "deleteTrackerItem").forEach {
            composeRule.onNodeWithTag(it).assertExists()
        }
        composeRule.onNodeWithTag("statsItem").performClick()

        assertEquals(true, statsOpened)
        composeRule.onNodeWithTag("statsItem").assertDoesNotExist()
    }
}
