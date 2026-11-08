package com.spendobserver.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.spendobserver.R

/** What a screen reader says about a section that opens and closes: its state, and what a tap does. */
class ExpandLabels(val state: String, val action: String)

@Composable
fun expandLabels(expanded: Boolean) = ExpandLabels(
    state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed),
    action = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
)

/**
 * Opens or closes a section on tap. TalkBack reads [ExpandLabels.state] with
 * the row ("2026, 12 records, expanded") and offers "double-tap to collapse".
 * The labels come in from [expandLabels] because a modifier factory shouldn't
 * itself be @Composable.
 */
fun Modifier.expandToggle(labels: ExpandLabels, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = labels.action, onClick = onClick)
        .semantics { stateDescription = labels.state }
