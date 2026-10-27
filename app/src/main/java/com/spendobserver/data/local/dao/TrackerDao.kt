package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    @Insert
    suspend fun insert(tracker: Tracker): Long

    @Update
    suspend fun update(tracker: Tracker)

    @Delete
    suspend fun delete(tracker: Tracker)

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun getById(id: Long): Tracker?

    /** Ids that no longer exist are simply skipped. */
    @Query("UPDATE trackers SET backupAt = :at WHERE id IN (:ids)")
    suspend fun setBackupAt(ids: Collection<Long>, at: Long)

    /** Emits `null` once the tracker is deleted. */
    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observeById(id: Long): Flow<Tracker?>

    @Query("SELECT * FROM trackers WHERE title = :title")
    suspend fun getByTitle(title: String): Tracker?

    @Query("SELECT * FROM trackers ORDER BY title ASC")
    fun observeAll(): Flow<List<Tracker>>

    @Query("SELECT * FROM trackers")
    suspend fun getAll(): List<Tracker>
}
