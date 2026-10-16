package com.spendobserver.domain.summary

import kotlin.math.abs

/**
 * Formats [cents] as plain `whole.fraction`, with a leading "-" for negative values
 * (e.g. `-1234` -> `"-12.34"`): the form amount input fields hold, which the
 * calculator reads back. For display use [com.spendobserver.domain.format.formatAmount].
 */
fun formatCents(cents: Long): String {
    val negative = cents < 0
    val absCents = abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}$whole.$fraction"
}
