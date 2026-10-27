package com.spendobserver.ui.common

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.R
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

    private val ru = ApplicationProvider.getApplicationContext<Context>()
        .createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag("ru-RU")) })
        .resources

    @Test
    fun russianPlurals_pickOneFewMany_byTheLastTwoDigits() {
        val forms = listOf(1, 21, 101, 2, 22, 104, 5, 11, 12, 14, 25, 111)
            .map { ru.getQuantityString(R.plurals.records_count, it, it) }

        assertEquals(
            listOf(
                "1 запись", "21 запись", "101 запись",
                "2 записи", "22 записи", "104 записи",
                "5 записей", "11 записей", "12 записей", "14 записей", "25 записей", "111 записей",
            ),
            forms,
        )
    }

    /** A missing `few` or `many` would silently fall back to `other` — e.g. "5 записи". */
    @Test
    fun everyRussianPlural_hasItsOwnOneFewAndManyForms() {
        val expected = mapOf(
            R.plurals.imported_trackers to listOf("Импортирован 1 трекер", "Импортировано 2 трекера", "Импортировано 5 трекеров"),
            R.plurals.records_count to listOf("1 запись", "2 записи", "5 записей"),
            R.plurals.parts_count to listOf("1 часть", "2 части", "5 частей"),
            R.plurals.tag_usage to listOf("в 1 записи", "в 2 записях", "в 5 записях"),
            R.plurals.stats_costs_count to listOf("1 расход", "2 расхода", "5 расходов"),
            R.plurals.stats_incomes_count to listOf("1 доход", "2 дохода", "5 доходов"),
            R.plurals.stats_tag_found_in to listOf("Встречается в 1 записи", "Встречается в 2 записях", "Встречается в 5 записях"),
            R.plurals.tags_count to listOf("1 тег", "2 тега", "5 тегов"),
        )
        for ((id, forms) in expected) {
            assertEquals(forms, listOf(1, 2, 5).map { ru.getQuantityString(id, it, it) })
        }
    }

    @Test
    fun amount_onAndroid_matchesTheJvmFormats() {
        assertEquals("1,234,567.89", formatAmount(123456789, Locale.forLanguageTag("en-US")))
        assertEquals("1 234 567,89", formatAmount(123456789, Locale.forLanguageTag("ru-RU")))
        assertEquals("1 234 567,89", formatAmount(123456789, Locale.forLanguageTag("fr-FR")))
        assertEquals("1.234.567,89", formatAmount(123456789, Locale.forLanguageTag("de-DE")))
    }
}
