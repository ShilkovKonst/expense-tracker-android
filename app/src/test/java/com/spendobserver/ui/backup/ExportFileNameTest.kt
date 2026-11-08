package com.spendobserver.ui.backup

import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameTest {

    /** The date part is today's, so only its shape is checked. */
    private fun assertName(prefix: String, actual: String) =
        assertTrue(actual, Regex("""${Regex.escape(prefix)}_\d{4}-\d{2}-\d{2}\.json""").matches(actual))

    @Test
    fun oneTracker_isNamedAfterIt() {
        assertName("домашний", exportFileName(ExportScope.One(1, "домашний")))
    }

    @Test
    fun allTrackers_useTheAppName() {
        assertName("spendobserver", exportFileName(ExportScope.All))
    }

    @Test
    fun charactersAFileSystemRefuses_becomeUnderscores() {
        assertName("a_b_c_d_e_f_g_h_i_j", exportFileName(ExportScope.One(1, """a\b/c:d*e?f"g<h>i|j""")))
    }

    @Test
    fun otherPunctuationAndSpaces_stay() {
        assertName("Дом & дача (2026)", exportFileName(ExportScope.One(1, "Дом & дача (2026)")))
    }
}
