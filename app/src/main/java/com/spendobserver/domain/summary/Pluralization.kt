package com.spendobserver.domain.summary

/** Russian plural choice for [count]: [one] for 1 (21, 31…), [few] for 2-4 (22-24…), [many] for 5+ and 11-14. */
fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** 1 запись, 2-4 записи, 5+ (and 11-14) записей. */
fun recordsWord(count: Int): String = russianPlural(count, "запись", "записи", "записей")

/** 1 часть, 2-4 части, 5+ (and 11-14) частей. */
fun partsWord(count: Int): String = russianPlural(count, "часть", "части", "частей")
