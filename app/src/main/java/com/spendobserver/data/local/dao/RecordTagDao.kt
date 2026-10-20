package com.spendobserver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.spendobserver.data.local.entity.RecordTag

@Dao
interface RecordTagDao {

    @Insert
    suspend fun insert(recordTag: RecordTag)

    @Query("SELECT * FROM record_tags WHERE recordId = :recordId")
    suspend fun getForRecord(recordId: Long): List<RecordTag>

    @Query("DELETE FROM record_tags WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)
}
