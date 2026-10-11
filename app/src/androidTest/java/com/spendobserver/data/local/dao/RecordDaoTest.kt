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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var recordDao: RecordDao
    private lateinit var recordPartDao: RecordPartDao
    private var trackerId: Long = 0

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        recordDao = db.recordDao()
        recordPartDao = db.recordPartDao()
        trackerId = db.trackerDao().insert(
            Tracker(title = "Family", createdAt = 0, updatedAt = 0),
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun record(date: LocalDate, type: RecordType = RecordType.COST, tracker: Long = trackerId) = Record(
        trackerId = tracker,
        date = date,
        type = type,
        description = null,
    )

    /** Inserts [record] plus one untagged part per amount, returning the record id. */
    private suspend fun insertWithParts(record: Record, vararg partAmountsCents: Long = longArrayOf(1000)): Long {
        val id = recordDao.insert(record)
        partAmountsCents.forEachIndexed { position, cents ->
            recordPartDao.insert(RecordPart(recordId = id, amountCents = cents, position = position))
        }
        return id
    }

    @Test
    fun insertAndGetById_returnsSameRecord() = runBlocking {
        val id = recordDao.insert(record(LocalDate(2026, 3, 15)).copy(description = "groceries"))

        val loaded = recordDao.getById(id)

        assertEquals(trackerId, loaded?.trackerId)
        assertEquals(LocalDate(2026, 3, 15), loaded?.date)
        assertEquals("groceries", loaded?.description)
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = recordDao.insert(record(LocalDate(2026, 3, 15)))
        val original = recordDao.getById(id)!!

        recordDao.update(original.copy(type = RecordType.INCOME))

        assertEquals(RecordType.INCOME, recordDao.getById(id)?.type)
    }

    @Test
    fun delete_removesRecordAndCascadesToItsParts() = runBlocking {
        val id = insertWithParts(record(LocalDate(2026, 3, 15)), 1000, 2000)
        val inserted = recordDao.getById(id)!!

        recordDao.delete(inserted)

        assertNull(recordDao.getById(id))
        assertEquals(emptyList<RecordPart>(), recordPartDao.getForRecord(id))
    }

    @Test
    fun observeInRange_returnsOnlyRecordsWithinMonth_orderedByDate() = runBlocking {
        insertWithParts(record(LocalDate(2026, 2, 28))) // до диапазона
        val inMarch1 = insertWithParts(record(LocalDate(2026, 3, 1)))
        val inMarch2 = insertWithParts(record(LocalDate(2026, 3, 20)))
        insertWithParts(record(LocalDate(2026, 4, 1))) // после диапазона

        val march = recordDao.observeInRange(
            trackerId = trackerId,
            from = LocalDate(2026, 3, 1),
            to = LocalDate(2026, 4, 1),
        ).first()

        assertEquals(listOf(inMarch1, inMarch2), march.map { it.record.id })
    }

    @Test
    fun observeInRange_excludesOtherTrackers() = runBlocking {
        val otherTrackerId = db.trackerDao().insert(
            Tracker(title = "Work", createdAt = 0, updatedAt = 0),
        )
        insertWithParts(record(LocalDate(2026, 3, 10)))
        insertWithParts(record(LocalDate(2026, 3, 10), tracker = otherTrackerId))

        val march = recordDao.observeInRange(
            trackerId = trackerId,
            from = LocalDate(2026, 3, 1),
            to = LocalDate(2026, 4, 1),
        ).first()

        assertEquals(1, march.size)
        assertEquals(trackerId, march.first().record.trackerId)
    }

    @Test
    fun observeAll_returnsEveryRecordForTrackerOrderedByDate() = runBlocking {
        val otherTrackerId = db.trackerDao().insert(
            Tracker(title = "Work", createdAt = 0, updatedAt = 0),
        )
        val jan = insertWithParts(record(LocalDate(2026, 1, 5)))
        val dec = insertWithParts(record(LocalDate(2025, 12, 20)))
        insertWithParts(record(LocalDate(2026, 1, 1), tracker = otherTrackerId))

        val all = recordDao.observeAll(trackerId).first()

        assertEquals(listOf(dec, jan), all.map { it.record.id })
    }

    @Test
    fun observeAll_loadsGlobalTagsAndEachPartWithItsOwnTags() = runBlocking {
        val shop = db.tagDao().insert(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true))
        val groceries = db.tagDao().insert(Tag(trackerId = trackerId, name = "продукты"))
        val alcohol = db.tagDao().insert(Tag(trackerId = trackerId, name = "алко"))
        val id = recordDao.insert(record(LocalDate(2026, 3, 1)))
        db.recordTagDao().insert(RecordTag(recordId = id, tagId = shop))
        val groceriesPart = recordPartDao.insert(RecordPart(recordId = id, amountCents = 2350, position = 0))
        recordPartDao.insertTag(RecordPartTag(partId = groceriesPart, tagId = groceries))
        val alcoholPart = recordPartDao.insert(RecordPart(recordId = id, amountCents = 800, position = 1))
        recordPartDao.insertTag(RecordPartTag(partId = alcoholPart, tagId = alcohol))

        val loaded = recordDao.observeAll(trackerId).first().single()

        assertEquals(listOf("Ашан"), loaded.tags.map { it.name })
        val partTags = loaded.parts.sortedBy { it.part.position }.map { p -> p.tags.map { it.name } }
        assertEquals(listOf(listOf("продукты"), listOf("алко")), partTags)
        assertEquals(3150L, loaded.amountCents)
    }

    @Test
    fun observeStatsByTracker_countsRecordsNotParts_andNetsIncomeAgainstCost() = runBlocking {
        val otherTrackerId = db.trackerDao().insert(Tracker(title = "Work", createdAt = 0, updatedAt = 0))
        insertWithParts(record(LocalDate(2026, 3, 1), type = RecordType.INCOME), 5000)
        // One receipt split into three parts — still a single record.
        insertWithParts(record(LocalDate(2026, 3, 2)), 1000, 600, 400)
        insertWithParts(record(LocalDate(2026, 3, 1), tracker = otherTrackerId), 700)

        val stats = recordDao.observeStatsByTracker().first().associateBy { it.trackerId }

        assertEquals(2, stats.getValue(trackerId).recordCount)
        assertEquals(5000L, stats.getValue(trackerId).incomeCents)
        assertEquals(2000L, stats.getValue(trackerId).costCents)
        assertEquals(1, stats.getValue(otherTrackerId).recordCount)
        assertEquals(700L, stats.getValue(otherTrackerId).costCents)
    }

    @Test
    fun observeStatsByTracker_trackerWithNoRecords_hasNoRow() = runBlocking {
        db.trackerDao().insert(Tracker(title = "Empty", createdAt = 0, updatedAt = 0))

        val stats = recordDao.observeStatsByTracker().first()

        assertEquals(emptyList<Any>(), stats)
    }
}
