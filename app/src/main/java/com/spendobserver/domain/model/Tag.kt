package com.spendobserver.domain.model

/**
 * [isGlobal] fixes which level a tag lives on: global tags ("где/с кем" —
 * a shop, a trip) go on the record itself; local tags ("на что" — groceries,
 * alcohol) go on its parts. Never both, so per-tag statistics stay unambiguous.
 */
data class Tag(
    val id: Long = 0,
    val trackerId: Long,
    val name: String,
    val isGlobal: Boolean = false,
)
