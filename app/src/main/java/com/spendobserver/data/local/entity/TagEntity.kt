package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A row of `tags`; see [com.spendobserver.domain.model.Tag] for what a tag is. */
@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackerId")],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Long,
    val name: String,
    val isGlobal: Boolean = false,
)
