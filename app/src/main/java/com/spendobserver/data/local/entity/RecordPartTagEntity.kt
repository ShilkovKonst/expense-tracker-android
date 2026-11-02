package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Links a [RecordPartEntity] to a local [TagEntity]; the composite key allows each tag once per part. */
@Entity(
    tableName = "record_part_tags",
    primaryKeys = ["partId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecordPartEntity::class,
            parentColumns = ["id"],
            childColumns = ["partId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class RecordPartTagEntity(
    val partId: Long,
    val tagId: Long,
)
