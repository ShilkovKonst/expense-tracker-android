package com.spendobserver.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.Tag

/**
 * A [Record] joined with its tags through [RecordTag], for screens that
 * need to show tag names alongside a record (the tracker-detail accordion
 * preview) without a manual per-record query. Room keeps this reactive:
 * a `Flow` built on the query below re-emits when `records`, `record_tags`,
 * or `tags` changes.
 */
data class RecordWithTags(
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
)
