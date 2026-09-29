package com.spendobserver.domain.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {

    @Test
    fun formatCents_positive() {
        assertEquals("12.34", formatCents(1234))
    }

    @Test
    fun formatCents_negative_prefixesMinus() {
        assertEquals("-12.34", formatCents(-1234))
    }

    @Test
    fun formatCents_zero_noSign() {
        assertEquals("0.00", formatCents(0))
    }

    @Test
    fun formatSignedCents_positive_prefixesPlus() {
        assertEquals("+12.34", formatSignedCents(1234))
    }

    @Test
    fun formatSignedCents_negative_reusesMinusFromFormatCents() {
        assertEquals("-12.34", formatSignedCents(-1234))
    }

    @Test
    fun formatSignedCents_zero_noSign() {
        assertEquals("0.00", formatSignedCents(0))
    }
}
