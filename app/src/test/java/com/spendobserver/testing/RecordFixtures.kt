package com.spendobserver.testing

import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.PartWithTags
import com.spendobserver.data.local.relation.RecordWithParts
import kotlinx.datetime.LocalDate

/** A [RecordWithParts] with one untagged part per entry of [partAmountsCents] — for JVM tests that never touch Room. */
fun recordWithParts(
    id: Long = 0,
    date: LocalDate = LocalDate(2026, 3, 15),
    type: RecordType = RecordType.COST,
    vararg partAmountsCents: Long = longArrayOf(1000),
) = RecordWithParts(
    record = Record(id = id, trackerId = 1, date = date, type = type, description = null),
    tags = emptyList(),
    parts = partAmountsCents.mapIndexed { position, cents ->
        PartWithTags(RecordPart(recordId = id, amountCents = cents, position = position), tags = emptyList())
    },
)

/** One part of [taggedRecord]: its amount and local tags. */
data class TestPart(val amountCents: Long, val tags: List<Tag> = emptyList())

/** A [RecordWithParts] with global [tags] and tagged [parts] — for JVM tests of tag statistics. */
fun taggedRecord(
    id: Long,
    date: LocalDate = LocalDate(2026, 3, 15),
    type: RecordType = RecordType.COST,
    tags: List<Tag> = emptyList(),
    parts: List<TestPart>,
) = RecordWithParts(
    record = Record(id = id, trackerId = 1, date = date, type = type, description = null),
    tags = tags,
    parts = parts.mapIndexed { position, part ->
        PartWithTags(RecordPart(recordId = id, amountCents = part.amountCents, position = position), part.tags)
    },
)
