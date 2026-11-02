package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendobserver.domain.model.RecordType
import kotlinx.datetime.LocalDate

/** A row of `records`; see [com.spendobserver.domain.model.Record] for what a record is. */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackerId", "date")],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val type: RecordType,
    val description: String?,
)
