package com.spendobserver.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.spendobserver.R
import com.spendobserver.domain.format.formatAmount
import com.spendobserver.domain.format.formatLocale
import com.spendobserver.domain.format.formatSignedAmount
import com.spendobserver.domain.format.monthName
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * The locale amounts and dates are shown in: the language Android picked the
 * texts in (`locale_language` in each `values-xx/strings.xml`) with the user's
 * region for it — see [formatLocale]. Recomposes when the system language changes.
 */
@Composable
fun rememberFormatLocale(): Locale {
    val language = stringResource(R.string.locale_language)
    val locales = LocalConfiguration.current.locales
    return remember(language, locales) { formatLocale(language, List(locales.size()) { locales[it] }) }
}

/** [cents] for display: `1,700.00` / `1 700,00` / `1.700,00`. Never for an input field — see [formatAmount]. */
@Composable
fun amountText(cents: Long): String = formatAmount(cents, rememberFormatLocale())

@Composable
fun signedAmountText(cents: Long): String = formatSignedAmount(cents, rememberFormatLocale())

@Composable
fun monthText(month: Int): String = monthName(month, rememberFormatLocale())

/**
 * [date] as the locale writes a numeric date with a four-digit year: en-US
 * `03/01/2026`, ru/de `01.03.2026`, fr `01/03/2026`. The "best pattern" for the
 * day-month-year skeleton picks the order and separators.
 */
@Composable
fun dateText(date: LocalDate): String {
    val locale = rememberFormatLocale()
    val formatter = remember(locale) { numericDateFormatter(locale) }
    return formatter.format(date.toJavaLocalDate())
}

internal fun numericDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "ddMMyyyy"), locale)
