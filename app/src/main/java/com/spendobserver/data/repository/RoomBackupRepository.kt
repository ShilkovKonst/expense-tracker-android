package com.spendobserver.data.repository

import androidx.room.withTransaction
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.local.entity.TrackerEntity
import com.spendobserver.data.local.toDomain
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.backup.ClashResolution
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.TrackerSnapshot
import com.spendobserver.domain.backup.copyTitle
import com.spendobserver.domain.backup.toEpochMillis
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.domain.text.isSameNameAs
import kotlinx.datetime.TimeZone

/** Builds the Room-backed [BackupRepository]; named like the interface, as `RecordRepository(db)` is. */
fun BackupRepository(
    db: AppDatabase,
    recordRepository: RecordRepository = RecordRepository(db),
    now: () -> Long = System::currentTimeMillis,
    zone: () -> TimeZone = TimeZone::currentSystemDefault,
): BackupRepository = RoomBackupRepository(db, recordRepository, now, zone)

/**
 * Format and validation live in `domain/backup`; this class only moves data
 * between those models and Room.
 */
class RoomBackupRepository(
    private val db: AppDatabase,
    private val recordRepository: RecordRepository,
    private val now: () -> Long,
    private val zone: () -> TimeZone,
) : BackupRepository {
    private val trackerDao = db.trackerDao()
    private val tagDao = db.tagDao()

    override suspend fun snapshot(trackerId: Long): TrackerSnapshot? = db.withTransaction {
        trackerDao.getById(trackerId)?.let { snapshotOf(it) }
    }

    /** One transaction, so a write landing mid-export can't leave the file half old, half new. */
    override suspend fun snapshotAll(): List<TrackerSnapshot> = db.withTransaction {
        trackerDao.getAll().sortedBy { it.title.lowercase() }.map { snapshotOf(it) }
    }

    private suspend fun snapshotOf(tracker: TrackerEntity) = TrackerSnapshot(
        tracker = tracker.toDomain(),
        tags = tagDao.getForTracker(tracker.id).map { it.toDomain() },
        records = recordRepository.getAll(tracker.id),
    )

    override suspend fun markBackedUp(trackerIds: Collection<Long>) = trackerDao.setBackupAt(trackerIds, now())

    override suspend fun import(items: List<ImportItem>): List<Long> = db.withTransaction {
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
            TrackerEntity(title = title, createdAt = imported.createdAt.toEpochMillis(zone()), updatedAt = now()),
        )
        val tagIds = imported.tags.associate { tag ->
            tag.name to tagDao.insert(TagEntity(trackerId = trackerId, name = tag.name, isGlobal = tag.isGlobal))
        }
        for (record in imported.records) {
            recordRepository.insertRecord(
                record = RecordDraft(trackerId = trackerId, date = record.date, type = record.type, description = record.description),
                tagIds = record.tagNames.mapTo(HashSet()) { tagIds.getValue(it) },
                parts = record.parts.map { part -> PartDraft(part.amountCents, part.tagNames.mapTo(HashSet()) { tagIds.getValue(it) }) },
            )
        }
        return trackerId
    }
}
