package com.spendobserver.data.repository

import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.flow.Flow

/**
 * Records with their parts and tags. An interface so JVM tests of the
 * ViewModels can pass a fake; the app uses [RoomRecordRepository], built by
 * the `RecordRepository(db)` factory function.
 */
interface RecordRepository {

    fun observeAll(trackerId: Long): Flow<List<Record>>

    suspend fun getAll(trackerId: Long): List<Record>

    /** Record count + net totals per tracker, across all dates — trackers with no records are simply absent from the map. */
    fun observeStatsByTracker(): Flow<Map<Long, TrackerStats>>

    suspend fun getRecord(id: Long): Record?

    /**
     * Inserts [record] with its global [tagIds] and its [parts], all or
     * nothing, and returns the new id.
     *
     * @throws IllegalArgumentException if [parts] is empty or an amount is out of range.
     */
    suspend fun insertRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>): Long

    /**
     * Replaces the record's fields, global tags, and all of its parts.
     *
     * @throws IllegalArgumentException on the same [parts] as [insertRecord].
     */
    suspend fun updateRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>)

    /** Its parts and tag links go with it. */
    suspend fun deleteRecord(id: Long)

    /** Deletes [tag] and merges the parts this leaves with identical tags: amounts and record totals stay as they were. */
    suspend fun deleteTag(tag: Tag)
}
