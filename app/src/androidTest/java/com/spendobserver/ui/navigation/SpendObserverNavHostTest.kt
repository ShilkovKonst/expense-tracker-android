package com.spendobserver.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.testing.InMemoryDatabaseRule
import com.spendobserver.testing.string
import com.spendobserver.testing.waitForNode
import com.spendobserver.testing.waitForText
import androidx.compose.ui.test.hasContentDescription
import kotlinx.coroutines.runBlocking
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

    @Test
    fun tappingBackTwiceDuringTheAnimation_stopsAtTheStartScreen() {
        val db = dbRule.db
        runBlocking { db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0)) }
        val records = RecordRepository(db)
        composeRule.setContent {
            navController = rememberNavController()
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
        composeRule.waitForText("Family")
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
            assertTrue(navController.currentDestination?.hasRoute<Route.TrackerList>() == true)
        }
    }
}
