package com.spendobserver.data.repository

import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepository(private val tagDao: TagDao) {

    fun observeTags(trackerId: Long): Flow<List<Tag>> = tagDao.observeForTracker(trackerId)

    suspend fun findByName(trackerId: Long, name: String): Tag? =
        tagDao.getByTrackerAndName(trackerId, name)

    suspend fun insertTag(tag: Tag): Long = tagDao.insert(tag)

    suspend fun updateTag(tag: Tag) = tagDao.update(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag)

    /** Records using each tag, keyed by tag id. */
    fun observeUsage(trackerId: Long): Flow<Map<Long, Int>> =
        tagDao.observeUsage(trackerId).map { rows -> rows.associate { it.tagId to it.recordCount } }

    /** `false` when the tag is in use — its level can only change while no record carries it. */
    suspend fun setGlobal(tag: Tag, isGlobal: Boolean): Boolean = tagDao.setGlobalIfUnused(tag.id, isGlobal) == 1
}
