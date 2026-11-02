package com.spendobserver.domain.model

data class Tracker(
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val backupAt: Long? = null,
)
