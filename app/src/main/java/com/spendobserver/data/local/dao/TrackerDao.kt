package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendobserver.data.local.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    @Insert
    suspend fun insert(tracker: TrackerEntity): Long

    @Update
    suspend fun update(tracker: TrackerEntity)

    @Delete
    suspend fun delete(tracker: TrackerEntity)

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun getById(id: Long): TrackerEntity?

    /** Ids that no longer exist are simply skipped. */
    @Query("UPDATE trackers SET backupAt = :at WHERE id IN (:ids)")
    suspend fun setBackupAt(ids: Collection<Long>, at: Long)

    /** Emits `null` once the tracker is deleted. */
    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observeById(id: Long): Flow<TrackerEntity?>

    @Query("SELECT * FROM trackers WHERE title = :title")
    suspend fun getByTitle(title: String): TrackerEntity?

    @Query("SELECT * FROM trackers ORDER BY title ASC")
    fun observeAll(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers")
    suspend fun getAll(): List<TrackerEntity>
}
