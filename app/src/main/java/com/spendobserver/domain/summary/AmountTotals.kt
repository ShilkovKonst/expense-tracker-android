package com.spendobserver.domain.summary

import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.relation.RecordWithParts

data class AmountTotals(
    val incomeCents: Long,
    val costCents: Long,
) {
    val netCents: Long get() = incomeCents - costCents
}

fun List<RecordWithParts>.amountTotals(): AmountTotals {
    var income = 0L
    var cost = 0L
    for (record in this) {
        when (record.record.type) {
            RecordType.INCOME -> income += record.amountCents
            RecordType.COST -> cost += record.amountCents
        }
    }
    return AmountTotals(incomeCents = income, costCents = cost)
}
