package com.spendobserver.domain.stats

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tag
import com.spendobserver.data.local.relation.RecordWithParts

/**
 * The text half of the statistics screen for one period. [costCount] and
 * [incomeCount] ignore the type toggle (the header shows both); everything
 * else counts only records of the selected type.
 */
data class PeriodStats(
    val costCount: Int,
    val incomeCount: Int,
    val amountCents: Long,
    val tags: List<TagStats>,
    val globalTops: TagTops,
    val localTops: TagTops,
)

/** [PeriodStats] over the records of [period]; [tags] are all of the tracker's tags. */
fun List<RecordWithParts>.periodStats(period: StatsPeriod, type: RecordType, tags: List<Tag>): PeriodStats {
    val inPeriod = inPeriod(period)
    val selected = inPeriod.filter { it.record.type == type }
    val tagStats = selected.tagStats(tags)
    return PeriodStats(
        costCount = inPeriod.count { it.record.type == RecordType.COST },
        incomeCount = inPeriod.count { it.record.type == RecordType.INCOME },
        amountCents = selected.sumOf { it.amountCents },
        tags = tagStats,
        globalTops = tagStats.filter { it.tag.isGlobal }.tops(),
        localTops = tagStats.filterNot { it.tag.isGlobal }.tops(),
    )
}
