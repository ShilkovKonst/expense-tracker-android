package com.spendobserver.data.repository

import androidx.room.withTransaction
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.RecordPartDao
import com.spendobserver.data.local.dao.RecordTagDao
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordPartTag
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordRepository(
    private val db: AppDatabase,
    private val recordDao: RecordDao = db.recordDao(),
    private val recordTagDao: RecordTagDao = db.recordTagDao(),
    private val recordPartDao: RecordPartDao = db.recordPartDao(),
) {

    fun observeAll(trackerId: Long): Flow<List<RecordWithParts>> =
        recordDao.observeAll(trackerId).map { list -> list.map { it.withSortedParts() } }

    suspend fun getAll(trackerId: Long): List<RecordWithParts> =
        recordDao.getAll(trackerId).map { it.withSortedParts() }

    /** Record count + net totals per tracker, across all dates — trackers with no records are simply absent from the map. */
    fun observeStatsByTracker(): Flow<Map<Long, TrackerStats>> =
        recordDao.observeStatsByTracker().map { rows ->
            rows.associate { it.trackerId to TrackerStats(it.recordCount, AmountTotals(it.incomeCents, it.costCents)) }
        }

    suspend fun getRecord(id: Long): RecordWithParts? = recordDao.getWithPartsById(id)?.withSortedParts()

    /**
     * Inserts [record] with its global [tagIds] and its [parts] in one
     * transaction. Which tags are global vs local isn't re-checked here —
     * the form only offers each tag on its own level.
     */
    suspend fun insertRecord(record: Record, tagIds: Set<Long>, parts: List<PartDraft>): Long {
        requireValidParts(parts)
        return db.withTransaction {
            val id = recordDao.insert(record)
            insertTagsAndParts(id, tagIds, parts)
            id
        }
    }

    /** Replaces the record's fields, global tags, and all of its parts. */
    suspend fun updateRecord(record: Record, tagIds: Set<Long>, parts: List<PartDraft>) {
        requireValidParts(parts)
        db.withTransaction {
            recordDao.update(record)
            recordTagDao.deleteForRecord(record.id)
            recordPartDao.deleteForRecord(record.id)
            insertTagsAndParts(record.id, tagIds, parts)
        }
    }

    /** Parts and all tag links go with it, via the foreign keys' CASCADE. */
    suspend fun deleteRecord(record: Record) = recordDao.delete(record)

    private suspend fun insertTagsAndParts(recordId: Long, tagIds: Set<Long>, parts: List<PartDraft>) {
        tagIds.forEach { tagId -> recordTagDao.insert(RecordTag(recordId = recordId, tagId = tagId)) }
        parts.forEachIndexed { position, draft ->
            val partId = recordPartDao.insert(
                RecordPart(recordId = recordId, amountCents = draft.amountCents, position = position),
            )
            draft.tagIds.forEach { tagId -> recordPartDao.insertTag(RecordPartTag(partId = partId, tagId = tagId)) }
        }
    }

    private fun requireValidParts(parts: List<PartDraft>) {
        require(parts.isNotEmpty()) { "A record needs at least one part" }
        require(parts.all { it.amountCents > 0 }) { "Every part's amount must be positive" }
    }
}

private fun RecordWithParts.withSortedParts() = copy(parts = parts.sortedBy { it.part.position })
