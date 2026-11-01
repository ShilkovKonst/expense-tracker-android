package com.spendobserver.data.local

import androidx.room.migration.Migration

/**
 * Every schema upgrade, in version order. A schema change raises
 * [AppDatabase]'s version and adds its `Migration(from, to)` here: the file on
 * the phone keeps its old structure when the app updates.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
