package com.spendobserver.domain.backup

import com.spendobserver.domain.text.isSameNameAs

/** What to do with an imported tracker whose title another tracker already has. */
enum class ClashResolution {
    /** Delete the existing tracker (with all its records), then import. */
    REPLACE,

    /** Import beside it under a free "title (N)" name. */
    COPY,
}

/** One tracker to import; [resolution] is required exactly when its title is taken. */
data class ImportItem(
    val tracker: ImportedTracker,
    val resolution: ClashResolution? = null,
)

/**
 * The first of "title (2)", "title (3)", … that no title in [taken] matches
 * by [isSameNameAs] — so "Дом (2)" is skipped when "дом (2)" exists.
 */
fun copyTitle(title: String, taken: Collection<String>): String =
    generateSequence(2) { it + 1 }
        .map { "$title ($it)" }
        .first { candidate -> taken.none { it.isSameNameAs(candidate) } }
