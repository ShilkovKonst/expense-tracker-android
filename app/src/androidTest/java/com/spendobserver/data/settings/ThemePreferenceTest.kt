package com.spendobserver.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(ThemePreference.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit(commit = true) { clear() }
    }

    @Test
    fun nothingStored_followsTheSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemePreference(context).mode.value)
    }

    @Test
    fun choice_isVisibleAtOnceAndSurvivesRestart() {
        val preference = ThemePreference(context)
        preference.set(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, preference.mode.value)
        assertEquals(ThemeMode.DARK, ThemePreference(context).mode.value)
    }

    @Test
    fun unknownStoredValue_followsTheSystem() {
        prefs.edit(commit = true) { putString(ThemePreference.KEY_THEME_MODE, "SEPIA") }

        assertEquals(ThemeMode.SYSTEM, ThemePreference(context).mode.value)
    }
}
