package com.spendobserver.data.repository

import com.spendobserver.data.local.toEntity
import com.spendobserver.data.local.toDomain
import com.spendobserver.data.local.entity.RecordPartEntity
import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.local.entity.TrackerEntity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.model.Tracker
import com.spendobserver.domain.calculator.MAX_AMOUNT_CENTS
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

        trackerId = db.trackerDao().insert(TrackerEntity(title = "Family", createdAt = 0, updatedAt = 0))
        shopId = db.tagDao().insert(TagEntity(trackerId = trackerId, name = "Ашан", isGlobal = true))
        groceriesId = db.tagDao().insert(TagEntity(trackerId = trackerId, name = "продукты"))
        alcoholId = db.tagDao().insert(TagEntity(trackerId = trackerId, name = "алко"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record() = RecordDraft(
        trackerId = trackerId,
        date = LocalDate(2026, 3, 15),
        type = RecordType.COST,
        description = null,
    )

    private fun Record.partSummary() = parts.map { p -> p.amountCents to p.tags.map { it.id }.toSet() }

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
        // Stored last position first, so reading in insertion order would come out reversed.
        val id = db.recordDao().insert(record().toEntity())
        for (position in 2 downTo 0) {
            db.recordPartDao().insert(RecordPartEntity(recordId = id, amountCents = position * 100L, position = position))
        }

        val loaded = repository.observeAll(trackerId).first().single()

        assertEquals(listOf(0L, 100L, 200L), loaded.parts.map { it.amountCents })
    }

    @Test
    fun updateRecord_thatFailsPartWay_leavesTheRecordAsItWas() = runBlocking<Unit> {
        val id = repository.insertRecord(record().copy(description = "old"), emptySet(), listOf(PartDraft(100, setOf(groceriesId))))
        val noSuchTag = 999_999L

        assertThrows(Exception::class.java) {
            runBlocking {
                repository.updateRecord(record().copy(id = id, description = "new"), emptySet(), listOf(PartDraft(200, setOf(noSuchTag))))
            }
        }

        val stored = repository.getRecord(id)!!
        assertEquals("old", stored.description)
        assertEquals(listOf(100L to setOf(groceriesId)), stored.partSummary())
    }

    @Test
    fun insertRecord_refusesAnAmountOverTheLimit() = runBlocking<Unit> {
        var refused = false
        try {
            repository.insertRecord(record(), emptySet(), listOf(PartDraft(MAX_AMOUNT_CENTS + 1, emptySet())))
        } catch (_: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
        assertTrue(repository.observeAll(trackerId).first().isEmpty())
    }

    @Test
    fun deleteTag_mergesPartsLeftWithTheSameTags_keepingTheTotal() = runBlocking {
        val merging = repository.insertRecord(
            record(),
            emptySet(),
            listOf(PartDraft(100, setOf(alcoholId)), PartDraft(200, setOf(groceriesId)), PartDraft(400, emptySet())),
        )
        val untouched = repository.insertRecord(record(), emptySet(), listOf(PartDraft(10, setOf(groceriesId)), PartDraft(20, emptySet())))

        repository.deleteTag(db.tagDao().getForTracker(trackerId).single { it.id == alcoholId }.toDomain())

        val merged = repository.getRecord(merging)!!
        assertEquals(listOf(500L to emptySet<Long>(), 200L to setOf(groceriesId)), merged.partSummary())
        assertEquals(700L, merged.amountCents)
        assertEquals(listOf(10L to setOf(groceriesId), 20L to emptySet<Long>()), repository.getRecord(untouched)!!.partSummary())
        assertTrue(db.tagDao().getForTracker(trackerId).none { it.id == alcoholId })
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
            RecordDraft(id = loaded.id, trackerId = loaded.trackerId, date = loaded.date, type = loaded.type, description = "edited"),
            tagIds = emptySet(),
            parts = listOf(PartDraft(1000, setOf(groceriesId, alcoholId))),
        )

        val updated = repository.getRecord(id)!!
        assertEquals("edited", updated.description)
        assertTrue(updated.tags.isEmpty())
        assertEquals(listOf(1000L to setOf(groceriesId, alcoholId)), updated.partSummary())
    }

    @Test
    fun deleteRecord_removesRecordAndCascadesToParts() = runBlocking {
        val id = repository.insertRecord(record(), setOf(shopId), listOf(PartDraft(1000, setOf(groceriesId))))

        repository.deleteRecord(id)

        assertNull(repository.getRecord(id))
        assertEquals(emptyList<RecordPartEntity>(), db.recordPartDao().getForRecord(id))
        assertTrue(db.recordTagDao().getForRecord(id).isEmpty())
    }

    @Test
    fun insertRecord_rejectsNoParts() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertRecord(record(), emptySet(), emptyList()) }
        }
    }

    @Test
    fun insertRecord_rejectsNegativePart() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertRecord(record(), emptySet(), listOf(PartDraft(1000, emptySet()), PartDraft(-1, emptySet()))) }
        }
    }

    @Test
    fun insertRecord_acceptsZeroAmount() = runBlocking {
        repository.insertRecord(record(), emptySet(), listOf(PartDraft(0, emptySet())))

        val stats = repository.observeStatsByTracker().first().getValue(trackerId)

        assertEquals(1, stats.recordCount)
        assertEquals(0L, stats.totals.costCents)
    }

    @Test
    fun observeStatsByTracker_countsAMultiPartRecordOnce() = runBlocking {
        repository.insertRecord(record(), emptySet(), listOf(PartDraft(2350, emptySet()), PartDraft(800, emptySet())))

        val stats = repository.observeStatsByTracker().first().getValue(trackerId)

        assertEquals(1, stats.recordCount)
        assertEquals(3150L, stats.totals.costCents)
    }
}
