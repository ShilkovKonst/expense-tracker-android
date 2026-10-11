package com.spendobserver.domain.text

import org.junit.Assert.assertEquals
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
}
