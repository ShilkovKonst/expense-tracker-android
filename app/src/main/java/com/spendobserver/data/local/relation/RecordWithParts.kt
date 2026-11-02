package com.spendobserver.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.spendobserver.data.local.entity.RecordEntity
import com.spendobserver.data.local.entity.RecordPartEntity
import com.spendobserver.data.local.entity.RecordPartTagEntity
import com.spendobserver.data.local.entity.RecordTagEntity
import com.spendobserver.data.local.entity.TagEntity

/** A [RecordPartEntity] with its local tags. */
data class PartWithTags(
    @Embedded val part: RecordPartEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordPartTagEntity::class,
            parentColumn = "partId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

/**
 * A [RecordEntity] with its global tags and its parts (each with local tags) —
 * what [com.spendobserver.domain.model.Record] is built from. Nested
 * [Relation]s make Room load the whole tree in one transaction, and a
 * `Flow` of these re-emits when any of the five underlying tables change.
 *
 * Room doesn't order [Relation] lists, so [parts] arrive in arbitrary
 * order; `toDomain()` sorts them by [RecordPartEntity.position].
 */
data class RecordWithParts(
    @Embedded val record: RecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordTagEntity::class,
            parentColumn = "recordId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
    @Relation(
        entity = RecordPartEntity::class,
        parentColumn = "id",
        entityColumn = "recordId",
    )
    val parts: List<PartWithTags>,
)
