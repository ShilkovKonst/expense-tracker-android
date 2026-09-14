package com.spendobserver.domain.meta

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MetaDateFormatTest {

    @Test
    fun formatsKnownDateCorrectly() {
        val dateTime = LocalDateTime(2024, 1, 5, 14, 30, 45)

        assertEquals("05/01/2024_14:30:45", formatDateToMeta(dateTime))
    }

    @Test
    fun padsSingleDigitValues() {
        val dateTime = LocalDateTime(2024, 3, 3, 1, 2, 3)

        assertEquals("03/03/2024_01:02:03", formatDateToMeta(dateTime))
    }

    @Test
    fun roundTripsWithFormatDateToMeta() {
        val original = LocalDateTime(2024, 6, 15, 10, 20, 30)

        val parsed = parseMetaToDate(formatDateToMeta(original))

        assertEquals(original, parsed)
    }

    @Test
    fun returnsNull_forNullInput() {
        assertNull(parseMetaToDate(null))
    }

    @Test
    fun returnsNull_forEmptyString() {
        assertNull(parseMetaToDate(""))
    }

    @Test
    fun throws_forInvalidFormat() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            parseMetaToDate("invalid")
        }
        assertEquals("Invalid meta date format: invalid", exception.message)
    }
}
