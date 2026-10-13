package com.spendobserver.ui.stats

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var recordRepository: RecordRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    /**
     * Ашан receipt (2026-03): 10.00 продукты + 5.00 untagged; продукты alone
     * (2025-05): 2.00; salary income (2026-03): 90.00; кино never used.
     */
    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        recordRepository = RecordRepository(db)
        tagRepository = TagRepository(db.tagDao())

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        val auchan = tagRepository.insertTag(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true))
        val food = tagRepository.insertTag(Tag(trackerId = trackerId, name = "продукты"))
        tagRepository.insertTag(Tag(trackerId = trackerId, name = "кино"))

        insert(LocalDate(2026, 3, 1), RecordType.COST, setOf(auchan), PartDraft(1000, setOf(food)), PartDraft(500, emptySet()))
        insert(LocalDate(2025, 5, 1), RecordType.COST, emptySet(), PartDraft(200, setOf(food)))
        insert(LocalDate(2026, 3, 5), RecordType.INCOME, emptySet(), PartDraft(9000, emptySet()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(date: LocalDate, type: RecordType, tagIds: Set<Long>, vararg parts: PartDraft) {
        recordRepository.insertRecord(
            Record(trackerId = trackerId, date = date, type = type, description = null),
            tagIds,
            parts.toList(),
        )
    }

    private fun setContent() {
        composeRule.setContent {
            StatsScreen(
                trackerId = trackerId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onBackClick = {},
            )
        }
        composeRule.waitForIdle()
    }

    /** Taps the chart near its right edge: the marker snaps to the nearest — so the last — bar. */
    private fun tapLastBar() {
        composeRule.onNodeWithTag("statsList").performScrollToNode(hasTestTag("statsChart"))
        composeRule.onNodeWithTag("statsChart").performTouchInput { click(percentOffset(0.95f, 0.5f)) }
        composeRule.waitForIdle()
    }

    @Test
    fun overview_showsCountsCostTotalAndTopsPerLevel() {
        setContent()

        composeRule.onNodeWithText("3 записи (2 расхода, 1 доход)").assertExists()
        composeRule.onNodeWithText("Расходы: 17.00").assertExists()
        composeRule.onNodeWithText("Чаще всего: Ашан (1)").assertExists()
        composeRule.onNodeWithText("Чаще всего: продукты (2)").assertExists()
        composeRule.onNodeWithText("Больше всего: продукты (12.00)").assertExists()
    }

    @Test
    fun overview_isTheDefaultSubject_titledWithTheType() {
        setContent()

        composeRule.onNodeWithTag("subjectTitle").assertTextEquals("Общая статистика (расходы)")
    }

    @Test
    fun picker_listsUnusedTagWithZeros() {
        setContent()

        composeRule.onNodeWithTag("subjectField").performClick()

        composeRule.onNodeWithTag("subjectList").performScrollToNode(hasTestTag("tagRow-кино"))
        composeRule.onNodeWithText("0 · 0.00").assertExists()
    }

    @Test
    fun pickingTag_showsItsStats_andPickingOverviewReturns() {
        setContent()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("tagRow-продукты").performClick()
        composeRule.onNodeWithTag("subjectList").assertDoesNotExist()
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals("Тег «продукты» (расходы)")
        composeRule.onNodeWithText("Встречается в 2 записях").assertExists()
        composeRule.onNodeWithText("Расходы: 12.00").assertExists()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("overviewRow").performClick()
        composeRule.onNodeWithText("Расходы: 17.00").assertExists()
    }

    @Test
    fun picker_cancelKeepsSelection() {
        setContent()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.onNodeWithTag("subjectList").assertDoesNotExist()
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals("Общая статистика (расходы)")
    }

    @Test
    fun typeToggle_switchesToIncome() {
        setContent()

        composeRule.onNodeWithTag("incomeTypeButton").performClick()

        composeRule.onNodeWithText("Доходы: 90.00").assertExists()
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals("Общая статистика (доходы)")
        composeRule.onNodeWithText("3 записи (2 расхода, 1 доход)").assertExists()
    }

    @Test
    fun typeWithoutRecordsInTracker_isDisabled() {
        trackerId = runBlocking {
            val id = db.trackerDao().insert(Tracker(title = "Costs only", createdAt = 0, updatedAt = 0))
            recordRepository.insertRecord(
                Record(trackerId = id, date = LocalDate(2026, 3, 1), type = RecordType.COST, description = null),
                emptySet(),
                listOf(PartDraft(100, emptySet())),
            )
            id
        }
        setContent()

        composeRule.onNodeWithTag("incomeTypeButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("costTypeButton").assertIsEnabled()
    }

    @Test
    fun metricToggle_keepsTheChart() {
        setContent()

        composeRule.onNodeWithText("Записи").performClick()

        composeRule.onNodeWithTag("statsList").performScrollToNode(hasTestTag("statsChart"))
        composeRule.onNodeWithTag("statsChart").assertExists()
    }

    @Test
    fun tappingBars_drillsDown_andBreadcrumbsGoBack() {
        setContent()

        // All time → the last year (2026).
        tapLastBar()
        composeRule.onNodeWithTag("breadcrumbs").assert(hasAnyDescendant(hasText("2026")))
        composeRule.onNodeWithText("2 записи (1 расход, 1 доход)").assertExists()
        composeRule.onNodeWithText("Расходы: 15.00").assertExists()

        // 2026 → its last month, December: nothing recorded there.
        tapLastBar()
        composeRule.onNodeWithText("Декабрь").assertExists()
        composeRule.onNodeWithText("0 записей (0 расходов, 0 доходов)").assertExists()

        composeRule.onNodeWithText("2026").performClick()
        composeRule.onNodeWithText("2 записи (1 расход, 1 доход)").assertExists()
        composeRule.onNodeWithText("Всё время").performClick()
        composeRule.onNodeWithText("3 записи (2 расхода, 1 доход)").assertExists()
    }
}
