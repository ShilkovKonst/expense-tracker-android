package com.spendobserver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trackers",
    indices = [Index("title", unique = true)],
)
data class Tracker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val backupAt: Long? = null,
)
