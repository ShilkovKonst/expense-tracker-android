package com.spendobserver.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.spendobserver.R
import com.spendobserver.data.repository.BackupRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/** No value is saved for `null`; all trackers are an empty list, one is its id and title. */
private val PendingExportSaver = Saver<ExportScope?, ArrayList<Any>>(
    save = { target ->
        when (target) {
            null -> null
            ExportScope.All -> arrayListOf()
            is ExportScope.One -> arrayListOf(target.trackerId, target.title)
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) ExportScope.All else ExportScope.One(saved[0] as Long, saved[1] as String)
    },
)

/** Starts an export; results (and failures) come back through the `onMessage` it was made with. */
class ExportActions internal constructor(
    val save: (ExportScope) -> Unit,
    val share: (ExportScope) -> Unit,
)

/**
 * "Save to a file" goes through the system's create-document screen: the
 * user picks folder and name, Android hands back a Uri, and only then is
 * the file written. "Share" writes to the cache and opens the share sheet.
 */
@Composable
fun rememberExportActions(backupRepository: BackupRepository, onMessage: (String) -> Unit): ExportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnMessage by rememberUpdatedState(onMessage)
    // Read when a result arrives, so a language change since then is respected.
    val resources by rememberUpdatedState(LocalResources.current)
    val exporter = remember(context, backupRepository) { BackupExporter(context.applicationContext, backupRepository) }

    // What the open create-document screen is for; the launcher result only carries the Uri.
    // Saveable: turning the phone on that screen recreates the activity beneath it, and
    // without this the document it creates would stay empty.
    var pending by rememberSaveable(stateSaver = PendingExportSaver) { mutableStateOf<ExportScope?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val target = pending
        pending = null
        if (uri != null && target != null) {
            // Detached from the composition: a turn of the phone mid-write must not cut the file short.
            scope.launch(NonCancellable) {
                runCatching { exporter.saveTo(uri, target) }
                    .onSuccess { currentOnMessage(resources.getString(R.string.export_saved)) }
                    .onFailure { currentOnMessage(resources.getString(R.string.export_save_failed)) }
            }
        }
    }

    return remember(exporter, saveLauncher) {
        ExportActions(
            save = { target ->
                pending = target
                saveLauncher.launch(exportFileName(target))
            },
            share = { target ->
                scope.launch {
                    runCatching { exporter.shareIntent(target) }
                        .onSuccess { context.startActivity(it) }
                        .onFailure { currentOnMessage(resources.getString(R.string.export_prepare_failed)) }
                }
            },
        )
    }
}
