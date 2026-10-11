package com.spendobserver.data.repository

import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.Flow

class TrackerRepository(private val trackerDao: TrackerDao) {

    fun observeTrackers(): Flow<List<Tracker>> = trackerDao.observeAll()

    suspend fun getTracker(id: Long): Tracker? = trackerDao.getById(id)

    fun observeTracker(id: Long): Flow<Tracker?> = trackerDao.observeById(id)

    suspend fun findByTitle(title: String): Tracker? = trackerDao.getByTitle(title)

    /**
     * Whether another tracker already has [title] (titles are unique). Pass
     * [exceptTrackerId] when renaming, so a tracker keeping its own name
     * doesn't count as a clash with itself.
     */
    suspend fun isTitleTaken(title: String, exceptTrackerId: Long? = null): Boolean =
        trackerDao.getByTitle(title)?.let { it.id != exceptTrackerId } ?: false

    suspend fun insertTracker(tracker: Tracker): Long = trackerDao.insert(tracker)

    suspend fun updateTracker(tracker: Tracker) = trackerDao.update(tracker)

    suspend fun deleteTracker(tracker: Tracker) = trackerDao.delete(tracker)
}
