package com.spendobserver.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var recordDao: RecordDao
    private var trackerId: Long = 0

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        recordDao = db.recordDao()
        trackerId = db.trackerDao().insert(
            Tracker(title = "Family", createdAt = 0, updatedAt = 0),
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun record(date: LocalDate, amountCents: Long = 1000) = Record(
        trackerId = trackerId,
        date = date,
        type = RecordType.COST,
        description = null,
        amountCents = amountCents,
    )

    @Test
    fun insertAndGetById_returnsSameRecord() = runBlocking {
        val id = recordDao.insert(record(LocalDate(2026, 3, 15)))

        val loaded = recordDao.getById(id)

        assertEquals(trackerId, loaded?.trackerId)
        assertEquals(LocalDate(2026, 3, 15), loaded?.date)
        assertEquals(1000L, loaded?.amountCents)
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = recordDao.insert(record(LocalDate(2026, 3, 15)))
        val original = recordDao.getById(id)!!

        recordDao.update(original.copy(amountCents = 2500))

        assertEquals(2500L, recordDao.getById(id)?.amountCents)
    }

    @Test
    fun delete_removesRecord() = runBlocking {
        val id = recordDao.insert(record(LocalDate(2026, 3, 15)))
        val inserted = recordDao.getById(id)!!

        recordDao.delete(inserted)

        assertNull(recordDao.getById(id))
    }

    @Test
    fun observeInRange_returnsOnlyRecordsWithinMonth_orderedByDate() = runBlocking {
        recordDao.insert(record(LocalDate(2026, 2, 28))) // до диапазона
        val inMarch1 = recordDao.insert(record(LocalDate(2026, 3, 1)))
        val inMarch2 = recordDao.insert(record(LocalDate(2026, 3, 20)))
        recordDao.insert(record(LocalDate(2026, 4, 1))) // после диапазона

        val march = recordDao.observeInRange(
            trackerId = trackerId,
            from = LocalDate(2026, 3, 1),
            to = LocalDate(2026, 4, 1),
        ).first()

        assertEquals(listOf(inMarch1, inMarch2), march.map { it.id })
    }

    @Test
    fun observeInRange_excludesOtherTrackers() = runBlocking {
        val otherTrackerId = db.trackerDao().insert(
            Tracker(title = "Work", createdAt = 0, updatedAt = 0),
        )
        recordDao.insert(record(LocalDate(2026, 3, 10)))
        db.recordDao().insert(
            Record(
                trackerId = otherTrackerId,
                date = LocalDate(2026, 3, 10),
                type = RecordType.COST,
                description = null,
                amountCents = 500,
            ),
        )

        val march = recordDao.observeInRange(
            trackerId = trackerId,
            from = LocalDate(2026, 3, 1),
            to = LocalDate(2026, 4, 1),
        ).first()

        assertEquals(1, march.size)
        assertEquals(trackerId, march.first().trackerId)
    }
}
