package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordPartTag

/**
 * Parts and their tags are only ever written together with their record
 * (see [RecordRepository][com.spendobserver.data.repository.RecordRepository]),
 * and read through [RecordWithParts][com.spendobserver.data.local.relation.RecordWithParts],
 * so one DAO covers both tables.
 */
@Dao
interface RecordPartDao {

    @Insert
    suspend fun insert(part: RecordPart): Long

    @Insert
    suspend fun insertTag(partTag: RecordPartTag)

    /** Also removes the parts' tag links, via the foreign key's CASCADE. */
    @Query("DELETE FROM record_parts WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)

    @Query("SELECT * FROM record_parts WHERE recordId = :recordId ORDER BY position ASC")
    suspend fun getForRecord(recordId: Long): List<RecordPart>
}
