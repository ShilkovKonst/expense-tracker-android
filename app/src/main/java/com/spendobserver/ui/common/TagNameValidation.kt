package com.spendobserver.ui.common

import com.spendobserver.data.repository.TagRepository

/**
 * Why [name] (already passed through `trimInvisible()`) can't be used for a
 * tag of [trackerId], or `null` if it can. Names are unique per tracker
 * across both levels, ignoring case. [exceptTagId] is the tag being
 * renamed, which may keep or recase its own name.
 */
suspend fun TagRepository.tagNameError(trackerId: Long, name: String, exceptTagId: Long? = null): NameError? = when {
    name.isEmpty() -> NameError.EMPTY
    isNameTaken(trackerId, name, exceptTagId) -> NameError.TAKEN
    else -> null
}
