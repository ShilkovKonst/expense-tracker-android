package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcExpressionAlgebraicTest {

    @Test
    fun respectsAlgebraicOrderOfOperations() {
        // 5+10/2 => 5 + 5 = 10 (division before addition)
        assertEquals(10.0, calcExpressionAlgebraic("5+10/2"), 0.0001)
    }

    @Test
    fun handlesMultiplicationBeforeAddition() {
        // 2+3*4 => 2+12 = 14
        assertEquals(14.0, calcExpressionAlgebraic("2+3*4"), 0.0001)
    }

    @Test
    fun handlesNegativeFirstOperand() {
        assertEquals(5.0, calcExpressionAlgebraic("-5+10"), 0.0001)
    }

    @Test
    fun handlesPlainNumbers() {
        assertEquals(1.0, calcExpressionAlgebraic("1"), 0.0001)
        assertEquals(0.5, calcExpressionAlgebraic("0.5"), 0.0001)
    }

    @Test
    fun returnsNaN_forEmptyString() {
        assertTrue(calcExpressionAlgebraic("").isNaN())
    }

    @Test
    fun returnsNaN_forNonNumericInput() {
        assertTrue(calcExpressionAlgebraic("abc").isNaN())
    }

    @Test
    fun dropsMultipleTrailingOperators_insteadOfNaN() {
        // "7+3*5-4+*/-" -> trailing "+*/-" is dropped, leaving "7+3*5-4"
        // algebraic: 3*5=15 first, then 7+15=22, -4=18
        assertEquals(18.0, calcExpressionAlgebraic("7+3*5-4+*/-"), 0.0001)
    }

    @Test
    fun dispatcher_selectsAlgorithmByMode() {
        assertEquals(7.5, calcExpression("5+10/2", CalculatorMode.LEFT_TO_RIGHT), 0.0001)
        assertEquals(10.0, calcExpression("5+10/2", CalculatorMode.ALGEBRAIC), 0.0001)
    }

    @Test
    fun dispatcher_defaultsToLeftToRight() {
        assertEquals(7.5, calcExpression("5+10/2"), 0.0001)
    }
}
