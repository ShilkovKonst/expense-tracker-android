package com.spendobserver.ui.trackers

import android.database.sqlite.SQLiteConstraintException
import com.spendobserver.data.repository.TrackerRepository

internal const val TITLE_EMPTY_ERROR = "Введите название"
internal const val TITLE_TAKEN_ERROR = "Трекер с таким названием уже есть"

/**
 * Why [title] (already passed through `trimInvisible()`) can't be used, or
 * `null` if it can. [exceptTrackerId] is the tracker being renamed, which
 * may keep its own name.
 */
internal suspend fun TrackerRepository.titleError(title: String, exceptTrackerId: Long? = null): String? = when {
    title.isEmpty() -> TITLE_EMPTY_ERROR
    isTitleTaken(title, exceptTrackerId) -> TITLE_TAKEN_ERROR
    else -> null
}

/**
 * Runs a write that may still hit the `trackers.title` unique index — the
 * check above and the write aren't atomic — returning a failed [Result]
 * for the caller to show as [TITLE_TAKEN_ERROR], instead of an uncaught
 * exception that crashes the app.
 */
internal suspend fun <T> catchingTitleClash(write: suspend () -> T): Result<T> =
    try {
        Result.success(write())
    } catch (e: SQLiteConstraintException) {
        Result.failure(e)
    }
