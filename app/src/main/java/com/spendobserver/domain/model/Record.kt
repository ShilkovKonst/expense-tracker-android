package com.spendobserver.domain.model

import kotlinx.datetime.LocalDate

enum class RecordType {
    INCOME,
    COST,
}

/**
 * One real-world event, e.g. a single receipt, with its global [tags] and its
 * [parts] in the order they were entered (at least one).
 */
data class Record(
    val id: Long,
    val trackerId: Long,
    val date: LocalDate,
    val type: RecordType,
    val description: String?,
    val tags: List<Tag>,
    val parts: List<Part>,
) {
    /** The record's total: always derived, never stored, so it can't drift from the parts. */
    val amountCents: Long get() = parts.sumOf { it.amountCents }
}

/** A slice of a [Record]'s amount with its own local [tags], e.g. the alcohol on a grocery receipt. */
data class Part(val amountCents: Long, val tags: List<Tag>)

/** A record's own fields as they're saved; its tags and parts go beside them. [id] is 0 for a new record. */
data class RecordDraft(
    val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val type: RecordType,
    val description: String?,
)
