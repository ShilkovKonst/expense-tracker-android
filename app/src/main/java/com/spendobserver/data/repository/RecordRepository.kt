package com.spendobserver.data.repository

import androidx.room.withTransaction
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.RecordPartDao
import com.spendobserver.data.local.dao.RecordTagDao
import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.entity.RecordPartEntity
import com.spendobserver.data.local.entity.RecordPartTagEntity
import com.spendobserver.data.local.entity.RecordTagEntity
import com.spendobserver.data.local.toDomain
import com.spendobserver.data.local.toEntity
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.calculator.MAX_AMOUNT_CENTS
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.record.mergeSameTags
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordRepository(
    private val db: AppDatabase,
    private val recordDao: RecordDao = db.recordDao(),
    private val recordTagDao: RecordTagDao = db.recordTagDao(),
    private val recordPartDao: RecordPartDao = db.recordPartDao(),
    private val tagDao: TagDao = db.tagDao(),
) {

    fun observeAll(trackerId: Long): Flow<List<Record>> =
        recordDao.observeAll(trackerId).map { list -> list.map { it.toDomain() } }

    suspend fun getAll(trackerId: Long): List<Record> =
        recordDao.getAll(trackerId).map { it.toDomain() }

    /** Record count + net totals per tracker, across all dates — trackers with no records are simply absent from the map. */
    fun observeStatsByTracker(): Flow<Map<Long, TrackerStats>> =
        recordDao.observeStatsByTracker().map { rows ->
            rows.associate { it.trackerId to TrackerStats(it.recordCount, AmountTotals(it.incomeCents, it.costCents)) }
        }

    suspend fun getRecord(id: Long): Record? = recordDao.getWithPartsById(id)?.toDomain()

    /**
     * Inserts [record] with its global [tagIds] and its [parts] in one
     * transaction. Which tags are global vs local isn't re-checked here —
     * the form only offers each tag on its own level.
     */
    suspend fun insertRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>): Long {
        requireValidParts(parts)
        return db.withTransaction {
            val id = recordDao.insert(record.toEntity())
            insertTagsAndParts(id, tagIds, parts)
            id
        }
    }

    /** Replaces the record's fields, global tags, and all of its parts. */
    suspend fun updateRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>) {
        requireValidParts(parts)
        db.withTransaction {
            recordDao.update(record.toEntity())
            recordTagDao.deleteForRecord(record.id)
            recordPartDao.deleteForRecord(record.id)
            insertTagsAndParts(record.id, tagIds, parts)
        }
    }

    /** Parts and all tag links go with it, via the foreign keys' CASCADE. */
    suspend fun deleteRecord(id: Long) = recordDao.deleteById(id)

    /**
     * Deletes [tag] and merges the parts this leaves with identical tags, in
     * one transaction: amounts and record totals stay as they were.
     */
    suspend fun deleteTag(tag: Tag) {
        db.withTransaction {
            val recordIds = recordPartDao.getRecordIdsWithTag(tag.id)
            tagDao.delete(tag.toEntity())
            for (recordId in recordIds) {
                val saved = recordDao.getWithPartsById(recordId)?.toDomain() ?: continue
                val parts = saved.parts.map { p -> PartDraft(p.amountCents, p.tags.mapTo(HashSet()) { it.id }) }
                val merged = parts.mergeSameTags()
                if (merged.size != parts.size) {
                    recordPartDao.deleteForRecord(recordId)
                    insertParts(recordId, merged)
                }
            }
        }
    }

    private suspend fun insertTagsAndParts(recordId: Long, tagIds: Set<Long>, parts: List<PartDraft>) {
        tagIds.forEach { tagId -> recordTagDao.insert(RecordTagEntity(recordId = recordId, tagId = tagId)) }
        insertParts(recordId, parts)
    }

    private suspend fun insertParts(recordId: Long, parts: List<PartDraft>) {
        parts.forEachIndexed { position, draft ->
            val partId = recordPartDao.insert(
                RecordPartEntity(recordId = recordId, amountCents = draft.amountCents, position = position),
            )
            draft.tagIds.forEach { tagId -> recordPartDao.insertTag(RecordPartTagEntity(partId = partId, tagId = tagId)) }
        }
    }

    private fun requireValidParts(parts: List<PartDraft>) {
        require(parts.isNotEmpty()) { "A record needs at least one part" }
        require(parts.all { it.amountCents in 0..MAX_AMOUNT_CENTS }) { "A part's amount is out of range" }
    }
}
