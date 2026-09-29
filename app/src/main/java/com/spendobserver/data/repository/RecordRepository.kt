package com.spendobserver.data.repository

import androidx.room.withTransaction
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.RecordTagDao
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RecordRepository(
    private val db: AppDatabase,
    private val recordDao: RecordDao = db.recordDao(),
    private val recordTagDao: RecordTagDao = db.recordTagDao(),
) {

    fun observeInRange(trackerId: Long, from: LocalDate, to: LocalDate): Flow<List<Record>> =
        recordDao.observeInRange(trackerId, from, to)

    fun observeAll(trackerId: Long): Flow<List<Record>> = recordDao.observeAll(trackerId)

    /** Tag names for every record of [trackerId], keyed by record id — for previews that show tags without a separate query per row. */
    fun observeTagNamesByRecord(trackerId: Long): Flow<Map<Long, List<String>>> =
        recordDao.observeAllWithTags(trackerId).map { list ->
            list.associate { it.record.id to it.tags.map(Tag::name) }
        }

    suspend fun getRecord(id: Long): Record? = recordDao.getById(id)

    suspend fun getTagIds(recordId: Long): List<Long> =
        recordTagDao.getForRecord(recordId).map { it.tagId }

    suspend fun insertRecord(record: Record, tagIds: Set<Long>): Long = db.withTransaction {
        val id = recordDao.insert(record)
        tagIds.forEach { tagId -> recordTagDao.insert(RecordTag(recordId = id, tagId = tagId)) }
        id
    }

    suspend fun updateRecord(record: Record, tagIds: Set<Long>) = db.withTransaction {
        recordDao.update(record)
        recordTagDao.getForRecord(record.id).forEach { recordTagDao.delete(it) }
        tagIds.forEach { tagId -> recordTagDao.insert(RecordTag(recordId = record.id, tagId = tagId)) }
    }

    suspend fun deleteRecord(record: Record) = recordDao.delete(record)
}
