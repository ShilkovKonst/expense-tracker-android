package com.spendobserver.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordPartTag
import com.spendobserver.data.local.entity.RecordTag
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var tagDao: TagDao
    private var trackerId: Long = 0

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tagDao = db.tagDao()
        trackerId = db.trackerDao().insert(
            Tracker(title = "Family", createdAt = 0, updatedAt = 0),
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun observeForTracker_ordersByNameAndExcludesOtherTrackers() = runBlocking {
        val otherTrackerId = db.trackerDao().insert(
            Tracker(title = "Work", createdAt = 0, updatedAt = 0),
        )
        tagDao.insert(Tag(trackerId = trackerId, name = "rent"))
        tagDao.insert(Tag(trackerId = trackerId, name = "groceries"))
        tagDao.insert(Tag(trackerId = otherTrackerId, name = "office"))

        val names = tagDao.observeForTracker(trackerId).first().map { it.name }

        assertEquals(listOf("groceries", "rent"), names)
    }

    @Test
    fun deletingTracker_cascadesToItsTags() = runBlocking {
        tagDao.insert(Tag(trackerId = trackerId, name = "groceries"))
        val tracker = db.trackerDao().getById(trackerId)!!

        db.trackerDao().delete(tracker)

        assertTrue(tagDao.observeForTracker(trackerId).first().isEmpty())
    }

    /** A record using [globalTagIds] directly and one part per entry of [partTagIds]. */
    private suspend fun insertRecord(globalTagIds: List<Long> = emptyList(), vararg partTagIds: List<Long>): Long {
        val recordId = db.recordDao().insert(
            Record(trackerId = trackerId, date = LocalDate(2026, 3, 1), type = RecordType.COST, description = null),
        )
        globalTagIds.forEach { db.recordTagDao().insert(RecordTag(recordId = recordId, tagId = it)) }
        partTagIds.forEachIndexed { position, tagIds ->
            val partId = db.recordPartDao().insert(RecordPart(recordId = recordId, amountCents = 100, position = position))
            tagIds.forEach { db.recordPartDao().insertTag(RecordPartTag(partId = partId, tagId = it)) }
        }
        return recordId
    }

    @Test
    fun observeUsage_countsRecordsOnBothLevels_onceEvenAcrossSeveralParts() = runBlocking {
        val shop = tagDao.insert(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true))
        val alcohol = tagDao.insert(Tag(trackerId = trackerId, name = "алко"))
        val unused = tagDao.insert(Tag(trackerId = trackerId, name = "снеки"))
        insertRecord(listOf(shop), listOf(alcohol), listOf(alcohol)) // alcohol on two parts of one record
        insertRecord(listOf(shop), listOf(alcohol))

        val usage = tagDao.observeUsage(trackerId).first().associate { it.tagId to it.recordCount }

        assertEquals(mapOf(shop to 2, alcohol to 2, unused to 0), usage)
    }
}
