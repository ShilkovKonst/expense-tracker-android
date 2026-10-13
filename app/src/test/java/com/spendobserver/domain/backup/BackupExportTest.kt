package com.spendobserver.domain.backup

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.testing.TestPart
import com.spendobserver.testing.taggedRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportTest {

    private val zone = TimeZone.UTC
    private val createdAt = LocalDateTime(2025, 12, 1, 2, 5, 41).toEpochMillis(zone)

    private val shop = Tag(id = 1, trackerId = 1, name = "g20", isGlobal = true)
    private val food = Tag(id = 2, trackerId = 1, name = "продукты")
    private val alco = Tag(id = 3, trackerId = 1, name = "алко")
    private val unused = Tag(id = 4, trackerId = 1, name = "aldi", isGlobal = true)

    private val snapshot = TrackerSnapshot(
        tracker = Tracker(id = 1, title = "домашний", createdAt = createdAt, updatedAt = createdAt),
        tags = listOf(food, shop, alco, unused),
        records = listOf(
            taggedRecord(id = 2, date = LocalDate(2025, 10, 2), tags = listOf(shop), parts = listOf(TestPart(661, listOf(alco)), TestPart(4355, listOf(food)))),
            taggedRecord(id = 1, date = LocalDate(2025, 9, 30), type = RecordType.INCOME, parts = listOf(TestPart(2700))),
        ),
    )

    @Test
    fun buildsTheFileInStableOrder() {
        val tracker = buildBackup(listOf(snapshot), exportedAtMillis = createdAt + 1_234, zone = zone).trackers.single()

        assertEquals("2025-12-01T02:05:41", tracker.createdAt)
        assertEquals(listOf("aldi", "g20", "алко", "продукты"), tracker.tags.map { it.name })
        assertEquals(listOf("2025-09-30", "2025-10-02"), tracker.records.map { it.date })
        assertEquals(listOf("income", "cost"), tracker.records.map { it.type })
        assertEquals(listOf(661L, 4355L), tracker.records[1].parts.map { it.amount })
    }

    @Test
    fun exportedAt_dropsMilliseconds() {
        val file = buildBackup(listOf(snapshot), exportedAtMillis = createdAt + 999, zone = zone)

        assertEquals("2025-12-01T02:05:41", file.exportedAt)
    }

    @Test
    fun roundTrip_exportThenImport_givesTheSameData() {
        val json = encodeBackup(buildBackup(listOf(snapshot), createdAt, zone))

        val tracker = (parseBackup(json) as BackupParseResult.Success).trackers.single()

        assertEquals("домашний", tracker.title)
        assertEquals(createdAt, tracker.createdAt.toEpochMillis(zone))
        assertEquals(snapshot.tags.map { ImportedTag(it.name, it.isGlobal) }.toSet(), tracker.tags.toSet())
        val receipt = tracker.records.single { it.date == LocalDate(2025, 10, 2) }
        assertEquals(listOf("g20"), receipt.tagNames)
        assertEquals(
            listOf(ImportedPart(661, listOf("алко")), ImportedPart(4355, listOf("продукты"))),
            receipt.parts,
        )
        val refund = tracker.records.single { it.type == RecordType.INCOME }
        assertEquals(listOf(ImportedPart(2700, emptyList())), refund.parts)
    }
}
