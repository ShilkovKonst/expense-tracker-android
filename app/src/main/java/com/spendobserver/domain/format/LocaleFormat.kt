package com.spendobserver.domain.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/*
 * Locale-aware display of amounts and month names. Display only: amount input
 * fields keep the calculator's plain `1234.56` (see formatCents), since a
 * grouped value such as German "1.234,56" would read back as 1.234.
 */

/**
 * The locale to format with. [resolvedLanguage] is the language Android picked
 * the app's texts in (it falls back to English for an unsupported system
 * language); [preferred] is the user's language list in order. The first
 * preferred locale of that language keeps its region (en-GB dates stay
 * day-first); with none, the bare language is used — formats then match the
 * texts instead of the unsupported system language.
 */
fun formatLocale(resolvedLanguage: String, preferred: List<Locale>): Locale =
    preferred.firstOrNull { it.language == resolvedLanguage } ?: Locale.forLanguageTag(resolvedLanguage)

/** [cents] with the locale's grouping and decimal separators, always two decimals: en `1,234.56`, de `1.234,56`. */
fun formatAmount(cents: Long, locale: Locale): String =
    // BigDecimal keeps the cents exact; a Double such as 0.29 is really 0.28999….
    amountFormat(locale, fractionDigits = 2).format(BigDecimal.valueOf(cents, 2))

/** Same as [formatAmount] with a "+" before positive values, for a net total or a signed record amount. */
fun formatSignedAmount(cents: Long, locale: Locale): String =
    (if (cents > 0) "+" else "") + formatAmount(cents, locale)

/** A whole [value] with the locale's grouping (en `12,000`), for chart axis steps. */
fun formatWholeNumber(value: Long, locale: Locale): String =
    amountFormat(locale, fractionDigits = 0).format(value)

/** The month's name as a standalone word, capitalized for a header: ru `Март`, fr `Mars`. */
fun monthName(month: Int, locale: Locale): String =
    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale).capitalized(locale)

/** The month's short name for a chart axis, as the locale abbreviates it. */
fun shortMonthName(month: Int, locale: Locale): String =
    Month.of(month).getDisplayName(TextStyle.SHORT_STANDALONE, locale)

private fun amountFormat(locale: Locale, fractionDigits: Int): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = true
    }

private fun String.capitalized(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
