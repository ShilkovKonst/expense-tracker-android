package com.spendobserver.domain.meta

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

private fun Int.pad2(): String = toString().padStart(2, '0')

/**
 * Formats a date/time as the `dd/MM/yyyy_HH:mm:ss` string used for
 * `meta.createdAt`/`updatedAt`/`backupAt` in the tracker file format —
 * matches the web app's `formatDatetoMeta` byte-for-byte (day-first, with
 * seconds), which the README's own example gets wrong.
 */
fun formatDateToMeta(dateTime: LocalDateTime): String {
    val dd = dateTime.day.pad2()
    val mm = dateTime.month.number.pad2()
    val hh = dateTime.hour.pad2()
    val min = dateTime.minute.pad2()
    val sec = dateTime.second.pad2()
    return "$dd/$mm/${dateTime.year}_$hh:$min:$sec"
}

/**
 * Parses a `dd/MM/yyyy_HH:mm:ss` meta date string back into a [LocalDateTime].
 * Returns `null` for `null`/empty input; throws [IllegalArgumentException]
 * for any other malformed value (mirrors the web app throwing on `"invalid"`).
 */
fun parseMetaToDate(value: String?): LocalDateTime? {
    if (value.isNullOrEmpty()) return null

    val parts = value.split("_")
    val dateNums = parts.getOrNull(0)?.split("/")?.mapNotNull { it.toIntOrNull() }
    val timeNums = parts.getOrNull(1)?.split(":")?.mapNotNull { it.toIntOrNull() }
    require(parts.size == 2 && dateNums?.size == 3 && timeNums?.size == 3) {
        "Invalid meta date format: $value"
    }

    val (dd, mm, yyyy) = dateNums
    val (hh, min, sec) = timeNums
    return LocalDateTime(yyyy, mm, dd, hh, min, sec)
}
