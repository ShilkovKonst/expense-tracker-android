package com.spendobserver.domain.text

import com.spendobserver.domain.model.Tag
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TagOrderTest {

    private fun sorted(locale: Locale, vararg names: String): List<String> =
        names.map { Tag(trackerId = 1, name = it) }.sortedForLocale(locale).map { it.name }

    @Test
    fun ignoresCase() {
        assertEquals(
            listOf("aldi", "Boucherie", "carrefour", "Zara"),
            sorted(Locale.ENGLISH, "Zara", "carrefour", "aldi", "Boucherie"),
        )
    }

    @Test
    fun russianUi_cyrillicFirst_thenLatin() {
        assertEquals(
            listOf("Аптека", "вино", "продукты", "aldi", "G20", "lidl"),
            sorted(Locale.forLanguageTag("ru"), "lidl", "продукты", "G20", "вино", "aldi", "Аптека"),
        )
    }

    @Test
    fun latinUi_latinFirst_thenCyrillic() {
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN)) {
            assertEquals(
                listOf("aldi", "G20", "lidl", "Аптека", "вино", "продукты"),
                sorted(locale, "lidl", "продукты", "G20", "вино", "aldi", "Аптека"),
            )
        }
    }

    @Test
    fun accentedLetters_sitWithTheirBaseLetter() {
        assertEquals(
            listOf("eau", "épicerie", "fnac"),
            sorted(Locale.FRENCH, "fnac", "épicerie", "eau"),
        )
    }

    @Test
    fun namesWithoutLetters_joinTheOthers_digitsFirst() {
        assertEquals(
            listOf("вино", "2024", "aldi"),
            sorted(Locale.forLanguageTag("ru"), "2024", "aldi", "вино"),
        )
    }

    @Test
    fun leadingDigits_groupByTheFirstLetter() {
        assertEquals(
            listOf("1 магазин", "вино", "aldi"),
            sorted(Locale.forLanguageTag("ru"), "aldi", "вино", "1 магазин"),
        )
    }

    @Test
    fun sameNameDifferentCase_keepsAFixedOrder() {
        val expected = sorted(Locale.ENGLISH, "Aldi", "aldi")
        assertEquals(expected, sorted(Locale.ENGLISH, "aldi", "Aldi"))
    }
}
