package com.spendobserver.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var trackerDao: TrackerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        trackerDao = db.trackerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun observeAll_ordersByTitle() = runBlocking {
        trackerDao.insert(Tracker(title = "Work", createdAt = 0, updatedAt = 0))
        trackerDao.insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        trackerDao.insert(Tracker(title = "Personal", createdAt = 0, updatedAt = 0))

        val titles = trackerDao.observeAll().first().map { it.title }

        assertEquals(listOf("Family", "Personal", "Work"), titles)
    }

    @Test
    fun insert_duplicateTitle_fails() = runBlocking {
        trackerDao.insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))

        var threw = false
        try {
            trackerDao.insert(Tracker(title = "Family", createdAt = 1, updatedAt = 1))
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("Insert with a duplicate title should fail the unique constraint", threw)
    }
}
