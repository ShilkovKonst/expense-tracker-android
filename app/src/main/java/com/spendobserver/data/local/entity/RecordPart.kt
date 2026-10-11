package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A slice of a [Record]'s amount with its own local tags (through
 * [RecordPartTag]) — e.g. the alcohol on a grocery receipt. [position]
 * keeps the parts in the order they were entered in the form.
 */
@Entity(
    tableName = "record_parts",
    foreignKeys = [
        ForeignKey(
            entity = Record::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
data class RecordPart(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordId: Long,
    val amountCents: Long,
    val position: Int,
)
