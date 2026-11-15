package com.spendobserver.domain.text

import com.spendobserver.domain.model.Tag
import java.text.Collator
import java.util.Locale

/** Tags in the order a reader of [locale] expects — see [sortedByNameForLocale]. */
fun List<Tag>.sortedForLocale(locale: Locale): List<Tag> = sortedByNameForLocale(locale) { it.name }

/**
 * Items in the order a reader of [locale] expects their [name]s: names in that
 * language's own script first (Cyrillic for ru, Latin for en/fr/de), all the others
 * after — other scripts, and names with no letter at all. Each group is alphabetical
 * by the language's rules (é next to e in French), case ignored; equal names apart
 * from case keep one fixed order.
 */
fun <T> List<T>.sortedByNameForLocale(locale: Locale, name: (T) -> String): List<T> {
    // The language's name in itself ("русский", "English") shows which script it is written in.
    val ownScript = scriptOf(locale.getDisplayLanguage(locale))
    // SECONDARY: letters and accents count, case does not.
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return sortedWith(
        compareBy<T> { scriptOf(name(it)) != ownScript }
            .thenComparator { a, b -> collator.compare(name(a), name(b)) }
            .thenBy { name(it) },
    )
}

/** The script of the first letter in [text]; null when it has none. */
private fun scriptOf(text: String): Character.UnicodeScript? {
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        if (Character.isLetter(codePoint)) return Character.UnicodeScript.of(codePoint)
        i += Character.charCount(codePoint)
    }
    return null
}
