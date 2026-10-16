package com.spendobserver.testing

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

/*
 * UI texts as the app shows them on this device. Tests look nodes up by these
 * instead of literal strings, so they pass whatever the emulator's language is.
 */

private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)

fun plural(@PluralsRes id: Int, count: Int): String = resources.getQuantityString(id, count, count)
