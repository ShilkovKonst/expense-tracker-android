package com.spendobserver.data.repository

import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

class TagRepository(private val tagDao: TagDao) {

    fun observeTags(trackerId: Long): Flow<List<Tag>> = tagDao.observeForTracker(trackerId)

    suspend fun findByName(trackerId: Long, name: String): Tag? =
        tagDao.getByTrackerAndName(trackerId, name)

    suspend fun insertTag(tag: Tag): Long = tagDao.insert(tag)

    suspend fun updateTag(tag: Tag) = tagDao.update(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag)
}
