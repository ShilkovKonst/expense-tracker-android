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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.amount
import com.spendobserver.testing.month
import com.spendobserver.testing.plural
import com.spendobserver.testing.string
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

    /** "N records (N expenses, N incomes)" as the summary card shows it. */
    private fun counts(records: Int, costs: Int, incomes: Int) = string(
        R.string.stats_counts,
        plural(R.plurals.records_count, records),
        plural(R.plurals.stats_costs_count, costs),
        plural(R.plurals.stats_incomes_count, incomes),
    )

    private fun costs(cents: Long) = string(R.string.stats_costs_amount, amount(cents))

    /**
     * [loadedCosts] is the default view's summary line, shown once the data is in.
     * "Today" is pinned in 2026, the data's last year, so the all-time axis ends there.
     */
    private fun setContent(loadedCosts: String = costs(1700)) {
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
    private fun tapLastBar(crumb: String) = tapChartUntil(crumb) { 0.95f }

    /**
     * Taps across the chart, left to right and round again, until the breadcrumbs
     * reach [crumb]. A tap snaps to the nearest bar, so the sweep hits every bar —
     * and the empty ones on the way must open nothing, or the breadcrumbs would
     * move elsewhere and never reach [crumb].
     */
    private fun sweepBars(crumb: String) {
        var x = 0f
        tapChartUntil(crumb) {
            x = if (x >= 0.95f) 0.05f else x + 0.03f
            x
        }
    }

    private fun tapChartUntil(crumb: String, nextX: () -> Float) {
        composeRule.onNodeWithTag("statsChart").performScrollTo()
        composeRule.waitFor {
            val x = nextX()
            composeRule.onNodeWithTag("statsChart").performTouchInput { click(percentOffset(x, 0.5f)) }
            composeRule.waitForIdle()
            hasCrumb(crumb)
        }
    }

    private fun hasCrumb(crumb: String) =
        composeRule.onAllNodes(hasTestTag("breadcrumbs") and hasAnyDescendant(hasText(crumb)))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun overview_showsCountsCostTotalAndTopsPerLevel() {
        setContent()

        composeRule.onNodeWithText(counts(3, 2, 1)).assertExists()
        composeRule.onNodeWithText(costs(1700)).assertExists()
        composeRule.onNodeWithText(string(R.string.stats_most_often, "Ашан (1)")).assertExists()
        composeRule.onNodeWithText(string(R.string.stats_most_often, "продукты (2)")).assertExists()
        composeRule.onNodeWithText(string(R.string.stats_most_amount, "продукты (" + amount(1200) + ")")).assertExists()
    }

    @Test
    fun overview_isTheDefaultSubject_titledWithTheType() {
        setContent()

        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_overview, string(R.string.stats_costs_inline)))
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
        composeRule.waitForText(costs(1200))
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_tag, "продукты", string(R.string.stats_costs_inline)))
        composeRule.onNodeWithText(plural(R.plurals.stats_tag_found_in, 2)).assertExists()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("overviewRow").performClick()
        composeRule.waitForText(costs(1700))
    }

    @Test
    fun picker_cancelKeepsSelection() {
        setContent()

        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        composeRule.onNodeWithTag("subjectList").assertDoesNotExist()
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_overview, string(R.string.stats_costs_inline)))
    }

    @Test
    fun typeToggle_switchesToIncome() {
        setContent()

        composeRule.onNodeWithTag("incomeTypeButton").performClick()

        composeRule.waitForText(string(R.string.stats_incomes_amount, amount(9000)))
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_overview, string(R.string.stats_incomes_inline)))
        composeRule.onNodeWithText(counts(3, 2, 1)).assertExists()
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
        setContent(loadedCosts = costs(100))

        composeRule.onNodeWithTag("incomeTypeButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("costTypeButton").assertIsEnabled()
    }

    @Test
    fun selectedTypeLosingItsLastRecord_fallsBackToTheOtherType() {
        setContent()
        composeRule.onNodeWithTag("incomeTypeButton").performClick()
        composeRule.waitForText(string(R.string.stats_incomes_amount, amount(9000)))

        runBlocking {
            val income = recordRepository.getAll(trackerId).single { it.record.type == RecordType.INCOME }
            recordRepository.deleteRecord(income.record.id)
        }

        composeRule.waitForText(costs(1700))
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_overview, string(R.string.stats_costs_inline)))
        composeRule.onNodeWithTag("incomeTypeButton").assertIsNotEnabled()
    }

    @Test
    fun selectedTagDeletedElsewhere_fallsBackToOverview() {
        setContent()
        composeRule.onNodeWithTag("subjectField").performClick()
        composeRule.onNodeWithTag("tagRow-продукты").performClick()
        composeRule.waitForText(costs(1200))

        runBlocking {
            tagRepository.deleteTag(db.tagDao().getForTracker(trackerId).single { it.name == "продукты" })
        }

        composeRule.waitForText(costs(1700))
        composeRule.onNodeWithTag("subjectTitle").assertTextEquals(string(R.string.stats_subject_overview, string(R.string.stats_costs_inline)))
    }

    @Test
    fun tappingBars_drillsDown_andBreadcrumbsGoBack() {
        setContent()

        // All time → the last year (2026).
        tapLastBar(crumb = "2026")
        composeRule.waitForText(counts(2, 1, 1))
        composeRule.onNodeWithText(costs(1500)).assertExists()

        // 2026 → March, its only month with records; the empty months open nothing.
        sweepBars(crumb = month(3))
        composeRule.waitForText(costs(1500))

        composeRule.onNodeWithText("2026").performClick()
        composeRule.waitFor { !hasCrumb(month(3)) }
        composeRule.onNodeWithText(string(R.string.stats_all_time)).performClick()
        composeRule.waitForText(counts(3, 2, 1))
    }
}
