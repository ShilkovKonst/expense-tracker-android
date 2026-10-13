package com.spendobserver.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.domain.backup.buildBackup
import com.spendobserver.domain.backup.encodeBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.io.File
import kotlin.time.Clock

/** Which trackers an export covers. */
sealed interface ExportScope {
    data class One(val trackerId: Long, val title: String) : ExportScope
    data object All : ExportScope
}

/** `домашний_2026-10-13.json`, or `spendobserver_2026-10-13.json` for all trackers. */
fun exportFileName(scope: ExportScope): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val base = when (scope) {
        is ExportScope.One -> scope.title.replace(UnsafeFileNameChars, "_")
        ExportScope.All -> "spendobserver"
    }
    return "${base}_$today.json"
}

/** Characters at least one common file system refuses in a name. */
private val UnsafeFileNameChars = Regex("""[\\/:*?"<>|]""")

/**
 * Turns trackers into a file: written where the user picked (Storage Access
 * Framework) or into the cache for the share sheet. Either way, the
 * exported trackers get their `backupAt` stamped.
 */
class BackupExporter(
    private val context: Context,
    private val backupRepository: BackupRepository,
) {
    /** Writes the export into [uri] (from `ActivityResultContracts.CreateDocument`). */
    suspend fun saveTo(uri: Uri, scope: ExportScope) {
        val (json, ids) = export(scope)
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri for writing")
            stream.use { it.write(json.toByteArray()) }
        }
        backupRepository.markBackedUp(ids)
    }

    /**
     * A ready-to-start share-sheet intent for the export. Android doesn't
     * report whether the user then actually sent it, so `backupAt` is
     * stamped as soon as the file is handed over.
     */
    suspend fun shareIntent(scope: ExportScope): Intent {
        val (json, ids) = export(scope)
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            // One file at a time: drop earlier exports so the cache doesn't pile up.
            dir.listFiles()?.forEach { it.delete() }
            File(dir, exportFileName(scope)).apply { writeText(json) }
        }
        backupRepository.markBackedUp(ids)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    private suspend fun export(scope: ExportScope): Pair<String, List<Long>> {
        val snapshots = when (scope) {
            is ExportScope.One -> listOfNotNull(backupRepository.snapshot(scope.trackerId))
            ExportScope.All -> backupRepository.snapshotAll()
        }
        check(snapshots.isNotEmpty()) { "Nothing to export" }
        val json = withContext(Dispatchers.Default) {
            encodeBackup(buildBackup(snapshots, System.currentTimeMillis(), TimeZone.currentSystemDefault()))
        }
        return json to snapshots.map { it.tracker.id }
    }
}
