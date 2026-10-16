package com.spendobserver.ui.help

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent() {
        composeRule.setContent { FaqScreen(onBackClick = {}) }
    }

    @Test
    fun allQuestionsListed_answersCollapsed() {
        setContent()

        FAQ.forEach { entry ->
            composeRule.onNodeWithText(entry.question).performScrollTo().assertExists()
            composeRule.onNodeWithText(entry.answer.first()).assertDoesNotExist()
        }
    }

    @Test
    fun tappingQuestion_togglesItsAnswerOnly() {
        setContent()
        val entry = FAQ[2]

        composeRule.onNodeWithText(entry.question).performScrollTo().performClick()
        entry.answer.forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithText(FAQ[1].answer.first()).assertDoesNotExist()

        composeRule.onNodeWithText(entry.question).performClick()
        composeRule.onNodeWithText(entry.answer.first()).assertDoesNotExist()
    }
}
