package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendobserver.data.local.dto.TrackerRecordStatsRow
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.relation.RecordWithTags
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): Record?

    @Query(
        "SELECT * FROM records " +
            "WHERE trackerId = :trackerId AND date >= :from AND date < :to " +
            "ORDER BY date ASC",
    )
    fun observeInRange(trackerId: Long, from: LocalDate, to: LocalDate): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE trackerId = :trackerId ORDER BY date ASC")
    fun observeAll(trackerId: Long): Flow<List<Record>>

    @Transaction
    @Query("SELECT * FROM records WHERE trackerId = :trackerId ORDER BY date ASC")
    fun observeAllWithTags(trackerId: Long): Flow<List<RecordWithTags>>

    /** Record count + income/cost totals per tracker, across all dates — for the tracker list and detail header. A tracker with zero records has no row here. */
    @Query(
        "SELECT trackerId, COUNT(*) AS recordCount, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents, " +
            "COALESCE(SUM(CASE WHEN type = 'COST' THEN amountCents ELSE 0 END), 0) AS costCents " +
            "FROM records GROUP BY trackerId",
    )
    fun observeStatsByTracker(): Flow<List<TrackerRecordStatsRow>>
}
