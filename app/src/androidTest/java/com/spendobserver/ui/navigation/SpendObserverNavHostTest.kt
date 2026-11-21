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
import androidx.compose.ui.test.hasContentDescription
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

    @Test
    fun wideWindow_showsTheTrackersStatisticsBesideItsRecords() {
        setContent(width = UnfoldedWidth)
        composeRule.onNodeWithText("Family").performClick()

        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
        // One way back, the record list's: the stats column has none of its own.
        assertEquals(1, composeRule.onAllNodesWithContentDescription(string(R.string.action_back)).fetchSemanticsNodes().size)
        // And the menu no longer offers the statistics already on screen.
        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.waitForTag("editTrackerItem")
        composeRule.onNodeWithTag("statsItem").assertDoesNotExist()
    }

    @Test
    fun foldingAndUnfolding_dropsAndBringsBackTheStatsColumn() {
        setContent(width = UnfoldedWidth)
        composeRule.onNodeWithText("Family").performClick()
        waitForStatsShown()

        width = PhoneWidth
        waitForStatsGone()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }

        width = UnfoldedWidth
        waitForStatsShown()
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
    fun unfoldingOnTheStatsPane_showsBothColumns() {
        setContent(width = PhoneWidth)
        openTracker()
        swipeLeftAcross()
        waitForStatsShown()

        width = UnfoldedWidth
        composeRule.waitForText("Family")
        waitForStatsShown()
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
        composeRule.runOnIdle { assertTrue(previousRouteIsTrackerList()) }
    }

    @Test
    fun wideWindow_statsFromTheStartScreenOpenTheTracker() {
        setContent(width = UnfoldedWidth)
        composeRule.onNodeWithTag("trackerRowMenu-Family").performClick()
        composeRule.waitForTag("statsTrackerItem-Family")
        composeRule.onNodeWithTag("statsTrackerItem-Family").performClick()

        waitForStatsShown()
        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    /** Opens "Family" from the start screen and waits for its records. */
    private fun openTracker() {
        composeRule.onNodeWithText("Family").performClick()
        composeRule.waitForTag("trackerMenuButton")
        composeRule.waitFor { currentRouteIs { it.hasRoute<Route.TrackerDetail>() } }
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

    @Test
    fun wideWindow_swipesDoNothingYet() {
        setContent(width = UnfoldedWidth)
        openTracker()

        swipeLeftAcross()
        composeRule.waitForIdle()
        swipeRightAcross()
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertTrue(currentRouteIs { it.hasRoute<Route.TrackerDetail>() }) }
    }

    private companion object {
        val PhoneWidth = 400.dp

        /** A book-style foldable opened in portrait. */
        val UnfoldedWidth = 700.dp
    }
}
