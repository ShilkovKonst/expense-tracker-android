package com.spendobserver.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.summary.recordsWord
import com.spendobserver.domain.summary.russianPlural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the system file picker; the chosen file's text (or null if it
 * couldn't be read) goes to [onText]. Any type is offered: file managers
 * label `.json` inconsistently, and the content check sorts it out anyway.
 */
@Composable
fun rememberImportLauncher(onText: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnText by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.reader().readText() } }.getOrNull()
            }
            currentOnText(text)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** Whatever [state] needs on screen: the preview, an error, or a busy indicator. */
@Composable
fun ImportDialogs(
    state: ImportState,
    onSetResolution: (index: Int, ClashResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        ImportState.Idle, is ImportState.Done -> Unit
        ImportState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = { CircularProgressIndicator() },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Импорт невозможен") },
            text = { Text(state.message, modifier = Modifier.testTag("importError")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is ImportState.Preview -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Импорт") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    state.candidates.forEachIndexed { index, candidate ->
                        CandidateBlock(candidate, onSelect = { onSetResolution(index, it) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag("importConfirmButton")) { Text("Импортировать") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CandidateBlock(candidate: ImportCandidate, onSelect: (ClashResolution) -> Unit) {
    val tracker = candidate.tracker
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tracker.title, fontWeight = FontWeight.SemiBold)
        val records = tracker.records.size
        val tags = tracker.tags.size
        Text(
            "$records ${recordsWord(records)} · $tags ${russianPlural(tags, "тег", "тега", "тегов")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val existing = candidate.clashesWith ?: return@Column
        Text("Трекер «$existing» уже есть", modifier = Modifier.padding(top = 4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = candidate.resolution == ClashResolution.COPY,
                onClick = { onSelect(ClashResolution.COPY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("resolveCopy-${tracker.title}"),
            ) { Text("Копия") }
            SegmentedButton(
                selected = candidate.resolution == ClashResolution.REPLACE,
                onClick = { onSelect(ClashResolution.REPLACE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("resolveReplace-${tracker.title}"),
            ) { Text("Заменить") }
        }
        when (candidate.resolution) {
            ClashResolution.COPY -> Text(
                "Будет добавлен как «${candidate.copyTitle}»",
                style = MaterialTheme.typography.bodySmall,
            )
            ClashResolution.REPLACE -> Text(
                "«$existing» и все его записи будут удалены",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }
    }
}
