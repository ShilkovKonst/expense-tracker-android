package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Links a [RecordPart] to a local [Tag]; the composite key allows each tag once per part. */
@Entity(
    tableName = "record_part_tags",
    primaryKeys = ["partId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecordPart::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class RecordPartTag(
    val partId: Long,
    val tagId: Long,
)
