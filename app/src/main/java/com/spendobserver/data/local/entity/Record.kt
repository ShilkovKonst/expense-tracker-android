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

/**
 * One real-world event — e.g. a single receipt. Its amount isn't stored:
 * it's always the sum of its [RecordPart]s (at least one per record), so
 * the total can never drift from the parts. Tags attached directly to a
 * record (through [RecordTag]) are global ones only ([Tag.isGlobal]).
 */
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
)
