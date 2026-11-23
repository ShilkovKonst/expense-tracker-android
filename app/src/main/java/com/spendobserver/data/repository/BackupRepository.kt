package com.spendobserver.data.repository

import com.spendobserver.domain.backup.ImportItem
import com.spendobserver.domain.backup.TrackerSnapshot

/**
 * Reads trackers whole for export and writes imported ones. An interface so
 * JVM tests of the ViewModels can pass a fake; the app uses
 * [RoomBackupRepository], built by the `BackupRepository(db)` factory function.
 */
interface BackupRepository {

    /** Null if the tracker is gone (e.g. deleted while the export dialog was open). */
    suspend fun snapshot(trackerId: Long): TrackerSnapshot?

    /** Every tracker, sorted by title, read as one consistent state. */
    suspend fun snapshotAll(): List<TrackerSnapshot>

    /** Called once a file with these trackers has actually been saved or shared. */
    suspend fun markBackedUp(trackerIds: Collection<Long>)

    /**
     * Imports [items] all or nothing — any failure leaves the database as it
     * was. Returns the new trackers' ids in [items] order.
     *
     * @throws IllegalStateException for an item whose title is taken but
     *   that has no [ImportItem.resolution]: the caller must ask first.
     */
    suspend fun import(items: List<ImportItem>): List<Long>
}
