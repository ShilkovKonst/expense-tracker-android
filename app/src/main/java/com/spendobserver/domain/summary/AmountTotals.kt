package com.spendobserver.domain.summary

import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Record

data class AmountTotals(
    val incomeCents: Long,
    val costCents: Long,
) {
    val netCents: Long get() = incomeCents - costCents
}

fun List<Record>.amountTotals(): AmountTotals {
    var income = 0L
    var cost = 0L
    for (record in this) {
        when (record.type) {
            RecordType.INCOME -> income += record.amountCents
            RecordType.COST -> cost += record.amountCents
        }
    }
    return AmountTotals(incomeCents = income, costCents = cost)
}
