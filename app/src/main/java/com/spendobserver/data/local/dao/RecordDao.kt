package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendobserver.data.local.entity.Record
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
}
