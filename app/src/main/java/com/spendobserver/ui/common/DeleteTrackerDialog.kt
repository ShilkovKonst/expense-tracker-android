package com.spendobserver.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirmation before deleting a tracker — shared by the tracker list and the tracker's own screen. */
@Composable
fun DeleteTrackerDialog(trackerTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить «$trackerTitle»?") },
        text = { Text("Все записи этого трекера будут удалены без возможности восстановления.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
