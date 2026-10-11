package com.spendobserver.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimInvisibleTest {

    @Test
    fun trimsOrdinaryWhitespace() {
        assertEquals("Family", "  Family\t\n".trimInvisible())
    }

    @Test
    fun trimsNonBreakingSpace() {
        assertEquals("Family", " Family ".trimInvisible())
    }

    @Test
    fun trimsZeroWidthCharactersAndBom() {
        assertEquals("Family", "﻿​Family‍⁠".trimInvisible())
    }

    @Test
    fun keepsCharactersInside() {
        assertEquals("Моя семья", " Моя семья ".trimInvisible())
    }

    @Test
    fun onlyInvisibleCharacters_becomeEmpty() {
        assertEquals("", "​  ".trimInvisible())
    }

    @Test
    fun sameName_ignoresCaseIncludingCyrillic() {
        assertTrue("Семья".isSameNameAs("СЕМЬЯ"))
        assertTrue("Family".isSameNameAs("family"))
    }

    @Test
    fun sameName_ignoresInvisibleEdges() {
        assertTrue(" Ашан​".isSameNameAs("ашан"))
    }

    @Test
    fun sameName_differentLettersStayDifferent() {
        assertFalse("ёлка".isSameNameAs("елка"))
        assertFalse("Family".isSameNameAs("Family2"))
    }
}
