package com.spendobserver.domain.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleFormatTest {

    private val en = Locale.forLanguageTag("en-US")
    private val ru = Locale.forLanguageTag("ru-RU")
    private val fr = Locale.forLanguageTag("fr-FR")
    private val de = Locale.forLanguageTag("de-DE")

    // Grouping separators that look like spaces but aren't.
    private val nbsp = ' '
    private val narrowNbsp = ' '

    @Test
    fun `amount uses each locale's grouping and decimal separators`() {
        assertEquals("1,234,567.89", formatAmount(123456789, en))
        assertEquals("1${nbsp}234${nbsp}567,89", formatAmount(123456789, ru))
        assertEquals("1${narrowNbsp}234${narrowNbsp}567,89", formatAmount(123456789, fr))
        assertEquals("1.234.567,89", formatAmount(123456789, de))
    }

    @Test
    fun `amount always shows two decimals, exact to the cent`() {
        assertEquals("0.00", formatAmount(0, en))
        assertEquals("0.29", formatAmount(29, en))
        assertEquals("1,700.00", formatAmount(170000, en))
        assertEquals("-0,05", formatAmount(-5, de))
    }

    @Test
    fun `signed amount adds a plus only to positive values`() {
        assertEquals("+1.700,00", formatSignedAmount(170000, de))
        assertEquals("-1.700,00", formatSignedAmount(-170000, de))
        assertEquals("0,00", formatSignedAmount(0, de))
    }

    @Test
    fun `whole number is grouped without decimals`() {
        assertEquals("12,000", formatWholeNumber(12000, en))
        assertEquals("12.000", formatWholeNumber(12000, de))
        assertEquals("5", formatWholeNumber(5, ru))
    }

    @Test
    fun `month name is the capitalized standalone form`() {
        assertEquals("March", monthName(3, en))
        assertEquals("Март", monthName(3, ru)) // nominative, not the genitive "марта"
        assertEquals("Mars", monthName(3, fr))
        assertEquals("März", monthName(3, de))
    }

    @Test
    fun `short month name is the locale's own abbreviation`() {
        assertEquals("Mar", shortMonthName(3, en))
        assertEquals("янв.", shortMonthName(1, ru))
        assertEquals("févr.", shortMonthName(2, fr))
        assertEquals("Okt", shortMonthName(10, de))
    }

    @Test
    fun `format locale keeps the region of the preferred locale in the app's language`() {
        val enGb = Locale.forLanguageTag("en-GB")
        assertEquals(enGb, formatLocale("en", listOf(enGb)))
        // Ukrainian first, Russian second: Android shows the Russian texts, formats follow them.
        assertEquals(ru, formatLocale("ru", listOf(Locale.forLanguageTag("uk-UA"), ru)))
    }

    @Test
    fun `format locale falls back to the bare app language for an unsupported system language`() {
        assertEquals(Locale.forLanguageTag("en"), formatLocale("en", listOf(Locale.forLanguageTag("uk-UA"))))
    }
}
