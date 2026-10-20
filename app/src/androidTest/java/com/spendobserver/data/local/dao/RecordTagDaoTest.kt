package com.spendobserver.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordTagDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var recordTagDao: RecordTagDao
    private var trackerId: Long = 0
    private var recordId: Long = 0
    private var tagId: Long = 0

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        recordTagDao = db.recordTagDao()

        trackerId = db.trackerDao().insert(
            Tracker(title = "Family", createdAt = 0, updatedAt = 0),
        )
        recordId = db.recordDao().insert(
            Record(
                trackerId = trackerId,
                date = LocalDate(2026, 3, 15),
                type = RecordType.COST,
                description = null,
            ),
        )
        tagId = db.tagDao().insert(Tag(trackerId = trackerId, name = "groceries"))
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun deletingTag_cascadesToItsRecordTags() = runBlocking {
        recordTagDao.insert(RecordTag(recordId = recordId, tagId = tagId))
        val tag = db.tagDao().getByTrackerAndName(trackerId, "groceries")!!

        db.tagDao().delete(tag)

        assertTrue(recordTagDao.getForRecord(recordId).isEmpty())
    }
}
