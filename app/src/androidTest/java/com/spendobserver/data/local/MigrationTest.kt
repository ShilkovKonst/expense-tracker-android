package com.spendobserver.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendobserver.domain.model.RecordType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test.db"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * The path a real install takes: a database built from the 1.json snapshot
     * is opened by Room with [ALL_MIGRATIONS]. Rows go in as raw SQL because the
     * entities follow the current schema, while this test stays on version 1.
     */
    @Test
    fun dataWrittenWithTheVersion1Schema_opensInTheCurrentDatabase() = runBlocking {
        val date = LocalDate(2026, 3, 15)
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO trackers (id, title, createdAt, updatedAt, backupAt) VALUES (1, 'Family', 10, 20, NULL)")
            db.execSQL("INSERT INTO tags (id, trackerId, name, isGlobal) VALUES (1, 1, 'shop', 1), (2, 1, 'milk', 0)")
            db.execSQL("INSERT INTO records (id, trackerId, date, type, description) VALUES (1, 1, ${date.toEpochDays()}, 'COST', 'receipt')")
            db.execSQL("INSERT INTO record_tags (recordId, tagId) VALUES (1, 1)")
            db.execSQL("INSERT INTO record_parts (id, recordId, amountCents, position) VALUES (1, 1, 1250, 0), (2, 1, 300, 1)")
            db.execSQL("INSERT INTO record_part_tags (partId, tagId) VALUES (1, 2)")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val record = room.recordDao().getAll(1).single()
            assertEquals(date, record.record.date)
            assertEquals(RecordType.COST, record.record.type)
            assertEquals("receipt", record.record.description)
            assertEquals(listOf("shop"), record.tags.map { it.name })
            assertEquals(1550L, record.parts.sumOf { it.part.amountCents })
            val parts = record.parts.sortedBy { it.part.position }
            assertEquals(listOf("milk"), parts[0].tags.map { it.name })
            assertEquals(emptyList<String>(), parts[1].tags.map { it.name })
        } finally {
            room.close()
        }
    }
}
