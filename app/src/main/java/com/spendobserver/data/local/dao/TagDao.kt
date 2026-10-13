package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.spendobserver.data.local.dto.TagUsageRow
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

    @Query("SELECT * FROM tags WHERE trackerId = :trackerId")
    suspend fun getForTracker(trackerId: Long): List<Tag>

    /**
     * How many records use each of [trackerId]'s tags — directly (a global
     * tag) or through any of their parts (a local tag, counted once per
     * record even when several parts carry it). Re-emits whenever tags or
     * either link table change.
     */
    @Query(
        "SELECT t.id AS tagId, " +
            "(SELECT COUNT(*) FROM record_tags rt WHERE rt.tagId = t.id) + " +
            "(SELECT COUNT(DISTINCT p.recordId) FROM record_part_tags pt " +
            "JOIN record_parts p ON p.id = pt.partId WHERE pt.tagId = t.id) AS recordCount " +
            "FROM tags t WHERE t.trackerId = :trackerId",
    )
    fun observeUsage(trackerId: Long): Flow<List<TagUsageRow>>
}
