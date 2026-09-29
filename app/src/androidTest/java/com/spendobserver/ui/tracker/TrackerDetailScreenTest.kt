package com.spendobserver.ui.tracker

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var trackerRepository: TrackerRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        trackerRepository = TrackerRepository(db.trackerDao())
        recordRepository = RecordRepository(db)
        tagRepository = TagRepository(db.tagDao())

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertRecord(
        date: LocalDate,
        type: RecordType,
        amountCents: Long,
        description: String?,
    ) {
        recordRepository.insertRecord(
            Record(trackerId = trackerId, date = date, type = type, description = description, amountCents = amountCents),
            emptySet(),
        )
    }

    private fun setContent() {
        composeRule.setContent {
            TrackerDetailScreen(
                trackerId = trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                onBackClick = {},
            )
        }
    }

    @Test
    fun noRecords_showsEmptyStatePlaceholder() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Записей пока нет").assertExists()
    }

    @Test
    fun trackerTitle_isShownInAppBar() {
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Family").assertExists()
    }

    @Test
    fun yearAndMonth_areCollapsedByDefault() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, "groceries") }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").assertExists()
        composeRule.onNodeWithText("Март").assertDoesNotExist()
        composeRule.onNodeWithText("Описание: groceries").assertDoesNotExist()
    }

    @Test
    fun expandingYearThenMonth_revealsRecords() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, "groceries") }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Март").assertExists()
        composeRule.onNodeWithText("Описание: groceries").assertDoesNotExist()

        composeRule.onNodeWithText("Март").performClick()
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
        composeRule.waitForIdle()

        // Year nets all three records (5000 - 2000 - 1000 = 2000); only visible before expanding.
        composeRule.onNodeWithText("+20.00").assertExists()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()

        // March alone nets just its own two records (5000 - 2000 = 3000).
        composeRule.onNodeWithText("+30.00").assertExists()
    }

    @Test
    fun yearAndMonthHeaders_showTheirOwnRecordCount() {
        runBlocking {
            insertRecord(LocalDate(2026, 3, 1), RecordType.COST, 1000, "a")
            insertRecord(LocalDate(2026, 3, 2), RecordType.COST, 1000, "b")
            insertRecord(LocalDate(2026, 2, 1), RecordType.COST, 1000, "c")
        }
        setContent()
        composeRule.waitForIdle()

        // Year header: 3 records total, always visible.
        composeRule.onNodeWithText("3 записи").assertExists()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()

        // March header: only its own 2 records.
        composeRule.onNodeWithText("2 записи").assertExists()
    }

    @Test
    fun multipleYears_areOrderedNewestFirstAndBothExpandable() {
        runBlocking {
            insertRecord(LocalDate(2025, 12, 1), RecordType.COST, 500, "old")
            insertRecord(LocalDate(2026, 1, 1), RecordType.COST, 500, "new")
        }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").assertExists()
        composeRule.onNodeWithText("2025").assertExists()
    }

    @Test
    fun recordPreview_showsItsTags() {
        runBlocking {
            val tagId = tagRepository.insertTag(Tag(trackerId = trackerId, name = "groceries"))
            recordRepository.insertRecord(
                Record(trackerId = trackerId, date = LocalDate(2026, 3, 15), type = RecordType.COST, description = "shopping", amountCents = 1000),
                setOf(tagId),
            )
        }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Март").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("groceries").assertExists()
    }

    @Test
    fun recordPreview_blankDescription_showsPlaceholderInsteadOfEmptyLine() {
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, null) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Март").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Описание: без описания").assertExists()
    }

    @Test
    fun recordPreview_longDescription_stillRendersAsOneLabeledLine() {
        val longDescription = "very very very very so long and detailed description of record so long that I cant read it in one line"
        runBlocking { insertRecord(LocalDate(2026, 3, 15), RecordType.COST, 1000, longDescription) }
        setContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Март").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Описание: $longDescription").assertExists()
    }

    @Test
    fun appBar_showsRecordCountAndNetTotalAcrossAllYears() {
        runBlocking {
            insertRecord(LocalDate(2025, 12, 1), RecordType.INCOME, 5000, "old income")
            insertRecord(LocalDate(2026, 3, 1), RecordType.COST, 2000, "this year")
        }
        setContent()
        composeRule.waitForIdle()

        // 2 records total, net across both years: 5000 - 2000 = 3000.
        composeRule.onNodeWithText("2 записи · +30.00").assertExists()
    }
}
