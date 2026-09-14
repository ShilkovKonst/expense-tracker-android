package com.spendobserver.di

import android.content.Context
import com.spendobserver.data.local.AppDatabase
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository

/**
 * Manual dependency container (README's "ручной Container на первом этапе" —
 * a stand-in for a DI framework until/unless one is actually needed). Built
 * once per process by [com.spendobserver.SpendObserverApplication] and
 * handed down to whatever needs a repository.
 */
class AppContainer(context: Context) {
    private val database: AppDatabase = AppDatabase.getInstance(context)

    val trackerRepository: TrackerRepository by lazy { TrackerRepository(database.trackerDao()) }
    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao()) }
    val recordRepository: RecordRepository by lazy { RecordRepository(database) }
}
