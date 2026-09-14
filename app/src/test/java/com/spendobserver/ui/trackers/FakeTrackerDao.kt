package com.spendobserver.ui.trackers

import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [TrackerDao] fake so [TrackerListViewModel] can be unit-tested
 * on the JVM without Room/Context — mirrors the DAO's own
 * `ORDER BY title ASC` so tests see the same ordering the real query would
 * produce.
 */
class FakeTrackerDao : TrackerDao {
    private val trackers = MutableStateFlow<List<Tracker>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(tracker: Tracker): Long {
        val id = nextId++
        trackers.update { it + tracker.copy(id = id) }
        return id
    }

    override suspend fun update(tracker: Tracker) {
        trackers.update { list -> list.map { if (it.id == tracker.id) tracker else it } }
    }

    override suspend fun delete(tracker: Tracker) {
        trackers.update { list -> list.filterNot { it.id == tracker.id } }
    }

    override suspend fun getById(id: Long): Tracker? = trackers.value.find { it.id == id }

    override suspend fun getByTitle(title: String): Tracker? = trackers.value.find { it.title == title }

    override fun observeAll(): Flow<List<Tracker>> = trackers.map { list -> list.sortedBy { it.title } }
}
