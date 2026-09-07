package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendobserver.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags WHERE trackerId = :trackerId AND name = :name")
    suspend fun getByTrackerAndName(trackerId: Long, name: String): Tag?

    @Query("SELECT * FROM tags WHERE trackerId = :trackerId ORDER BY name ASC")
    fun observeForTracker(trackerId: Long): Flow<List<Tag>>
}
