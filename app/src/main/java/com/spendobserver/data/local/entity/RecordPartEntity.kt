package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A row of `record_parts`. [position] keeps the parts in the order they were entered in the form. */
@Entity(
    tableName = "record_parts",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
data class RecordPartEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordId: Long,
    val amountCents: Long,
    val position: Int,
)
