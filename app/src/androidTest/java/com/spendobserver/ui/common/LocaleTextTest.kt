package com.spendobserver.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.domain.format.formatAmount
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Locale formats as Android's own ICU data produces them — the JVM tests run on the JDK's. */
@RunWith(AndroidJUnit4::class)
class LocaleTextTest {

    private val date = LocalDate.of(2026, 3, 1)

    private fun numericDate(tag: String) = numericDateFormatter(Locale.forLanguageTag(tag)).format(date)

    @Test
    fun numericDate_followsEachLocalesOrderAndSeparators_withAFourDigitYear() {
        assertEquals("03/01/2026", numericDate("en-US"))
        assertEquals("01/03/2026", numericDate("en-GB"))
        assertEquals("01.03.2026", numericDate("ru-RU"))
        assertEquals("01/03/2026", numericDate("fr-FR"))
        assertEquals("01.03.2026", numericDate("de-DE"))
    }

    @Test
    fun amount_onAndroid_matchesTheJvmFormats() {
        assertEquals("1,234,567.89", formatAmount(123456789, Locale.forLanguageTag("en-US")))
        assertEquals("1 234 567,89", formatAmount(123456789, Locale.forLanguageTag("ru-RU")))
        assertEquals("1 234 567,89", formatAmount(123456789, Locale.forLanguageTag("fr-FR")))
        assertEquals("1.234.567,89", formatAmount(123456789, Locale.forLanguageTag("de-DE")))
    }
}
