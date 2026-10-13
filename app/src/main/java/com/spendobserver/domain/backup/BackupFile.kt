package com.spendobserver.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_FORMAT = "spendobserver"

/** The newest format version this app writes and reads. */
const val BACKUP_VERSION = 1

/**
 * The export/import file, field for field (README "Формат файла экспорта").
 *
 * These classes mirror the JSON as it comes, untrusted: dates, the record
 * type and tag references stay plain strings, so a bad value doesn't fail
 * deep inside the JSON parser with a cryptic message — [validateBackup]
 * checks them one by one and says exactly where the problem is.
 */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val trackers: List<BackupTracker>,
)

@Serializable
data class BackupTracker(
    val title: String,
    /** Local date-time, ISO 8601 (`2025-12-01T02:05:41`). */
    val createdAt: String,
    val tags: List<BackupTag> = emptyList(),
    val records: List<BackupRecord> = emptyList(),
)

@Serializable
data class BackupTag(
    val name: String,
    val global: Boolean,
)

@Serializable
data class BackupRecord(
    /** ISO 8601 date (`2025-10-02`). */
    val date: String,
    /** `"cost"` or `"income"`. */
    val type: String,
    /** Names of global tags. */
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val parts: List<BackupPart>,
)

@Serializable
data class BackupPart(
    /** Cents, always positive. */
    val amount: Long,
    /** Names of local tags. */
    val tags: List<String> = emptyList(),
)

/**
 * Pretty-printed for a human peeking into the file; unknown keys are
 * ignored, so a file from a newer app version with extra fields still
 * imports as long as everything this version needs is there.
 */
val BackupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
