package com.spendobserver.ui.trackers

import android.database.sqlite.SQLiteConstraintException
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.common.NameError

/**
 * Why [title] (already passed through `trimInvisible()`) can't be used, or
 * `null` if it can. [exceptTrackerId] is the tracker being renamed, which
 * may keep its own name.
 */
internal suspend fun TrackerRepository.titleError(title: String, exceptTrackerId: Long? = null): NameError? = when {
    title.isEmpty() -> NameError.EMPTY
    isTitleTaken(title, exceptTrackerId) -> NameError.TAKEN
    else -> null
}

/**
 * Runs a write that may still hit the `trackers.title` unique index — the
 * check above and the write aren't atomic — returning a failed [Result]
 * for the caller to show as [NameError.TAKEN], instead of an uncaught
 * exception that crashes the app.
 */
internal suspend fun <T> catchingTitleClash(write: suspend () -> T): Result<T> =
    try {
        Result.success(write())
    } catch (e: SQLiteConstraintException) {
        Result.failure(e)
    }
