package com.spendobserver.testing

import android.database.sqlite.SQLiteException
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.domain.calculator.MAX_AMOUNT_CENTS
import com.spendobserver.domain.model.Part
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.record.mergeSameTags
import com.spendobserver.domain.summary.AmountTotals
import com.spendobserver.domain.summary.TrackerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [RecordRepository] for ViewModel JVM tests. Mirrors what the
 * Room one promises: records sorted by date (ties in insertion order), the
 * same part checks, and tag deletion merging parts left with equal tags.
 *
 * Writes get only tag ids, so [tagById] turns them back into the [Tag]s a
 * [Record] holds — pass the tags a test created, or keep the placeholder.
 */
class FakeRecordRepository(
    private val tagById: (Long) -> Tag = { id -> Tag(id = id, trackerId = 1, name = "tag$id") },
) : RecordRepository {

    private val records = MutableStateFlow<List<Record>>(emptyList())
    private var nextId = 1L

    /** When set, every write throws [SQLiteException], as Room does on a full disk; nothing is changed. */
    var failWrites = false

    /** Every insert/update/delete that went through, so a test can tell one save from two. */
    var writeCount = 0
        private set

    /** Puts [record] in as is (a fresh id if its own is 0) and returns it as stored; no write counted. */
    fun add(record: Record): Record {
        val stored = if (record.id == 0L) record.copy(id = nextId++) else record.also { nextId = maxOf(nextId, it.id + 1) }
        records.update { it + stored }
        return stored
    }

    /** Lets a test write a record from the form, then read it back without going through the [Flow]s. */
    val all: List<Record> get() = records.value

    override fun observeAll(trackerId: Long): Flow<List<Record>> = records.map { it.ofTracker(trackerId) }

    override suspend fun getAll(trackerId: Long): List<Record> = records.value.ofTracker(trackerId)

    override fun observeStatsByTracker(): Flow<Map<Long, TrackerStats>> = records.map { list ->
        list.groupBy { it.trackerId }.mapValues { (_, own) ->
            TrackerStats(
                recordCount = own.size,
                totals = AmountTotals(
                    incomeCents = own.filter { it.type == RecordType.INCOME }.sumOf { it.amountCents },
                    costCents = own.filter { it.type == RecordType.COST }.sumOf { it.amountCents },
                ),
            )
        }
    }

    override suspend fun getRecord(id: Long): Record? = records.value.find { it.id == id }

    override suspend fun insertRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>): Long {
        requireValidParts(parts)
        write()
        val id = nextId++
        records.update { it + record.toRecord(id, tagIds, parts) }
        return id
    }

    override suspend fun updateRecord(record: RecordDraft, tagIds: Set<Long>, parts: List<PartDraft>) {
        requireValidParts(parts)
        write()
        records.update { list -> list.map { if (it.id == record.id) record.toRecord(record.id, tagIds, parts) else it } }
    }

    override suspend fun deleteRecord(id: Long) {
        write()
        records.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun deleteTag(tag: Tag) {
        write()
        records.update { list -> list.map { it.withoutTag(tag.id) } }
    }

    private fun write() {
        if (failWrites) throw SQLiteException("fake: write refused")
        writeCount++
    }

    private fun List<Record>.ofTracker(trackerId: Long) = filter { it.trackerId == trackerId }.sortedBy { it.date }

    private fun RecordDraft.toRecord(id: Long, tagIds: Set<Long>, parts: List<PartDraft>) = Record(
        id = id,
        trackerId = trackerId,
        date = date,
        type = type,
        description = description,
        tags = tagIds.map(tagById),
        parts = parts.map { Part(it.amountCents, it.tagIds.map(tagById)) },
    )

    private fun Record.withoutTag(tagId: Long): Record {
        val parts = parts.map { p -> PartDraft(p.amountCents, p.tags.mapNotNullTo(HashSet()) { t -> t.id.takeIf { it != tagId } }) }
        return copy(
            tags = tags.filterNot { it.id == tagId },
            parts = parts.mergeSameTags().map { Part(it.amountCents, it.tagIds.map(tagById)) },
        )
    }

    private fun requireValidParts(parts: List<PartDraft>) {
        require(parts.isNotEmpty()) { "A record needs at least one part" }
        require(parts.all { it.amountCents in 0..MAX_AMOUNT_CENTS }) { "A part's amount is out of range" }
    }
}
