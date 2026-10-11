package com.spendobserver.data.local.dto

/** One row of [com.spendobserver.data.local.dao.TagDao.observeUsage]: how many records use a tag, on either level. */
data class TagUsageRow(
    val tagId: Long,
    val recordCount: Int,
)
