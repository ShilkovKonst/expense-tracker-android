package com.spendobserver.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordPart
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.domain.record.PartDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecordRepository
    private var trackerId: Long = 0
    private var shopId: Long = 0
    private var groceriesId: Long = 0
    private var alcoholId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = RecordRepository(db)

        trackerId = db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
        shopId = db.tagDao().insert(Tag(trackerId = trackerId, name = "Ашан", isGlobal = true))
        groceriesId = db.tagDao().insert(Tag(trackerId = trackerId, name = "продукты"))
        alcoholId = db.tagDao().insert(Tag(trackerId = trackerId, name = "алко"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record() = Record(
        trackerId = trackerId,
        date = LocalDate(2026, 3, 15),
        type = RecordType.COST,
        description = null,
    )

    private fun RecordWithParts.partSummary() = parts.map { p -> p.part.amountCents to p.tags.map { it.id }.toSet() }

    @Test
    fun insertRecord_storesGlobalTagsAndPartsInOrder() = runBlocking {
        val id = repository.insertRecord(
            record(),
            tagIds = setOf(shopId),
            parts = listOf(PartDraft(2350, setOf(groceriesId)), PartDraft(800, setOf(alcoholId))),
        )

        val loaded = repository.getRecord(id)!!
        assertEquals(listOf(shopId), loaded.tags.map { it.id })
        assertEquals(listOf(2350L to setOf(groceriesId), 800L to setOf(alcoholId)), loaded.partSummary())
        assertEquals(3150L, loaded.amountCents)
    }

    @Test
    fun observeAll_returnsPartsSortedByPosition() = runBlocking {
        // Enough parts that Room's unordered relation load would likely scramble them.
        val amounts = (1L..8L).map { it * 100 }
        repository.insertRecord(record(), emptySet(), amounts.map { PartDraft(it, emptySet()) })

        val loaded = repository.observeAll(trackerId).first().single()

        assertEquals(amounts, loaded.parts.map { it.part.amountCents })
    }

    @Test
    fun updateRecord_replacesGlobalTagsAndAllParts() = runBlocking {
        val id = repository.insertRecord(
            record(),
            tagIds = setOf(shopId),
            parts = listOf(PartDraft(2350, setOf(groceriesId)), PartDraft(800, setOf(alcoholId))),
        )
        val loaded = repository.getRecord(id)!!

        repository.updateRecord(
            loaded.record.copy(description = "edited"),
            tagIds = emptySet(),
            parts = listOf(PartDraft(1000, setOf(groceriesId, alcoholId))),
        )

        val updated = repository.getRecord(id)!!
        assertEquals("edited", updated.record.description)
        assertTrue(updated.tags.isEmpty())
        assertEquals(listOf(1000L to setOf(groceriesId, alcoholId)), updated.partSummary())
    }

    @Test
    fun deleteRecord_removesRecordAndCascadesToParts() = runBlocking {
        val id = repository.insertRecord(record(), setOf(shopId), listOf(PartDraft(1000, setOf(groceriesId))))

        repository.deleteRecord(id)

        assertNull(repository.getRecord(id))
        assertEquals(emptyList<RecordPart>(), db.recordPartDao().getForRecord(id))
        assertTrue(db.recordTagDao().getForRecord(id).isEmpty())
    }

    @Test
    fun insertRecord_rejectsNoParts() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertRecord(record(), emptySet(), emptyList()) }
        }
    }

    @Test
    fun insertRecord_rejectsNonPositivePart() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertRecord(record(), emptySet(), listOf(PartDraft(1000, emptySet()), PartDraft(0, emptySet()))) }
        }
    }

    @Test
    fun observeStatsByTracker_countsAMultiPartRecordOnce() = runBlocking {
        repository.insertRecord(record(), emptySet(), listOf(PartDraft(2350, emptySet()), PartDraft(800, emptySet())))

        val stats = repository.observeStatsByTracker().first().getValue(trackerId)

        assertEquals(1, stats.recordCount)
        assertEquals(3150L, stats.totals.costCents)
    }
}
