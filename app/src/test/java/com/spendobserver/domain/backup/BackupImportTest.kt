package com.spendobserver.domain.backup

import com.spendobserver.data.local.entity.RecordType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportTest {

    /** A valid one-tracker file; [records] is spliced in as the JSON array body. */
    private fun file(
        records: String = """{"date": "2025-10-02", "type": "cost", "tags": ["g20"], "parts": [{"amount": 661, "tags": ["алко"]}]}""",
        tags: String = """{"name": "g20", "global": true}, {"name": "алко", "global": false}, {"name": "продукты", "global": false}""",
        format: String = BACKUP_FORMAT,
        version: Int = BACKUP_VERSION,
    ) = """
        {
          "format": "$format",
          "version": $version,
          "exportedAt": "2026-10-13T19:05:00",
          "trackers": [
            {"title": "домашний", "createdAt": "2025-12-01T02:05:41", "tags": [$tags], "records": [$records]}
          ]
        }
    """.trimIndent()

    private fun success(json: String) = parseBackup(json) as BackupParseResult.Success

    private fun failureMessage(json: String): String {
        val result = parseBackup(json)
        assertTrue("expected a failure, got $result", result is BackupParseResult.Failure)
        return (result as BackupParseResult.Failure).message
    }

    @Test
    fun validFile_becomesTypedTrackers() {
        val tracker = success(file()).trackers.single()

        assertEquals("домашний", tracker.title)
        assertEquals(LocalDateTime(2025, 12, 1, 2, 5, 41), tracker.createdAt)
        assertEquals(
            listOf(ImportedTag("g20", true), ImportedTag("алко", false), ImportedTag("продукты", false)),
            tracker.tags,
        )
        val record = tracker.records.single()
        assertEquals(LocalDate(2025, 10, 2), record.date)
        assertEquals(RecordType.COST, record.type)
        assertEquals(listOf("g20"), record.tagNames)
        assertEquals(listOf(ImportedPart(661, listOf("алко"))), record.parts)
    }

    @Test
    fun blankDescription_becomesNull_andNamesAreTrimmed() {
        val record = success(
            file(
                records = """{"date": "2025-10-02", "type": "income", "description": "  ", "parts": [{"amount": 1}]}""",
                tags = """{"name": "  g20 ", "global": true}""",
            ),
        ).trackers.single()

        assertEquals("g20", record.tags.single().name)
        assertEquals(null, record.records.single().description)
        assertEquals(RecordType.INCOME, record.records.single().type)
    }

    @Test
    fun tagReference_inAnotherCase_resolvesToTheDeclaredSpelling() {
        val record = success(
            file(records = """{"date": "2025-10-02", "type": "cost", "tags": ["G20", "g20"], "parts": [{"amount": 1, "tags": ["АЛКО"]}]}"""),
        ).trackers.single().records.single()

        assertEquals(listOf("g20"), record.tagNames)
        assertEquals(listOf("алко"), record.parts.single().tagNames)
    }

    @Test
    fun unknownFields_areIgnored() {
        val json = file().replace("\"version\": 1,", "\"version\": 1, \"futureField\": {\"x\": 1},")

        assertEquals(1, success(json).trackers.size)
    }

    @Test
    fun notJsonOrMissingFields_isReportedAsDamaged() {
        assertEquals("Файл повреждён или это не файл SpendObserver", failureMessage("hello"))
        assertEquals("Файл повреждён или это не файл SpendObserver", failureMessage("""{"format": "spendobserver"}"""))
    }

    @Test
    fun foreignFormat_andNewerVersion_areRefused() {
        assertEquals("Это не файл SpendObserver", failureMessage(file(format = "other")))
        assertEquals(
            "Файл создан более новой версией приложения — обновите приложение",
            failureMessage(file(version = BACKUP_VERSION + 1)),
        )
    }

    @Test
    fun tagNamesMustBeUniqueIgnoringCase() {
        val message = failureMessage(file(tags = """{"name": "g20", "global": true}, {"name": "G20", "global": false}"""))

        assertEquals("Трекер «домашний»: тег «G20» объявлен дважды", message)
    }

    @Test
    fun unknownTagReference_namesTheRecord() {
        val message = failureMessage(file(records = """{"date": "2025-10-02", "type": "cost", "tags": ["lidl"], "parts": [{"amount": 1}]}"""))

        assertEquals("Трекер «домашний», запись от 2025-10-02: неизвестный тег «lidl»", message)
    }

    @Test
    fun tagsMustSitOnTheirLevel() {
        val localOnRecord = failureMessage(
            file(records = """{"date": "2025-10-02", "type": "cost", "tags": ["алко"], "parts": [{"amount": 1}]}"""),
        )
        val globalOnPart = failureMessage(
            file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 1, "tags": ["g20"]}]}"""),
        )

        assertEquals("Трекер «домашний», запись от 2025-10-02: тег «алко» не общий — он ставится на части записи", localOnRecord)
        assertEquals(
            "Трекер «домашний», запись от 2025-10-02, часть 1: тег «g20» общий — он ставится на запись, а не на её часть",
            globalOnPart,
        )
    }

    @Test
    fun partsRules() {
        assertEquals(
            "Трекер «домашний», запись от 2025-10-02: нет ни одной части",
            failureMessage(file(records = """{"date": "2025-10-02", "type": "cost", "parts": []}""")),
        )
        assertEquals(
            "Трекер «домашний», запись от 2025-10-02, часть 2: сумма должна быть больше нуля",
            failureMessage(file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 5}, {"amount": 0, "tags": ["алко"]}]}""")),
        )
        assertEquals(
            "Трекер «домашний», запись от 2025-10-02: две части с одинаковыми тегами",
            failureMessage(
                file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 1, "tags": ["алко", "продукты"]}, {"amount": 2, "tags": ["продукты", "алко"]}]}"""),
            ),
        )
    }

    @Test
    fun badDatesAndTypes_areNamed() {
        assertEquals(
            "Трекер «домашний»: неверная дата записи «02/10/2025»",
            failureMessage(file(records = """{"date": "02/10/2025", "type": "cost", "parts": [{"amount": 1}]}""")),
        )
        assertEquals(
            "Трекер «домашний», запись от 2025-10-02: неизвестный тип «expense»",
            failureMessage(file(records = """{"date": "2025-10-02", "type": "expense", "parts": [{"amount": 1}]}""")),
        )
    }

    @Test
    fun sameTrackerTwiceInOneFile_isRefused() {
        val tracker = """{"title": "Дом", "createdAt": "2025-12-01T02:05:41"}"""
        val json = """{"format": "spendobserver", "version": 1, "exportedAt": "x", "trackers": [$tracker, ${tracker.replace("Дом", " дом ")}]}"""

        assertEquals("Трекер «дом» встречается в файле дважды", failureMessage(json))
    }
}
