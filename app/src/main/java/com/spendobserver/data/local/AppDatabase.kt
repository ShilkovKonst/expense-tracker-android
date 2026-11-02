package com.spendobserver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.RecordPartDao
import com.spendobserver.data.local.dao.RecordTagDao
import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.dao.TrackerDao
import com.spendobserver.data.local.entity.RecordEntity
import com.spendobserver.data.local.entity.RecordPartEntity
import com.spendobserver.data.local.entity.RecordPartTagEntity
import com.spendobserver.data.local.entity.RecordTagEntity
import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.local.entity.TrackerEntity

@Database(
    entities = [TrackerEntity::class, TagEntity::class, RecordEntity::class, RecordTagEntity::class, RecordPartEntity::class, RecordPartTagEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackerDao(): TrackerDao

    abstract fun tagDao(): TagDao

    abstract fun recordDao(): RecordDao

    abstract fun recordTagDao(): RecordTagDao

    abstract fun recordPartDao(): RecordPartDao

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
                ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
            }
    }
}
