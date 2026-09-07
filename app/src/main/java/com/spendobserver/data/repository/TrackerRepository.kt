package com.spendobserver.data.repository

import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.Flow

class TrackerRepository(private val trackerDao: TrackerDao) {

    fun observeTrackers(): Flow<List<Tracker>> = trackerDao.observeAll()

    suspend fun getTracker(id: Long): Tracker? = trackerDao.getById(id)

    suspend fun findByTitle(title: String): Tracker? = trackerDao.getByTitle(title)

    suspend fun insertTracker(tracker: Tracker): Long = trackerDao.insert(tracker)

    suspend fun updateTracker(tracker: Tracker) = trackerDao.update(tracker)

    suspend fun deleteTracker(tracker: Tracker) = trackerDao.delete(tracker)
}
