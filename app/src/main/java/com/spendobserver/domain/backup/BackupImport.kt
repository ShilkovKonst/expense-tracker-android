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
    data class Failure(val message: String) : BackupParseResult
}

/** Parses and validates a whole file; the first problem found is the [BackupParseResult.Failure]. */
fun parseBackup(json: String): BackupParseResult {
    val file = try {
        BackupJson.decodeFromString<BackupFile>(json)
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON, a missing field, a wrong type) is one of these.
        return BackupParseResult.Failure("Файл повреждён или это не файл SpendObserver")
    }
    return validateBackup(file)
}

/** Thrown inside validation to stop at the first problem; never escapes [validateBackup]. */
private class InvalidBackup(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw InvalidBackup(message)

/**
 * Checks everything the database and the app's own forms would insist on,
 * so an import can't store what the app could never have produced:
 * names non-blank and unique ignoring case, global tags only on records and
 * local ones only on parts, at least one part per record, positive amounts,
 * no two parts of one record with the same tags.
 */
fun validateBackup(file: BackupFile): BackupParseResult = try {
    if (file.format != BACKUP_FORMAT) fail("Это не файл SpendObserver")
    if (file.version > BACKUP_VERSION) fail("Файл создан более новой версией приложения — обновите приложение")
    if (file.trackers.isEmpty()) fail("В файле нет трекеров")

    val trackers = file.trackers.mapIndexed { index, tracker -> validateTracker(tracker, index) }
    trackers.forEachIndexed { i, tracker ->
        if (trackers.take(i).any { it.title.isSameNameAs(tracker.title) }) {
            fail("Трекер «${tracker.title}» встречается в файле дважды")
        }
    }
    BackupParseResult.Success(trackers)
} catch (e: InvalidBackup) {
    BackupParseResult.Failure(e.message!!)
}

private fun validateTracker(tracker: BackupTracker, index: Int): ImportedTracker {
    val title = tracker.title.trimInvisible()
    if (title.isEmpty()) fail("Трекер №${index + 1}: пустое название")
    val where = "Трекер «$title»"

    val createdAt = parseOrNull { LocalDateTime.parse(tracker.createdAt) }
        ?: fail("$where: неверная дата создания «${tracker.createdAt}»")

    val tags = tracker.tags.map { tag ->
        val name = tag.name.trimInvisible()
        if (name.isEmpty()) fail("$where: тег с пустым названием")
        ImportedTag(name, tag.global)
    }
    tags.forEachIndexed { i, tag ->
        if (tags.take(i).any { it.name.isSameNameAs(tag.name) }) fail("$where: тег «${tag.name}» объявлен дважды")
    }

    val records = tracker.records.map { record -> validateRecord(record, where, tags) }
    return ImportedTracker(title, createdAt, tags, records)
}

private fun validateRecord(record: BackupRecord, trackerWhere: String, tags: List<ImportedTag>): ImportedRecord {
    val date = parseOrNull { LocalDate.parse(record.date) }
        ?: fail("$trackerWhere: неверная дата записи «${record.date}»")
    val where = "$trackerWhere, запись от ${record.date}"

    val type = when (record.type) {
        "cost" -> RecordType.COST
        "income" -> RecordType.INCOME
        else -> fail("$where: неизвестный тип «${record.type}»")
    }

    /** Resolves references to declared tags of one level; a repeated reference counts once. */
    fun resolve(names: List<String>, global: Boolean, owner: String): List<String> =
        names.map { ref ->
            val tag = tags.firstOrNull { it.name.isSameNameAs(ref) } ?: fail("$owner: неизвестный тег «$ref»")
            if (tag.isGlobal != global) {
                fail(
                    if (global) "$owner: тег «${tag.name}» не общий — он ставится на части записи"
                    else "$owner: тег «${tag.name}» общий — он ставится на запись, а не на её часть",
                )
            }
            tag.name
        }.distinct()

    val recordTags = resolve(record.tags, global = true, owner = where)
    if (record.parts.isEmpty()) fail("$where: нет ни одной части")
    val parts = record.parts.mapIndexed { i, part ->
        val partWhere = "$where, часть ${i + 1}"
        if (part.amount <= 0) fail("$partWhere: сумма должна быть больше нуля")
        ImportedPart(part.amount, resolve(part.tags, global = false, owner = partWhere))
    }
    parts.forEachIndexed { i, part ->
        if (parts.take(i).any { it.tagNames.toSet() == part.tagNames.toSet() }) {
            fail("$where: две части с одинаковыми тегами")
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
