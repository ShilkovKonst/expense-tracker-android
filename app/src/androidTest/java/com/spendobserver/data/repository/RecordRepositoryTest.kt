package com.spendobserver.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecordRepository
    private var trackerId: Long = 0
    private var tag1Id: Long = 0
    private var tag2Id: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = RecordRepository(db)

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        tag1Id = db.tagDao().insert(Tag(trackerId = trackerId, name = "groceries"))
        tag2Id = db.tagDao().insert(Tag(trackerId = trackerId, name = "rent"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(amountCents: Long = 1000) = Record(
        trackerId = trackerId,
        date = LocalDate(2026, 3, 15),
        type = RecordType.COST,
        description = null,
        amountCents = amountCents,
    )

    @Test
    fun insertRecord_createsRecordAndTagLinks() = runBlocking {
        val id = repository.insertRecord(record(), setOf(tag1Id, tag2Id))

        assertEquals(1000L, repository.getRecord(id)?.amountCents)
        assertEquals(setOf(tag1Id, tag2Id), repository.getTagIds(id).toSet())
    }

    @Test
    fun updateRecord_replacesTagLinks() = runBlocking {
        val id = repository.insertRecord(record(), setOf(tag1Id))

        repository.updateRecord(repository.getRecord(id)!!.copy(amountCents = 2000), setOf(tag2Id))

        assertEquals(2000L, repository.getRecord(id)?.amountCents)
        assertEquals(setOf(tag2Id), repository.getTagIds(id).toSet())
    }

    @Test
    fun updateRecord_withEmptyTagIds_clearsAllTags() = runBlocking {
        val id = repository.insertRecord(record(), setOf(tag1Id, tag2Id))

        repository.updateRecord(repository.getRecord(id)!!, emptySet())

        assertTrue(repository.getTagIds(id).isEmpty())
    }

    @Test
    fun deleteRecord_removesRecordAndItsTagLinks() = runBlocking {
        val id = repository.insertRecord(record(), setOf(tag1Id))

        repository.deleteRecord(repository.getRecord(id)!!)

        assertNull(repository.getRecord(id))
        assertTrue(repository.getTagIds(id).isEmpty())
    }
}
