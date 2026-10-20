package com.spendobserver.data.repository

import androidx.room.withTransaction
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.TrackerSnapshot
import com.spendobserver.domain.backup.copyTitle
import com.spendobserver.domain.backup.toEpochMillis
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.text.isSameNameAs
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone

/**
 * Reads trackers whole for export and writes imported ones. Format and
 * validation live in `domain/backup`; this class only moves data between
 * those models and Room.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val recordRepository: RecordRepository = RecordRepository(db),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
) {
    private val trackerDao = db.trackerDao()
    private val tagDao = db.tagDao()

    /** Null if the tracker is gone (e.g. deleted while the export dialog was open). */
    suspend fun snapshot(trackerId: Long): TrackerSnapshot? {
        val tracker = trackerDao.getById(trackerId) ?: return null
        return TrackerSnapshot(
            tracker = tracker,
            tags = tagDao.getForTracker(trackerId),
            records = recordRepository.observeAll(trackerId).first(),
        )
    }

    suspend fun snapshotAll(): List<TrackerSnapshot> =
        trackerDao.getAll().sortedBy { it.title.lowercase() }.mapNotNull { snapshot(it.id) }

    /** Called once a file with these trackers has actually been saved or shared. */
    suspend fun markBackedUp(trackerIds: Collection<Long>) {
        val at = now()
        db.withTransaction {
            trackerIds.forEach { id -> trackerDao.getById(id)?.let { trackerDao.update(it.copy(backupAt = at)) } }
        }
    }

    /**
     * Imports [items] in one transaction — any failure leaves the database
     * as it was. Returns the new trackers' ids in [items] order.
     *
     * @throws IllegalStateException for an item whose title is taken but
     *   that has no [ImportItem.resolution]: the caller must ask first.
     */
    suspend fun import(items: List<ImportItem>): List<Long> = db.withTransaction {
        val fileTitles = items.map { it.tracker.title }
        items.map { item -> importOne(item, fileTitles) }
    }

    /**
     * A copy's name dodges [fileTitles] too — the same rule as the preview,
     * so the name shown there is the name stored, and a copy never takes the
     * title of a later item that had no clash and so no resolution.
     */
    private suspend fun importOne(item: ImportItem, fileTitles: List<String>): Long {
        val imported = item.tracker
        // Read inside the loop: an earlier item of this same import may have taken a title.
        val existing = trackerDao.getAll()
        val clash = existing.firstOrNull { it.title.isSameNameAs(imported.title) }
        val title = when {
            clash == null -> imported.title
            item.resolution == ClashResolution.REPLACE -> {
                trackerDao.delete(clash) // its tags, records and parts follow via CASCADE
                imported.title
            }
            item.resolution == ClashResolution.COPY -> copyTitle(imported.title, existing.map { it.title } + fileTitles)
            else -> error("Tracker «${imported.title}» already exists; choose REPLACE or COPY")
        }

        val trackerId = trackerDao.insert(
            Tracker(title = title, createdAt = imported.createdAt.toEpochMillis(zone()), updatedAt = now()),
        )
        val tagIds = imported.tags.associate { tag ->
            tag.name to tagDao.insert(Tag(trackerId = trackerId, name = tag.name, isGlobal = tag.isGlobal))
        }
        for (record in imported.records) {
            recordRepository.insertRecord(
                record = Record(trackerId = trackerId, date = record.date, type = record.type, description = record.description),
                tagIds = record.tagNames.mapTo(HashSet()) { tagIds.getValue(it) },
                parts = record.parts.map { part -> PartDraft(part.amountCents, part.tagNames.mapTo(HashSet()) { tagIds.getValue(it) }) },
            )
        }
        return trackerId
    }
}
