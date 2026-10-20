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
import com.spendobserver.domain.backup.BackupParseResult
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.ImportedPart
import com.spendobserver.domain.backup.ImportedRecord
import com.spendobserver.domain.backup.ImportedTag
import com.spendobserver.domain.backup.ImportedTracker
import com.spendobserver.domain.backup.buildBackup
import com.spendobserver.domain.backup.encodeBackup
import com.spendobserver.domain.backup.parseBackup
import com.spendobserver.domain.backup.toEpochMillis
import com.spendobserver.domain.record.PartDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val now = 1_800_000_000_000L
    private lateinit var db: AppDatabase
    private lateinit var records: RecordRepository
    private lateinit var repository: BackupRepository

    /** The Ашан receipt: 10.00 алко + 5.00 продукты, plus an untagged income. */
    private val homeFile = ImportedTracker(
        title = "Дом",
        createdAt = LocalDateTime(2025, 12, 1, 2, 5, 41),
        tags = listOf(ImportedTag("Ашан", true), ImportedTag("алко", false), ImportedTag("продукты", false)),
        records = listOf(
            ImportedRecord(
                date = LocalDate(2025, 10, 2),
                type = RecordType.COST,
                tagNames = listOf("Ашан"),
                description = "чек",
                parts = listOf(ImportedPart(1000, listOf("алко")), ImportedPart(500, listOf("продукты"))),
            ),
            ImportedRecord(LocalDate(2025, 10, 3), RecordType.INCOME, emptyList(), null, listOf(ImportedPart(2700, emptyList()))),
        ),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        records = RecordRepository(db)
        repository = BackupRepository(db, records, now = { now }, zone = { TimeZone.UTC })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun trackers() = db.trackerDao().getAll().sortedBy { it.title }

    @Test
    fun import_createsTrackerTagsRecordsAndParts() = runBlocking {
        val id = repository.import(listOf(ImportItem(homeFile))).single()

        val tracker = db.trackerDao().getById(id)!!
        assertEquals("Дом", tracker.title)
        assertEquals(LocalDateTime(2025, 12, 1, 2, 5, 41).toEpochMillis(TimeZone.UTC), tracker.createdAt)
        assertEquals(now, tracker.updatedAt)
        assertNull(tracker.backupAt)

        val tags = db.tagDao().getForTracker(id).associate { it.name to it.isGlobal }
        assertEquals(mapOf("Ашан" to true, "алко" to false, "продукты" to false), tags)

        val stored = records.observeAll(id).first().sortedBy { it.record.date }
        assertEquals(2, stored.size)
        val receipt = stored[0]
        assertEquals(listOf("Ашан"), receipt.tags.map { it.name })
        assertEquals("чек", receipt.record.description)
        assertEquals(listOf(1000L, 500L), receipt.parts.map { it.part.amountCents })
        assertEquals(listOf(listOf("алко"), listOf("продукты")), receipt.parts.map { p -> p.tags.map { it.name } })
        assertEquals(RecordType.INCOME, stored[1].record.type)
    }

    @Test
    fun clash_withoutResolution_throws_andWritesNothing() = runBlocking {
        db.trackerDao().insert(Tracker(title = "дом", createdAt = 0, updatedAt = 0))
        val other = homeFile.copy(title = "Работа")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.import(listOf(ImportItem(other), ImportItem(homeFile))) }
        }

        // "Работа" came first and was inserted, but the whole transaction rolled back.
        assertEquals(listOf("дом"), trackers().map { it.title })
    }

    @Test
    fun replace_deletesTheOldTrackerWithItsRecords() = runBlocking {
        val oldId = db.trackerDao().insert(Tracker(title = "ДОМ", createdAt = 0, updatedAt = 0))
        records.insertRecord(
            Record(trackerId = oldId, date = LocalDate(2020, 1, 1), type = RecordType.COST, description = null),
            emptySet(),
            listOf(PartDraft(1, emptySet())),
        )

        val newId = repository.import(listOf(ImportItem(homeFile, ClashResolution.REPLACE))).single()

        assertEquals(listOf("Дом"), trackers().map { it.title })
        assertEquals(emptyList<Any>(), records.observeAll(oldId).first())
        assertEquals(2, records.observeAll(newId).first().size)
    }

    @Test
    fun copy_importsBesideUnderAFreeName() = runBlocking {
        db.trackerDao().insert(Tracker(title = "Дом", createdAt = 0, updatedAt = 0))
        db.trackerDao().insert(Tracker(title = "Дом (2)", createdAt = 0, updatedAt = 0))

        repository.import(listOf(ImportItem(homeFile, ClashResolution.COPY)))

        assertEquals(listOf("Дом", "Дом (2)", "Дом (3)"), trackers().map { it.title })
    }

    @Test
    fun copy_skipsTitlesTheFileItselfBrings() = runBlocking {
        db.trackerDao().insert(Tracker(title = "Дом", createdAt = 0, updatedAt = 0))
        // "Дом (2)" isn't in the database, so it needs no resolution — but the copy mustn't take its name.
        val home2 = homeFile.copy(title = "Дом (2)")

        repository.import(listOf(ImportItem(homeFile, ClashResolution.COPY), ImportItem(home2)))

        assertEquals(listOf("Дом", "Дом (2)", "Дом (3)"), trackers().map { it.title })
    }

    @Test
    fun exportThenImport_asCopy_reproducesTheTracker() = runBlocking {
        val id = repository.import(listOf(ImportItem(homeFile))).single()
        db.tagDao().insert(Tag(trackerId = id, name = "кино")) // unused tags travel too

        val json = encodeBackup(buildBackup(listOf(repository.snapshot(id)!!), now, TimeZone.UTC))
        val parsed = (parseBackup(json) as BackupParseResult.Success).trackers.single()
        val copyId = repository.import(listOf(ImportItem(parsed, ClashResolution.COPY))).single()

        assertEquals("Дом (2)", db.trackerDao().getById(copyId)!!.title)
        assertEquals(4, db.tagDao().getForTracker(copyId).size)
        val original = records.observeAll(id).first().map { r -> r.record.date to r.parts.map { it.part.amountCents } }
        val copy = records.observeAll(copyId).first().map { r -> r.record.date to r.parts.map { it.part.amountCents } }
        assertEquals(original.sortedBy { it.first }, copy.sortedBy { it.first })
    }

    @Test
    fun markBackedUp_setsBackupAt() = runBlocking {
        val id = repository.import(listOf(ImportItem(homeFile))).single()

        repository.markBackedUp(listOf(id))

        assertEquals(now, db.trackerDao().getById(id)!!.backupAt)
    }

    @Test
    fun snapshot_ofMissingTracker_isNull() = runBlocking {
        assertNull(repository.snapshot(42))
    }
}
