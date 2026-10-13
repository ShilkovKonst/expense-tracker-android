package com.spendobserver.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Bottom room a form's scrolling content needs so its last field can scroll
 * out from under [FormActionButtons]: 56dp button + 16dp margin + 16dp gap.
 */
val FormActionsBottomPadding = 88.dp

/**
 * "Отмена" and "Сохранить" for a form screen's `Scaffold.floatingActionButton`
 * slot: pinned bottom-right, within thumb reach, without scrolling to the end
 * of the form or reaching for the back arrow top-left.
 */
@Composable
fun FormActionButtons(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveTestTag: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExtendedFloatingActionButton(
            onClick = onCancel,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("cancelButton"),
        ) { Text("Отмена") }
        ExtendedFloatingActionButton(
            onClick = onSave,
            modifier = Modifier.testTag(saveTestTag),
        ) { Text("Сохранить") }
    }
}
