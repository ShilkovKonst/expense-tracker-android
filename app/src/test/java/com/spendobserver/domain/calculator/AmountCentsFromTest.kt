package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountCentsFromTest {

    @Test
    fun plainNumber_convertsToCents() {
        assertEquals(1234L, amountCentsFrom("12.34"))
    }

    @Test
    fun expression_isEvaluatedBeforeConverting() {
        assertEquals(15000L, amountCentsFrom("100+50"))
    }

    @Test
    fun floatingPointNoise_roundsToNearestCent() {
        // 0.1 + 0.2 is 0.30000000000000004 in IEEE 754 double math.
        assertEquals(30L, amountCentsFrom("0.1+0.2"))
    }

    @Test
    fun emptyInput_returnsNull() {
        assertNull(amountCentsFrom(""))
    }

    @Test
    fun zeroOrNegativeResult_returnsNull() {
        assertNull(amountCentsFrom("0"))
        assertNull(amountCentsFrom("5-10"))
    }

    @Test
    fun commaDecimalSeparator_convertsToCents() {
        assertEquals(12526L, amountCentsFrom("125,26"))
    }

    @Test
    fun respectsSelectedCalculatorMode() {
        // (5+10)/2 = 7.5 left-to-right, vs 5+(10/2) = 10 with algebraic precedence.
        assertEquals(750L, amountCentsFrom("5+10/2", CalculatorMode.LEFT_TO_RIGHT))
        assertEquals(1000L, amountCentsFrom("5+10/2", CalculatorMode.ALGEBRAIC))
    }
}
