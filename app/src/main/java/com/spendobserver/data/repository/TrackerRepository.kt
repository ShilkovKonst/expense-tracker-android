package com.spendobserver.data.repository

import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.domain.text.isSameNameAs
import kotlinx.coroutines.flow.Flow

class TrackerRepository(private val trackerDao: TrackerDao) {

    fun observeTrackers(): Flow<List<Tracker>> = trackerDao.observeAll()

    suspend fun getTracker(id: Long): Tracker? = trackerDao.getById(id)

    fun observeTracker(id: Long): Flow<Tracker?> = trackerDao.observeById(id)

    /**
     * Whether another tracker already has [title] by [isSameNameAs] — so
     * also ignoring case and invisible edges, which the database's unique
     * index can't. Pass [exceptTrackerId] when renaming, so a tracker
     * keeping its own name (or just recasing it) doesn't clash with itself.
     */
    suspend fun isTitleTaken(title: String, exceptTrackerId: Long? = null): Boolean =
        trackerDao.getAll().any { it.id != exceptTrackerId && it.title.isSameNameAs(title) }

    suspend fun insertTracker(tracker: Tracker): Long = trackerDao.insert(tracker)

    suspend fun updateTracker(tracker: Tracker) = trackerDao.update(tracker)

    suspend fun deleteTracker(tracker: Tracker) = trackerDao.delete(tracker)
}
