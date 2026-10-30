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

    private fun failure(json: String): BackupError {
        val result = parseBackup(json)
        assertTrue("expected a failure, got $result", result is BackupParseResult.Failure)
        return (result as BackupParseResult.Failure).error
    }

    /** The file's only tracker, and its record of 2025-10-02. */
    private val inTracker = BackupLocation(trackerTitle = "домашний", trackerNumber = 1)
    private val inRecord = inTracker.copy(recordDate = "2025-10-02")

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
        assertEquals(BackupError.Damaged, failure("hello"))
        assertEquals(BackupError.Damaged, failure("""{"format": "spendobserver"}"""))
    }

    @Test
    fun foreignFormat_andNewerVersion_areRefused() {
        assertEquals(BackupError.NotSpendObserver, failure(file(format = "other")))
        assertEquals(BackupError.NewerVersion, failure(file(version = BACKUP_VERSION + 1)))
    }

    @Test
    fun tagNamesMustBeUniqueIgnoringCase() {
        val error = failure(file(tags = """{"name": "g20", "global": true}, {"name": "G20", "global": false}"""))

        assertEquals(BackupError.Invalid(inTracker, BackupIssue.DuplicateTag("G20")), error)
    }

    @Test
    fun unknownTagReference_namesTheRecord() {
        val error = failure(file(records = """{"date": "2025-10-02", "type": "cost", "tags": ["lidl"], "parts": [{"amount": 1}]}"""))

        assertEquals(BackupError.Invalid(inRecord, BackupIssue.UnknownTag("lidl")), error)
    }

    @Test
    fun tagsMustSitOnTheirLevel() {
        val localOnRecord = failure(
            file(records = """{"date": "2025-10-02", "type": "cost", "tags": ["алко"], "parts": [{"amount": 1}]}"""),
        )
        val globalOnPart = failure(
            file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 1, "tags": ["g20"]}]}"""),
        )

        assertEquals(BackupError.Invalid(inRecord, BackupIssue.TagNotGlobal("алко")), localOnRecord)
        assertEquals(BackupError.Invalid(inRecord.copy(partNumber = 1), BackupIssue.TagIsGlobal("g20")), globalOnPart)
    }

    @Test
    fun aRecordHasAtMostOneGlobalTag() {
        val error = failure(
            file(
                records = """{"date": "2025-10-02", "type": "cost", "tags": ["g20", "aldi"], "parts": [{"amount": 1}]}""",
                tags = """{"name": "g20", "global": true}, {"name": "aldi", "global": true}""",
            ),
        )

        assertEquals(BackupError.Invalid(inRecord, BackupIssue.SeveralGlobalTags), error)
    }

    @Test
    fun partsRules() {
        assertEquals(
            BackupError.Invalid(inRecord, BackupIssue.NoParts),
            failure(file(records = """{"date": "2025-10-02", "type": "cost", "parts": []}""")),
        )
        assertEquals(
            BackupError.Invalid(inRecord.copy(partNumber = 2), BackupIssue.NonPositiveAmount),
            failure(file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 5}, {"amount": 0, "tags": ["алко"]}]}""")),
        )
        assertEquals(
            BackupError.Invalid(inRecord, BackupIssue.DuplicatePartTags),
            failure(
                file(records = """{"date": "2025-10-02", "type": "cost", "parts": [{"amount": 1, "tags": ["алко", "продукты"]}, {"amount": 2, "tags": ["продукты", "алко"]}]}"""),
            ),
        )
    }

    @Test
    fun badDatesAndTypes_areNamed() {
        assertEquals(
            BackupError.Invalid(inTracker, BackupIssue.BadRecordDate("02/10/2025")),
            failure(file(records = """{"date": "02/10/2025", "type": "cost", "parts": [{"amount": 1}]}""")),
        )
        assertEquals(
            BackupError.Invalid(inRecord, BackupIssue.UnknownType("expense")),
            failure(file(records = """{"date": "2025-10-02", "type": "expense", "parts": [{"amount": 1}]}""")),
        )
    }

    @Test
    fun blankTrackerTitle_isLocatedByItsNumber() {
        val error = failure(file().replace("\"title\": \"домашний\"", "\"title\": \" \""))

        assertEquals(BackupError.Invalid(BackupLocation(trackerTitle = null, trackerNumber = 1), BackupIssue.EmptyTitle), error)
    }

    @Test
    fun sameTrackerTwiceInOneFile_isRefused() {
        val tracker = """{"title": "Дом", "createdAt": "2025-12-01T02:05:41"}"""
        val json = """{"format": "spendobserver", "version": 1, "exportedAt": "x", "trackers": [$tracker, ${tracker.replace("Дом", " дом ")}]}"""

        assertEquals(BackupError.DuplicateTracker("дом"), failure(json))
    }
}
