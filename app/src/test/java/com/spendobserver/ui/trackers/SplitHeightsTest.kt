package com.spendobserver.ui.trackers

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitHeightsTest {

    @Test
    fun bothOverHalf_splitEqually() {
        assertEquals(500 to 500, splitHeights(first = 900, second = 600, available = 1000))
    }

    @Test
    fun bothUnderHalf_splitEqually() {
        assertEquals(500 to 500, splitHeights(first = 100, second = 300, available = 1000))
    }

    @Test
    fun oneUnderHalf_keepsItsHeight_andTheOtherGetsTheRest() {
        assertEquals(800 to 200, splitHeights(first = 2000, second = 200, available = 1000))
        assertEquals(200 to 800, splitHeights(first = 200, second = 2000, available = 1000))
    }

    @Test
    fun smallerBlockNearHalf_movesSmoothlyIntoAnEqualSplit() {
        assertEquals(501 to 499, splitHeights(first = 2000, second = 499, available = 1000))
        assertEquals(500 to 500, splitHeights(first = 2000, second = 500, available = 1000))
    }

    @Test
    fun oddHeight_isSharedWithoutLosingAPixel() {
        assertEquals(500 to 501, splitHeights(first = 900, second = 900, available = 1001))
    }
}
