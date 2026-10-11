package com.spendobserver.testing

import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordType
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
