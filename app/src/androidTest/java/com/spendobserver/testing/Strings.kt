package com.spendobserver.testing

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.spendobserver.R
import com.spendobserver.domain.format.formatAmount
import com.spendobserver.domain.format.formatLocale
import com.spendobserver.domain.format.formatSignedAmount
import com.spendobserver.domain.format.monthName

/*
 * UI texts as the app shows them on this device. Tests look nodes up by these
 * instead of literal strings, so they pass whatever the emulator's language is.
 */

private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)

fun plural(@PluralsRes id: Int, count: Int): String = resources.getQuantityString(id, count, count)

/** The locale the app formats amounts and dates in on this device (see rememberFormatLocale). */
private val formatLocale
    get() = resources.configuration.locales.let { locales ->
        formatLocale(string(R.string.locale_language), List(locales.size()) { locales[it] })
    }

/** [cents] as a display text shows it — not as an amount input field holds it (that stays `1234.56`). */
fun amount(cents: Long): String = formatAmount(cents, formatLocale)

fun signedAmount(cents: Long): String = formatSignedAmount(cents, formatLocale)

fun month(month: Int): String = monthName(month, formatLocale)
