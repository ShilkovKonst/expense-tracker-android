package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spendobserver.data.local.entity.RecordTagEntity

@Dao
interface RecordTagDao {

    @Insert
    suspend fun insert(recordTag: RecordTagEntity)

    @Query("SELECT * FROM record_tags WHERE recordId = :recordId")
    suspend fun getForRecord(recordId: Long): List<RecordTagEntity>

    @Query("DELETE FROM record_tags WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)
}
