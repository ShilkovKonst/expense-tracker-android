package com.spendobserver.testing

import com.spendobserver.domain.model.Part
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import kotlinx.datetime.LocalDate

/** A [Record] with one untagged part per entry of [partAmountsCents]. */
fun recordWithParts(
    id: Long = 0,
    date: LocalDate = LocalDate(2026, 3, 15),
    type: RecordType = RecordType.COST,
    vararg partAmountsCents: Long = longArrayOf(1000),
) = Record(
    id = id,
    trackerId = 1,
    date = date,
    type = type,
    description = null,
    tags = emptyList(),
    parts = partAmountsCents.map { Part(it, emptyList()) },
)

/** One part of [taggedRecord]: its amount and local tags. */
data class TestPart(val amountCents: Long, val tags: List<Tag> = emptyList())

/** A [Record] with global [tags] and tagged [parts] — for JVM tests of tag statistics. */
fun taggedRecord(
    id: Long,
    date: LocalDate = LocalDate(2026, 3, 15),
    type: RecordType = RecordType.COST,
    tags: List<Tag> = emptyList(),
    parts: List<TestPart>,
) = Record(
    id = id,
    trackerId = 1,
    date = date,
    type = type,
    description = null,
    tags = tags,
    parts = parts.map { Part(it.amountCents, it.tags) },
)
