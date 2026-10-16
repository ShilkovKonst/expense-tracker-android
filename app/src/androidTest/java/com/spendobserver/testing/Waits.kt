package com.spendobserver.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/*
 * Waits for what the screen shows after Room or a ViewModel coroutine has done
 * its work. waitForIdle() only waits for Compose itself — not for Room's query
 * threads or viewModelScope — so asserting right after it races the database.
 */

const val WAIT_TIMEOUT_MS = 5_000L

/**
 * Waits until at least one node matches [matcher]. Merged tree by default, where a
 * chip and its label are one node (`hasText(…) and isSelected()`); unmerged when
 * looking for a bare text that a merged parent (such as an app bar title) hides.
 */
fun ComposeContentTestRule.waitForNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) {
    waitUntil(WAIT_TIMEOUT_MS) {
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Waits until no node matches [matcher]. */
fun ComposeContentTestRule.waitForNoNode(matcher: SemanticsMatcher) {
    waitUntil(WAIT_TIMEOUT_MS) {
        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
}

fun ComposeContentTestRule.waitForText(text: String, substring: Boolean = false) =
    waitForNode(hasText(text, substring = substring), useUnmergedTree = true)

fun ComposeContentTestRule.waitForNoText(text: String) = waitForNoNode(hasText(text))

fun ComposeContentTestRule.waitForTag(tag: String) = waitForNode(hasTestTag(tag))

/** Waits until [condition] holds — for state outside the UI, such as a save callback having fired. */
fun ComposeContentTestRule.waitFor(condition: () -> Boolean) = waitUntil(WAIT_TIMEOUT_MS, condition)
