package com.spendobserver.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.TrackerEntity
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.testing.InMemoryDatabaseRule
import com.spendobserver.testing.string
import com.spendobserver.testing.waitFor
import com.spendobserver.testing.waitForNode
import com.spendobserver.testing.waitForTag
import com.spendobserver.testing.waitForText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpendObserverNavHostTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** The window's width; tests change it to fold or unfold the "device". */
    private var width by mutableStateOf(PhoneWidth)

    private val statsTitle get() = string(R.string.stats_title)

    /**
     * Whether the statistics are on screen. On a phone they're laid out beside the
     * records even while off-screen, so being in the tree isn't enough.
     */
    private fun statsShown() =
        runCatching { composeRule.onNodeWithText(statsTitle, useUnmergedTree = true).isDisplayed() }.getOrDefault(false)

    private fun waitForStatsShown() = composeRule.waitFor { statsShown() }

    private fun waitForStatsGone() = composeRule.waitFor { !statsShown() }

    /** A "Family" tracker and the whole app in a window [width] wide, on the start screen. */
    private fun setContent(width: Dp) {
        this.width = width
        val db = dbRule.db
        runBlocking { db.trackerDao().insert(TrackerEntity(title = "Family", createdAt = 0, updatedAt = 0)) }
        val records = RecordRepository(db)
        composeRule.setContent {
            navController = rememberNavController()
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            // requiredWidth, not width: a "window" wider than the emulator's screen is the point.
            Box(Modifier.requiredWidth(this.width).fillMaxHeight()) {
                SpendObserverNavHost(
                    navController = navController,
                    trackerRepository = TrackerRepository(db.trackerDao()),
                    recordRepository = records,
                    tagRepository = TagRepository(db.tagDao(), records::deleteTag),
                    backupRepository = BackupRepository(db),
                    themeMode = ThemeMode.entries.first(),
                    onThemeModeChange = {},
                )
            }
        }
        composeRule.waitForText("Family")
    }

    private fun currentRouteIs(check: (NavDestination) -> Boolean) =
        navController.currentDestination?.let(check) == true

    private fun previousRouteIsTrackerList() =
        navController.previousBackStackEntry?.destination?.hasRoute<Route.TrackerList>() == true

    @Test
    fun tappingBackTwiceDuringTheAnimation_stopsAtTheStartScreen() {
        setContent(width = PhoneWidth)
        composeRule.onNodeWithText("Family").performClick()
        val back = string(R.string.action_back)
        composeRule.waitForNode(hasContentDescription(back))

        // A frozen clock holds the closing animation, so the second tap lands on the same screen.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription(back).performClick()
        composeRule.onNodeWithContentDescription(back).performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(currentRouteIs { it.hasRoute<Route.TrackerList>() })
        }
    }

    /** Whether [title]'s row in the tracker list is on screen: beside a tracker, or on the start screen. */
    private fun rowShown(title: String) =
        runCatching { composeRule.onNodeWithTag("trackerRow-$title").isDisplayed() }.getOrDefault(false)

    private fun placeholderShown() =
        runCatching { composeRule.onNodeWithTag("noTrackerPane").isDisplayed() }.getOrDefault(false)

    private fun openTrackerId() = navController.currentBackStackEntry
        ?.takeIf { it.destination.hasRoute<Route.TrackerDetail>() }
        ?.toRoute<Route.TrackerDetail>()?.trackerId

    /** More trackers beside "Family", by title; their ids in the same order. */
    private fun addTrackers(vararg titles: String): List<Long> = runBlocking {
        titles.map { title -> dbRule.db.trackerDao().insert(TrackerEntity(title = title, createdAt = 0, updatedAt = 0)) }
    }

    @Test
    fun wideWindow_startScreen_showsAPlaceholderBesideTheList() {
        setContent(width = UnfoldedWidth)

        composeRule.waitFor { placeholderShown() }
        assertTrue(rowShown("Family"))
        assertFalse(statsShown())
    }

    @Test
    fun wideWindow_pickingATracker_opensItsRecordsBesideTheListPickedOut() {
        setContent(width = UnfoldedWidth)
        openTracker()

        composeRule.waitFor { !placeholderShown() }
        composeRule.onNodeWithTag("trackerRow-Family").assertIsSelected()
        assertFalse(statsShown())
        // No back arrow: the list it would go back to is right there.
        assertEquals(0, composeRule.onAllNodesWithContentDescription(string(R.string.action_back)).fetchSemanticsNodes().size)
    }

    @Test
    fun wideWindow_pickingAnotherTracker_takesTheOpenOnesPlace() {
        setContent(width = UnfoldedWidth)
        val (workId) = addTrackers("Work")
        openTracker()
        composeRule.waitForTag("trackerRow-Work")

        composeRule.onNodeWithTag("trackerRow-Work").performClick()

        composeRule.waitFor { openTrackerId() == workId }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trackerRow-Work").assertIsSelected()
        // Replaced, not stacked: back goes to the list alone, not to "Family".
        composeRule.runOnIdle { assertTrue(previousRouteIsTrackerList()) }
    }

    @Test
    fun wideWindow_aTrackerPickedFarDown_keepsTheListWhereItWas() {
        setContent(width = UnfoldedWidth)
        // Sorted after "Family", and far more than a screenful.
        addTrackers(*Array(30) { i -> "Zz %02d".format(i + 1) })
        composeRule.waitForTag("trackerRow-Zz 01")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("trackerRow-Zz 30"))

        composeRule.onNodeWithTag("trackerRow-Zz 30").performClick()

        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerDetail>() } }
        composeRule.waitForIdle()
        // A list scrolled back to the top wouldn't have this row at all.
        composeRule.waitFor { rowShown("Zz 30") }
        composeRule.onNodeWithTag("trackerRow-Zz 30").assertIsSelected()
    }

    @Test
    fun wideWindow_swipingLeft_bringsInTheStatisticsAndRightTheListBack() {
        setContent(width = UnfoldedWidth)
        openTracker()

        swipeLeftAcross()
        waitForStatsShown()
        composeRule.waitFor { !rowShown("Family") }

        swipeRightAcross()
        waitForStatsGone()
        composeRule.waitFor { rowShown("Family") }
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun wideWindow_swipingRightBesideTheList_doesNothing() {
        setContent(width = UnfoldedWidth)
        openTracker()

        swipeRightAcross()
        composeRule.waitForIdle()

        assertTrue(rowShown("Family"))
        assertFalse(statsShown())
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun wideWindow_withNoTrackerOpen_swipesDoNothing() {
        setContent(width = UnfoldedWidth)
        composeRule.waitFor { placeholderShown() }

        swipeLeftAcross()
        composeRule.waitForIdle()

        assertTrue(placeholderShown())
        assertTrue(rowShown("Family"))
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerList>() }) }
    }

    @Test
    fun wideWindow_backOnTheStatistics_returnsBesideTheList() {
        setContent(width = UnfoldedWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        composeRule.runOnUiThread { backDispatcher.onBackPressed() }

        waitForStatsGone()
        composeRule.waitFor { rowShown("Family") }
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun wideWindow_backBesideTheList_closesTheTracker() {
        setContent(width = UnfoldedWidth)
        openTracker()

        composeRule.runOnUiThread { backDispatcher.onBackPressed() }

        composeRule.waitFor { placeholderShown() }
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerList>() }) }
    }

    @Test
    fun foldingAndUnfoldingBesideTheList_keepsTheRecords() {
        setContent(width = UnfoldedWidth)
        openTracker()

        width = PhoneWidth
        composeRule.waitFor { !rowShown("Family") }
        assertFalse(statsShown())
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }

        width = UnfoldedWidth
        composeRule.waitFor { rowShown("Family") }
        assertFalse(statsShown())
    }

    @Test
    fun foldingOnTheStatistics_keepsThemOnScreen() {
        setContent(width = UnfoldedWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        width = PhoneWidth
        composeRule.waitForIdle()
        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }

        width = UnfoldedWidth
        waitForStatsShown()
        assertFalse(rowShown("Family"))
    }

    @Test
    fun phone_statsFromTheTrackerMenu_slideInAsItsSecondPane() {
        setContent(width = PhoneWidth)
        openTracker()
        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.waitForTag("statsItem")
        composeRule.onNodeWithTag("statsItem").performClick()

        waitForStatsShown()
        // The same screen, not a new one on top of it.
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun unfoldingOnTheStatsPane_showsThemBesideTheRecords() {
        setContent(width = PhoneWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        width = UnfoldedWidth
        composeRule.waitForText("Family")
        waitForStatsShown()
        // The records beside them, the list off-screen to their left.
        assertFalse(rowShown("Family"))
        composeRule.runOnIdle {
            assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() })
            assertTrue(previousRouteIsTrackerList())
        }
    }

    @Test
    fun unfoldingOnStatsOpenedFromTheStartScreen_replacesThemWithTheTracker() {
        setContent(width = PhoneWidth)
        composeRule.onNodeWithTag("trackerRowMenu-Family").performClick()
        composeRule.waitForTag("statsTrackerItem-Family")
        composeRule.onNodeWithTag("statsTrackerItem-Family").performClick()
        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerStats>() } }

        width = UnfoldedWidth
        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerDetail>() } }
        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(previousRouteIsTrackerList()) }
    }

    @Test
    fun wideWindow_statsFromTheStartScreenOpenTheTrackerOnThem() {
        setContent(width = UnfoldedWidth)
        composeRule.onNodeWithTag("trackerRowMenu-Family").performClick()
        composeRule.waitForTag("statsTrackerItem-Family")
        composeRule.onNodeWithTag("statsTrackerItem-Family").performClick()

        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    /**
     * Opens "Family" from the start screen and waits for its records. In two columns, also
     * until the start screen held beneath them has gone, with its copy of the tracker list.
     */
    private fun openTracker() {
        composeRule.onNodeWithText("Family").performClick()
        composeRule.waitForTag("trackerMenuButton")
        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerDetail>() } }
        composeRule.waitForIdle()
    }

    // Away from the edges, where a phone's own back gesture would take the swipe.
    private fun swipeLeftAcross() = composeRule.onRoot().performTouchInput {
        swipeLeft(startX = width * 0.8f, endX = width * 0.2f)
    }

    private fun swipeRightAcross() = composeRule.onRoot().performTouchInput {
        swipeRight(startX = width * 0.2f, endX = width * 0.8f)
    }

    @Test
    fun phone_swipingLeftOnTheRecords_bringsInTheStatistics() {
        setContent(width = PhoneWidth)
        openTracker()

        swipeLeftAcross()

        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun phone_swipingRightOnTheStatistics_bringsBackTheRecords() {
        setContent(width = PhoneWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        swipeRightAcross()

        waitForStatsGone()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun phone_backOnTheStatistics_returnsToTheRecordsNotTheStartScreen() {
        setContent(width = PhoneWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        // The system back button, as the app receives it.
        composeRule.runOnUiThread { backDispatcher.onBackPressed() }

        waitForStatsGone()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    @Test
    fun phone_swipingRightOnTheRecords_returnsToTheStartScreen() {
        setContent(width = PhoneWidth)
        openTracker()

        swipeRightAcross()

        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerList>() } }
    }

    @Test
    fun phone_swipingRightOnStatsFromTheStartScreen_returnsThere() {
        setContent(width = PhoneWidth)
        composeRule.onNodeWithTag("trackerRowMenu-Family").performClick()
        composeRule.waitForTag("statsTrackerItem-Family")
        composeRule.onNodeWithTag("statsTrackerItem-Family").performClick()
        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerStats>() } }

        swipeRightAcross()

        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerList>() } }
    }

    @Test
    fun phone_aShortSlowSwipe_staysOnTheRecords() {
        setContent(width = PhoneWidth)
        openTracker()

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = width * 0.55f, endX = width * 0.45f, durationMillis = 1000)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput {
            swipeRight(startX = width * 0.45f, endX = width * 0.55f, durationMillis = 1000)
        }
        composeRule.waitForIdle()

        assertFalse(statsShown())
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    private companion object {
        val PhoneWidth = 400.dp

        /** A book-style foldable opened in portrait. */
        val UnfoldedWidth = 700.dp
    }
}
