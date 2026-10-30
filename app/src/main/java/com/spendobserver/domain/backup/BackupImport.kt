package com.spendobserver.domain.backup

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.domain.text.isSameNameAs
import com.spendobserver.domain.text.trimInvisible
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** A tracker from a file that passed [validateBackup]: typed, trimmed, consistent. */
data class ImportedTracker(
    val title: String,
    val createdAt: LocalDateTime,
    val tags: List<ImportedTag>,
    val records: List<ImportedRecord>,
)

data class ImportedTag(val name: String, val isGlobal: Boolean)

data class ImportedRecord(
    val date: LocalDate,
    val type: RecordType,
    /** Names as declared in [ImportedTracker.tags] (their spelling, not the reference's). */
    val tagNames: List<String>,
    val description: String?,
    val parts: List<ImportedPart>,
)

data class ImportedPart(val amountCents: Long, val tagNames: List<String>)

/** Either the trackers to import, or why the file can't be imported — never both. */
sealed interface BackupParseResult {
    data class Success(val trackers: List<ImportedTracker>) : BackupParseResult
    data class Failure(val error: BackupError) : BackupParseResult
}

/**
 * Why a file can't be imported — data, not text: the UI words it in the
 * app's language (see `ui/backup/BackupErrorText.kt`).
 */
sealed interface BackupError {
    /** Not JSON, or a required field is missing or of the wrong type. */
    data object Damaged : BackupError
    data object NotSpendObserver : BackupError
    data object NewerVersion : BackupError
    data object NoTrackers : BackupError
    data class DuplicateTracker(val title: String) : BackupError
    data class Invalid(val where: BackupLocation, val issue: BackupIssue) : BackupError
}

/**
 * Where in the file a problem is: a tracker by its title ([trackerNumber],
 * 1-based, when the title is blank), then a record by its date as the file
 * writes it, then one of that record's parts (1-based).
 */
data class BackupLocation(
    val trackerTitle: String?,
    val trackerNumber: Int,
    val recordDate: String? = null,
    val partNumber: Int? = null,
)

sealed interface BackupIssue {
    data object EmptyTitle : BackupIssue
    data class BadCreatedAt(val value: String) : BackupIssue
    data object EmptyTagName : BackupIssue
    data class DuplicateTag(val name: String) : BackupIssue
    data class BadRecordDate(val value: String) : BackupIssue
    data class UnknownType(val value: String) : BackupIssue
    data class UnknownTag(val name: String) : BackupIssue
    /** A local tag among the record's own tags. */
    data class TagNotGlobal(val name: String) : BackupIssue
    /** A global tag on one of the record's parts. */
    data class TagIsGlobal(val name: String) : BackupIssue
    data object SeveralGlobalTags : BackupIssue
    data object NoParts : BackupIssue
    data object NonPositiveAmount : BackupIssue
    data object DuplicatePartTags : BackupIssue
}

/** Parses and validates a whole file; the first problem found is the [BackupParseResult.Failure]. */
fun parseBackup(json: String): BackupParseResult {
    val file = try {
        BackupJson.decodeFromString<BackupFile>(json)
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON, a missing field, a wrong type) is one of these.
        return BackupParseResult.Failure(BackupError.Damaged)
    }
    return validateBackup(file)
}

/** Thrown inside validation to stop at the first problem; never escapes [validateBackup]. */
private class InvalidBackup(val error: BackupError) : Exception()

private fun fail(error: BackupError): Nothing = throw InvalidBackup(error)

private fun fail(where: BackupLocation, issue: BackupIssue): Nothing = fail(BackupError.Invalid(where, issue))

/**
 * Checks everything the database and the app's own forms would insist on,
 * so an import can't store what the app could never have produced:
 * names non-blank and unique ignoring case, global tags only on records and
 * local ones only on parts, at most one global tag and at least one part per
 * record, positive amounts,
 * no two parts of one record with the same tags.
 */
fun validateBackup(file: BackupFile): BackupParseResult = try {
    if (file.format != BACKUP_FORMAT) fail(BackupError.NotSpendObserver)
    if (file.version > BACKUP_VERSION) fail(BackupError.NewerVersion)
    if (file.trackers.isEmpty()) fail(BackupError.NoTrackers)

    val trackers = file.trackers.mapIndexed { index, tracker -> validateTracker(tracker, index) }
    trackers.forEachIndexed { i, tracker ->
        if (trackers.take(i).any { it.title.isSameNameAs(tracker.title) }) {
            fail(BackupError.DuplicateTracker(tracker.title))
        }
    }
    BackupParseResult.Success(trackers)
} catch (e: InvalidBackup) {
    BackupParseResult.Failure(e.error)
}

private fun validateTracker(tracker: BackupTracker, index: Int): ImportedTracker {
    val title = tracker.title.trimInvisible()
    val where = BackupLocation(trackerTitle = title.ifEmpty { null }, trackerNumber = index + 1)
    if (title.isEmpty()) fail(where, BackupIssue.EmptyTitle)

    val createdAt = parseOrNull { LocalDateTime.parse(tracker.createdAt) }
        ?: fail(where, BackupIssue.BadCreatedAt(tracker.createdAt))

    val tags = tracker.tags.map { tag ->
        val name = tag.name.trimInvisible()
        if (name.isEmpty()) fail(where, BackupIssue.EmptyTagName)
        ImportedTag(name, tag.global)
    }
    tags.forEachIndexed { i, tag ->
        if (tags.take(i).any { it.name.isSameNameAs(tag.name) }) fail(where, BackupIssue.DuplicateTag(tag.name))
    }

    val records = tracker.records.map { record -> validateRecord(record, where, tags) }
    return ImportedTracker(title, createdAt, tags, records)
}

private fun validateRecord(record: BackupRecord, trackerWhere: BackupLocation, tags: List<ImportedTag>): ImportedRecord {
    val date = parseOrNull { LocalDate.parse(record.date) }
        ?: fail(trackerWhere, BackupIssue.BadRecordDate(record.date))
    val where = trackerWhere.copy(recordDate = record.date)

    val type = when (record.type) {
        "cost" -> RecordType.COST
        "income" -> RecordType.INCOME
        else -> fail(where, BackupIssue.UnknownType(record.type))
    }

    /** Resolves references to declared tags of one level; a repeated reference counts once. */
    fun resolve(names: List<String>, global: Boolean, owner: BackupLocation): List<String> =
        names.map { ref ->
            val tag = tags.firstOrNull { it.name.isSameNameAs(ref) } ?: fail(owner, BackupIssue.UnknownTag(ref))
            if (tag.isGlobal != global) {
                fail(owner, if (global) BackupIssue.TagNotGlobal(tag.name) else BackupIssue.TagIsGlobal(tag.name))
            }
            tag.name
        }.distinct()

    val recordTags = resolve(record.tags, global = true, owner = where)
    if (recordTags.size > 1) fail(where, BackupIssue.SeveralGlobalTags)
    if (record.parts.isEmpty()) fail(where, BackupIssue.NoParts)
    val parts = record.parts.mapIndexed { i, part ->
        val partWhere = where.copy(partNumber = i + 1)
        if (part.amount <= 0) fail(partWhere, BackupIssue.NonPositiveAmount)
        ImportedPart(part.amount, resolve(part.tags, global = false, owner = partWhere))
    }
    parts.forEachIndexed { i, part ->
        if (parts.take(i).any { it.tagNames.toSet() == part.tagNames.toSet() }) {
            fail(where, BackupIssue.DuplicatePartTags)
        }
    }

    return ImportedRecord(
        date = date,
        type = type,
        tagNames = recordTags,
        description = record.description?.trimInvisible()?.ifEmpty { null },
        parts = parts,
    )
}

private inline fun <T> parseOrNull(parse: () -> T): T? = try {
    parse()
} catch (e: IllegalArgumentException) {
    null
}
