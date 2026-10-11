package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [isGlobal] fixes which level a tag lives on: global tags ("где/с кем" —
 * a shop, a trip) go on the record itself via [RecordTag]; local tags
 * ("на что" — groceries, alcohol) go on its parts via [RecordPartTag].
 * Never both, so per-tag statistics stay unambiguous.
 */
@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = Tracker::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackerId")],
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Long,
    val name: String,
    val isGlobal: Boolean = false,
)
