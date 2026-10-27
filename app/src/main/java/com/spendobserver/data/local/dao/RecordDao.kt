package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendobserver.data.local.dto.TrackerRecordStatsRow
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.relation.RecordWithParts
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    /** Parts and all tag links go with it, via the foreign keys' CASCADE. */
    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): Record?

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getWithPartsById(id: Long): RecordWithParts?

    @Transaction
    @Query("SELECT * FROM records WHERE trackerId = :trackerId ORDER BY date ASC")
    fun observeAll(trackerId: Long): Flow<List<RecordWithParts>>

    /** One-shot read of [observeAll] — usable inside a `withTransaction` block, unlike a Flow. */
    @Transaction
    @Query("SELECT * FROM records WHERE trackerId = :trackerId ORDER BY date ASC")
    suspend fun getAll(trackerId: Long): List<RecordWithParts>

    /**
     * Record count + income/cost totals per tracker, across all dates — for
     * the tracker list and detail header. A tracker with zero records has no
     * row here. Amounts live on parts, so this joins them in; the join yields
     * one row per *part*, hence `COUNT(DISTINCT r.id)` to count records.
     */
    @Query(
        "SELECT r.trackerId AS trackerId, COUNT(DISTINCT r.id) AS recordCount, " +
            "COALESCE(SUM(CASE WHEN r.type = 'INCOME' THEN p.amountCents ELSE 0 END), 0) AS incomeCents, " +
            "COALESCE(SUM(CASE WHEN r.type = 'COST' THEN p.amountCents ELSE 0 END), 0) AS costCents " +
            "FROM records r JOIN record_parts p ON p.recordId = r.id " +
            "GROUP BY r.trackerId",
    )
    fun observeStatsByTracker(): Flow<List<TrackerRecordStatsRow>>
}
