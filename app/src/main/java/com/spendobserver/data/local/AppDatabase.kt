package com.spendobserver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.RecordTagDao
import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker

@Database(
    entities = [Tracker::class, Tag::class, Record::class, RecordTag::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackerDao(): TrackerDao

    abstract fun tagDao(): TagDao

    abstract fun recordDao(): RecordDao

    abstract fun recordTagDao(): RecordTagDao

    companion object {
        private const val DATABASE_NAME = "spendobserver.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
