package com.spendobserver.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordPartTag
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.Tag

/** A [RecordPart] with its local tags. */
data class PartWithTags(
    @Embedded val part: RecordPart,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordPartTag::class,
            parentColumn = "partId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<Tag>,
)

/**
 * A [Record] with its global tags and its parts (each with local tags) —
 * everything a screen needs to show or edit one record. Nested
 * [Relation]s make Room load the whole tree in one transaction, and a
 * `Flow` of these re-emits when any of the five underlying tables change.
 *
 * Room doesn't order [Relation] lists, so [parts] arrive in arbitrary
 * order; [RecordRepository][com.spendobserver.data.repository.RecordRepository]
 * sorts them by [RecordPart.position] before handing them out.
 */
data class RecordWithParts(
    @Embedded val record: Record,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordTag::class,
            parentColumn = "recordId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<Tag>,
    @Relation(
        entity = RecordPart::class,
        parentColumn = "id",
        entityColumn = "recordId",
    )
    val parts: List<PartWithTags>,
) {
    /** The record's total — always derived, never stored. */
    val amountCents: Long get() = parts.sumOf { it.part.amountCents }
}
