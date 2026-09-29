package com.spendobserver.data.local.dto

/** One row of [com.spendobserver.data.local.dao.RecordDao.observeStatsByTracker]'s `GROUP BY trackerId` aggregate. */
data class TrackerRecordStatsRow(
    val trackerId: Long,
    val recordCount: Int,
    val incomeCents: Long,
    val costCents: Long,
)
