package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

enum class RecordType {
    INCOME,
    COST,
}

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Tracker::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackerId", "date")],
)
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val type: RecordType,
    val description: String?,
    val amountCents: Long,
)
