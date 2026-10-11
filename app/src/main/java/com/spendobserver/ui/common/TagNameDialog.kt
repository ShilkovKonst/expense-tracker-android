package com.spendobserver.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spendobserver.domain.text.trimInvisible

/**
 * A single text-field dialog for creating or renaming a tag. Shared between
 * the record form (create-on-the-fly) and the tracker-edit screen (full tag
 * management), so both stay visually and behaviorally identical.
 */
@Composable
fun TagNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    onTextChange: () -> Unit = {},
) {
    var text by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onTextChange()
                },
                singleLine = true,
                label = { Text("Название") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("tagNameInput"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trimInvisible().isNotEmpty(),
                modifier = Modifier.testTag("tagNameConfirmButton"),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
