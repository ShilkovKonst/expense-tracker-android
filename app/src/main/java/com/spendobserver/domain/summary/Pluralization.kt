package com.spendobserver.domain.summary

/** Russian plural form of "запись" for [count]: 1 запись, 2-4 записи, 5+ (and 11-14) записей. */
fun recordsWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "записей"
        mod10 == 1 -> "запись"
        mod10 in 2..4 -> "записи"
        else -> "записей"
    }
}
