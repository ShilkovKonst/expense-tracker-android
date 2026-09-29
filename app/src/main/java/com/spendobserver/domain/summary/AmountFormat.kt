package com.spendobserver.domain.summary

import kotlin.math.abs

/** Formats [cents] as `whole.fraction`, with a leading "-" for negative values (e.g. `-1234` -> `"-12.34"`). */
fun formatCents(cents: Long): String {
    val negative = cents < 0
    val absCents = abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}$whole.$fraction"
}

/** Same as [formatCents] but also prefixes a "+" for positive values — for contexts where the sign itself carries meaning (a net total, a signed record amount). */
fun formatSignedCents(cents: Long): String {
    val sign = if (cents > 0) "+" else ""
    return "$sign${formatCents(cents)}"
}
