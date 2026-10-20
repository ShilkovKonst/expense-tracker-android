package com.spendobserver.ui.help

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendobserver.R
import com.spendobserver.testing.string
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setContent(openFeedback: (Context) -> Boolean = { true }) {
        composeRule.setContent {
            HelpScreen(onBackClick = {}, openFeedback = openFeedback)
        }
    }

    @Test
    fun sections_followInAgreedOrder() {
        setContent()

        val tops = listOf("helpAboutSection", "helpDataSection", "helpLicensesSection")
            .map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun about_showsInstalledVersionAndContacts() {
        setContent()

        val version = appVersionName(context)
        assertNotEquals("—", version)
        composeRule.onNodeWithText(string(R.string.help_version, version)).assertExists()
        composeRule.onNodeWithText(string(R.string.help_email, FEEDBACK_EMAIL)).assertExists()
    }

    @Test
    fun feedbackButton_withMailApp_opensItAndShowsNoDialog() {
        var calls = 0
        setContent(openFeedback = { calls++; true })

        composeRule.onNodeWithTag("feedbackButton").performScrollTo().performClick()

        assertEquals(1, calls)
        composeRule.onNodeWithText(string(R.string.help_no_mail_title)).assertDoesNotExist()
    }

    @Test
    fun feedbackButton_withoutMailApp_offersToCopyAddress() {
        setContent(openFeedback = { false })

        composeRule.onNodeWithTag("feedbackButton").performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.help_no_mail_title)).assertExists()
        composeRule.onNodeWithTag("copyEmailButton").performClick()

        composeRule.onNodeWithText(string(R.string.help_address_copied)).assertExists()
        composeRule.onNodeWithText(string(R.string.help_no_mail_title)).assertDoesNotExist()
        var copied: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        }
        assertEquals(FEEDBACK_EMAIL, copied)
    }

    @Test
    fun feedbackIntent_isMailtoLetterWithSubjectAndVersion() {
        val intent = feedbackIntent(context)

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:", intent.data.toString())
        assertArrayEquals(arrayOf(FEEDBACK_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(string(R.string.feedback_subject), intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body, "App version: ${appVersionName(context)}" in body)
    }
}
