package com.spendobserver.testing

import android.database.sqlite.SQLiteException
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.TrackerSnapshot

/**
 * [BackupRepository] that only remembers what it was asked: an import's
 * effect on the database is [com.spendobserver.data.repository.RoomBackupRepository]'s
 * business and has its own connected test.
 */
class FakeBackupRepository(
    private val snapshots: List<TrackerSnapshot> = emptyList(),
) : BackupRepository {

    /** When set, [import] throws [SQLiteException], as Room does on a full disk. */
    var failImport = false

    /** The items of every [import] call that went through, in order. */
    val imports = mutableListOf<List<ImportItem>>()

    /** The ids of every [markBackedUp] call, in order. */
    val markedBackedUp = mutableListOf<Collection<Long>>()

    private var nextId = 1L

    override suspend fun snapshot(trackerId: Long): TrackerSnapshot? = snapshots.find { it.tracker.id == trackerId }

    override suspend fun snapshotAll(): List<TrackerSnapshot> = snapshots.sortedBy { it.tracker.title.lowercase() }

    override suspend fun markBackedUp(trackerIds: Collection<Long>) {
        markedBackedUp += trackerIds
    }

    override suspend fun import(items: List<ImportItem>): List<Long> {
        if (failImport) throw SQLiteException("fake: import refused")
        imports += items
        return items.map { nextId++ }
    }
}
