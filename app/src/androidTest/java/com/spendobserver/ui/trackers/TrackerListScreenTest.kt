package com.spendobserver.ui.trackers

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.spendobserver.data.repository.TrackerRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: TrackerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = TrackerRepository(db.trackerDao())

        composeRule.setContent {
            TrackerListScreen(trackerRepository = repository)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTracker(title: String) {
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput(title)
        composeRule.onNodeWithText("Создать").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun emptyState_showsPlaceholder() {
        composeRule.onNodeWithText("Пока нет трекеров").assertExists()
    }

    @Test
    fun createTracker_appearsInListAndClearsEmptyState() {
        createTracker("Family")

        composeRule.onNodeWithText("Family").assertExists()
        composeRule.onNodeWithText("Пока нет трекеров").assertDoesNotExist()
    }

    @Test
    fun createButton_disabledForBlankTitle() {
        composeRule.onNodeWithText("+").performClick()

        composeRule.onNodeWithText("Создать").assertIsNotEnabled()
    }

    @Test
    fun renameTracker_updatesTitleInList() {
        createTracker("Family")

        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.onNodeWithText("Переименовать").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trackerTitleInput").performTextClearance()
        composeRule.onNodeWithTag("trackerTitleInput").performTextInput("Home")
        composeRule.onNodeWithText("Сохранить").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Family").assertDoesNotExist()
    }

    @Test
    fun deleteTracker_withConfirmation_removesItAndRestoresEmptyState() {
        createTracker("Family")

        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Удалить «Family»?").assertExists()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Family").assertDoesNotExist()
        composeRule.onNodeWithText("Пока нет трекеров").assertExists()
    }

    @Test
    fun deleteTracker_dismissingConfirmation_keepsTracker() {
        createTracker("Family")

        composeRule.onNodeWithTag("trackerMenuButton").performClick()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.onNodeWithText("Family").assertExists()
    }
}
