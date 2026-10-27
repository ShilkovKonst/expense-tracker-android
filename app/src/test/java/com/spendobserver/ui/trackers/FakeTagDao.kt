package com.spendobserver.ui.trackers

import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.dto.TagUsageRow
import com.spendobserver.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [TagDao] fake for [TrackerEditViewModel] JVM tests — mirrors the
 * DAO's `ORDER BY name ASC`. No records exist here, so usage is always empty.
 */
class FakeTagDao : TagDao {
    private val tags = MutableStateFlow<List<Tag>>(emptyList())
    private var nextId = 1L

    /** Every [update] call, so a test can tell "nothing written" from "same value written". */
    var updateCount = 0
        private set

    override suspend fun insert(tag: Tag): Long {
        val id = nextId++
        tags.update { it + tag.copy(id = id) }
        return id
    }

    override suspend fun update(tag: Tag) {
        updateCount++
        tags.update { list -> list.map { if (it.id == tag.id) tag else it } }
    }

    override suspend fun delete(tag: Tag) {
        tags.update { list -> list.filterNot { it.id == tag.id } }
    }

    override suspend fun getByTrackerAndName(trackerId: Long, name: String): Tag? =
        tags.value.find { it.trackerId == trackerId && it.name == name }

    override fun observeForTracker(trackerId: Long): Flow<List<Tag>> =
        tags.map { list -> list.filter { it.trackerId == trackerId }.sortedBy { it.name } }

    override suspend fun getForTracker(trackerId: Long): List<Tag> = tags.value.filter { it.trackerId == trackerId }

    override fun observeUsage(trackerId: Long): Flow<List<TagUsageRow>> = flowOf(emptyList())
}
