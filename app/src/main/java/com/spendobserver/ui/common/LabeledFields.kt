package com.spendobserver.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * An [OutlinedTextField] whose [label] always sits on the border — even
 * while the field is empty and unfocused — so the [placeholder] can show
 * inside it, matching fields that always hold a value (like the date).
 *
 * `labelPosition` only exists on the [androidx.compose.foundation.text.input.TextFieldState]
 * overload, so this keeps a state internally and syncs it both ways with
 * the plain [value]/[onValueChange] the rest of the form uses.
 */
@Composable
fun AlwaysLabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // Outside → field: e.g. a stored value arriving after an async load.
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    // Field → outside: every edit the user makes.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { currentOnValueChange(it) }
    }
    OutlinedTextField(
        state = state,
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A bordered group whose [label] cuts into the top border — the same look
 * as a minimized [OutlinedTextField] label, for things that aren't a single
 * field (a record's part: its amount, tags, and remove button).
 *
 * The cut is faked by painting the label over the border with the page
 * [background] color, so this must sit directly on that background.
 */
@Composable
fun LabeledOutlinedBox(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Top padding leaves room for the half of the label sticking out above the border.
    Box(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, OutlinedTextFieldDefaults.shape)
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            content = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = 12.dp, y = (-8).dp)
                .background(background)
                .padding(horizontal = 4.dp),
        )
    }
}
