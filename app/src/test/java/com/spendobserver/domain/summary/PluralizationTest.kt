package com.spendobserver.domain.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralizationTest {

    @Test
    fun oneRecord_singular() {
        assertEquals("запись", recordsWord(1))
        assertEquals("запись", recordsWord(21))
    }

    @Test
    fun twoToFourRecords_few() {
        assertEquals("записи", recordsWord(2))
        assertEquals("записи", recordsWord(3))
        assertEquals("записи", recordsWord(4))
        assertEquals("записи", recordsWord(23))
    }

    @Test
    fun fiveToTwenty_many() {
        assertEquals("записей", recordsWord(0))
        assertEquals("записей", recordsWord(5))
        assertEquals("записей", recordsWord(11))
        assertEquals("записей", recordsWord(12))
        assertEquals("записей", recordsWord(14))
    }
}
