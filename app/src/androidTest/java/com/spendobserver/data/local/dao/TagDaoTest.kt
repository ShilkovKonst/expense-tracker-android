package com.spendobserver.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun insertAndGetByTrackerAndName_returnsSameTag() = runBlocking {
        tagDao.insert(Tag(trackerId = trackerId, name = "groceries"))

        val loaded = tagDao.getByTrackerAndName(trackerId, "groceries")

        assertEquals("groceries", loaded?.name)
        assertEquals(trackerId, loaded?.trackerId)
    }

    @Test
    fun getByTrackerAndName_returnsNullWhenAbsent() = runBlocking {
        assertNull(tagDao.getByTrackerAndName(trackerId, "nonexistent"))
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = tagDao.insert(Tag(trackerId = trackerId, name = "groceries"))

        tagDao.update(Tag(id = id, trackerId = trackerId, name = "groceries-renamed"))

        val loaded = tagDao.getByTrackerAndName(trackerId, "groceries-renamed")
        assertEquals(id, loaded?.id)
    }

    @Test
    fun delete_removesTag() = runBlocking {
        tagDao.insert(Tag(trackerId = trackerId, name = "groceries"))
        val inserted = tagDao.getByTrackerAndName(trackerId, "groceries")!!

        tagDao.delete(inserted)

        assertNull(tagDao.getByTrackerAndName(trackerId, "groceries"))
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
}
