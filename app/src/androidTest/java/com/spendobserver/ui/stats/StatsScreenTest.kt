package com.spendobserver.ui.stats

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.amount
import com.spendobserver.testing.month
import com.spendobserver.testing.waitFor
import com.spendobserver.testing.waitForText
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsScreenTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val db get() = dbRule.db
    private lateinit var recordRepository: RecordRepository
    private lateinit var tagRepository: TagRepository
    private var trackerId: Long = 0

    /**
     * Ашан receipt (2026-03): 10.00 продукты + 5.00 untagged; продукты alone
     * (2025-05): 2.00; salary income (2026-03): 90.00; кино never used.
     */
    @Before
    fun setUp() = runBlocking {
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

    private suspend fun insert(date: LocalDate, type: RecordType, tagIds: Set<Long>, vararg parts: PartDraft) {
        recordRepository.insertRecord(
            Record(trackerId = trackerId, date = date, type = type, description = null),
            tagIds,
            parts.toList(),
        )
    }

    /**
     * [loadedCosts] is the default view's summary line, shown once the data is in.
     * "Today" is pinned in 2026, the data's last year, so the all-time axis ends there.
     */
    private fun setContent(loadedCosts: String = "Расходы: " + amount(1700)) {
        composeRule.setContent {
            StatsScreen(
                trackerId = trackerId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onBackClick = {},
                today = { LocalDate(2026, 10, 20) },
            )
        }
        composeRule.waitForText(loadedCosts)
    }

    /**
     * Taps the chart near its right edge — the marker snaps to the nearest, so the
     * last, bar — until the breadcrumbs reach [crumb]. Vico builds the chart's model
     * on a background dispatcher, so a tap before it's ready hits no bar; repeating
     * the tap until it lands is safe, since a landed tap moves on to [crumb].
     */
    private fun tapLastBar(crumb: String) {
        composeRule.onNodeWithTag("statsList").performScrollToNode(hasTestTag("statsChart"))
        composeRule.waitFor {
            composeRule.onNodeWithTag("statsChart").performTouchInput { click(percentOffset(0.95f, 0.5f)) }
            composeRule.waitForIdle()
            composeRule.onAllNodes(hasTestTag("breadcrumbs") and hasAnyDescendant(hasText(crumb)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun overview_showsCountsCostTotalAndTopsPerLevel() {
        setContent()

        composeRule.onNodeWithText("3 записи (2 расхода, 1 доход)").assertExists()
        composeRule.onNodeWithText("Расходы: " + amount(1700)).assertExists()
        composeRule.onNodeWithText("Чаще всего: Ашан (1)").assertExists()
        composeRule.onNodeWithText("Чаще всего: продукты (2)").assertExists()
        composeRule.onNodeWithText("Больше всего: продукты (" + amount(1200) + ")").assertExists()
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
        composeRule.onNodeWithTag("tagRow-кино").assert(hasText("0 · " + amount(0)))
    }

    @Test
    fun pickingTag_showsItsStats_andPickingOverviewReturns() {
        setContent()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("tagRow-продукты").performClick()
        composeRule.onNodeWithTag("subjectList").assertDoesNotExist()
        composeRule.waitForText("Расходы: " + amount(1200))
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals("Тег «продукты» (расходы)")
        composeRule.onNodeWithText("Встречается в 2 записях").assertExists()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("overviewRow").performClick()
        composeRule.waitForText("Расходы: " + amount(1700))
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

        composeRule.waitForText("Доходы: " + amount(9000))
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
        setContent(loadedCosts = "Расходы: " + amount(100))

        composeRule.onNodeWithTag("incomeTypeButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("costTypeButton").assertIsEnabled()
    }

    @Test
    fun tappingBars_drillsDown_andBreadcrumbsGoBack() {
        setContent()

        // All time → the last year (2026).
        tapLastBar(crumb = "2026")
        composeRule.waitForText("2 записи (1 расход, 1 доход)")
        composeRule.onNodeWithText("Расходы: " + amount(1500)).assertExists()

        // 2026 → its last month, December: nothing recorded there.
        tapLastBar(crumb = month(12))
        composeRule.waitForText("0 записей (0 расходов, 0 доходов)")

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitForText("2 записи (1 расход, 1 доход)")
        composeRule.onNodeWithText("Всё время").performClick()
        composeRule.waitForText("3 записи (2 расхода, 1 доход)")
    }
}
