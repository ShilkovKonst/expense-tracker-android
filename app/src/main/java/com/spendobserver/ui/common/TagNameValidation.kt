package com.spendobserver.ui.common

import com.spendobserver.data.repository.TagRepository

const val TAG_NAME_EMPTY_ERROR = "Введите название"
const val TAG_NAME_TAKEN_ERROR = "Тег с таким названием уже есть"

/**
 * Why [name] (already passed through `trimInvisible()`) can't be used for a
 * tag of [trackerId], or `null` if it can. Names are unique per tracker
 * across both levels, ignoring case. [exceptTagId] is the tag being
 * renamed, which may keep or recase its own name.
 */
suspend fun TagRepository.tagNameError(trackerId: Long, name: String, exceptTagId: Long? = null): String? = when {
    name.isEmpty() -> TAG_NAME_EMPTY_ERROR
    isNameTaken(trackerId, name, exceptTagId) -> TAG_NAME_TAKEN_ERROR
    else -> null
}
