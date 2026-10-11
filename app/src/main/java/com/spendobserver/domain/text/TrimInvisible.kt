package com.spendobserver.domain.text

/**
 * Like [String.trim], but also strips invisible "format" characters from
 * both ends — zero-width space (U+200B), zero-width (non-)joiner, word
 * joiner, byte-order mark (U+FEFF) — which plain `trim()` keeps because
 * they aren't whitespace. Without this, "Family" and "Family​" look
 * identical on screen yet count as different names.
 *
 * Characters *inside* the string are left alone.
 */
fun String.trimInvisible(): String = trim { it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt() }

/**
 * Whether two user-entered names (trackers, tags) count as the same:
 * equal after [trimInvisible], ignoring case — so "Семья", " семья " and
 * "СЕМЬЯ​" are one name. Compared in Kotlin rather than SQL on
 * purpose: SQLite's `LOWER()` / `COLLATE NOCASE` only fold ASCII letters,
 * so Cyrillic names would slip through.
 */
fun String.isSameNameAs(other: String): Boolean = trimInvisible().equals(other.trimInvisible(), ignoreCase = true)
