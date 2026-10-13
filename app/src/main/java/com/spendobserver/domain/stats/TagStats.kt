package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.RecordWithParts

/** How often [tag] occurs in a set of records and how much money it covers. */
data class TagStats(
    val tag: Tag,
    val recordCount: Int,
    val amountCents: Long,
)

/**
 * Stats for every tag in [tags], in the same order, zeros included — the
 * screen greys unused tags out rather than hiding them.
 *
 * A global tag covers whole records, so it gets each record's full total.
 * A local tag covers only the parts it's on: its amount is the sum of those
 * parts, and its count is the number of *distinct* records, so a tag on two
 * parts of one receipt still counts once. A part with two local tags adds
 * its amount to both, which means tag amounts needn't add up to the total.
 */
fun List<RecordWithParts>.tagStats(tags: List<Tag>): List<TagStats> {
    val counts = HashMap<Long, Int>()
    val amounts = HashMap<Long, Long>()
    fun add(tagId: Long, cents: Long) {
        amounts[tagId] = (amounts[tagId] ?: 0L) + cents
    }

    for (record in this) {
        for (tag in record.tags) {
            counts[tag.id] = (counts[tag.id] ?: 0) + 1
            add(tag.id, record.amountCents)
        }
        val localTagIds = HashSet<Long>()
        for (part in record.parts) {
            for (tag in part.tags) {
                localTagIds += tag.id
                add(tag.id, part.part.amountCents)
            }
        }
        for (tagId in localTagIds) counts[tagId] = (counts[tagId] ?: 0) + 1
    }

    return tags.map { TagStats(it, counts[it.id] ?: 0, amounts[it.id] ?: 0L) }
}

/** Top tags of one level, by frequency and by money; unused tags never make it in. */
data class TagTops(
    val byCount: List<TagStats>,
    val byAmount: List<TagStats>,
)

private val byName = compareBy<TagStats> { it.tag.name.lowercase() }

fun List<TagStats>.tops(limit: Int = 3): TagTops {
    val used = filter { it.recordCount > 0 }
    return TagTops(
        byCount = used.sortedWith(
            compareByDescending<TagStats> { it.recordCount }.thenByDescending { it.amountCents }.then(byName),
        ).take(limit),
        byAmount = used.sortedWith(
            compareByDescending<TagStats> { it.amountCents }.thenByDescending { it.recordCount }.then(byName),
        ).take(limit),
    )
}
