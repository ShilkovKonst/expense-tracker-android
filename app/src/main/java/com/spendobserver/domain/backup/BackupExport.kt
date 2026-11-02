package com.spendobserver.domain.backup

import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.model.Tracker
import com.spendobserver.domain.model.Record
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One tracker with everything that goes into its file entry. */
data class TrackerSnapshot(
    val tracker: Tracker,
    val tags: List<Tag>,
    val records: List<Record>,
)

/**
 * The file content for [snapshots]. Records come oldest first (same-day ones
 * in entry order), parts in their form order, tags global-first by name —
 * so exporting the same data twice gives the same file, diffable by eye.
 */
fun buildBackup(snapshots: List<TrackerSnapshot>, exportedAtMillis: Long, zone: TimeZone): BackupFile =
    BackupFile(
        format = BACKUP_FORMAT,
        version = BACKUP_VERSION,
        exportedAt = formatMillis(exportedAtMillis, zone),
        trackers = snapshots.map { it.toBackup(zone) },
    )

fun encodeBackup(file: BackupFile): String = BackupJson.encodeToString(file)

private fun TrackerSnapshot.toBackup(zone: TimeZone) = BackupTracker(
    title = tracker.title,
    createdAt = formatMillis(tracker.createdAt, zone),
    tags = tags
        .sortedWith(compareBy<Tag>({ !it.isGlobal }, { it.name.lowercase() }))
        .map { BackupTag(name = it.name, global = it.isGlobal) },
    records = records
        .sortedWith(compareBy({ it.date }, { it.id }))
        .map { record ->
            BackupRecord(
                date = record.date.toString(),
                type = when (record.type) {
                    RecordType.COST -> "cost"
                    RecordType.INCOME -> "income"
                },
                tags = record.tags.map { it.name }.sorted(),
                description = record.description,
                parts = record.parts
                    .map { part -> BackupPart(amount = part.amountCents, tags = part.tags.map { it.name }.sorted()) },
            )
        },
)

/** Whole seconds: a millisecond fraction in the file would only be noise. */
private fun formatMillis(millis: Long, zone: TimeZone): String {
    val dateTime = Instant.fromEpochMilliseconds(millis / 1000 * 1000).toLocalDateTime(zone)
    return LocalDateTime.Formats.ISO.format(dateTime)
}

/** The inverse for import: a file's local date-time back to epoch millis in [zone]. */
fun LocalDateTime.toEpochMillis(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()
