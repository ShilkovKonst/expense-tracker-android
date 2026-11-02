package com.spendobserver.ui.trackers

import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.TrackerEntity
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
    private val trackers = MutableStateFlow<List<TrackerEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(tracker: TrackerEntity): Long {
        val id = nextId++
        trackers.update { it + tracker.copy(id = id) }
        return id
    }

    override suspend fun update(tracker: TrackerEntity) {
        trackers.update { list -> list.map { if (it.id == tracker.id) tracker else it } }
    }

    override suspend fun delete(tracker: TrackerEntity) {
        trackers.update { list -> list.filterNot { it.id == tracker.id } }
    }

    override suspend fun getById(id: Long): TrackerEntity? = trackers.value.find { it.id == id }

    override suspend fun setBackupAt(ids: Collection<Long>, at: Long) {
        trackers.update { list -> list.map { if (it.id in ids) it.copy(backupAt = at) else it } }
    }

    override fun observeById(id: Long): Flow<TrackerEntity?> = trackers.map { list -> list.find { it.id == id } }

    override suspend fun getByTitle(title: String): TrackerEntity? = trackers.value.find { it.title == title }

    override suspend fun getAll(): List<TrackerEntity> = trackers.value

    override fun observeAll(): Flow<List<TrackerEntity>> = trackers.map { list -> list.sortedBy { it.title } }
}
