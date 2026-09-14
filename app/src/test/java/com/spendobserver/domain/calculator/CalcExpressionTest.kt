package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcExpressionTest {

    @Test
    fun evaluatesSimpleAddition() {
        assertEquals(15.0, calcExpression("10+5"), 0.0001)
    }

    @Test
    fun evaluatesSubtraction() {
        assertEquals(12.0, calcExpression("20-8"), 0.0001)
    }

    @Test
    fun evaluatesMultiplication() {
        assertEquals(12.0, calcExpression("4*3"), 0.0001)
    }

    @Test
    fun evaluatesDivision() {
        assertEquals(2.5, calcExpression("10/4"), 0.0001)
    }

    @Test
    fun evaluatesLeftToRight_noAlgebraicPrecedence() {
        // left-to-right: (5+10)/2 = 7.5, NOT 5+(10/2)=10
        assertEquals(7.5, calcExpression("5+10/2"), 0.0001)
    }

    @Test
    fun handlesNegativeFirstOperand() {
        assertEquals(5.0, calcExpression("-5+10"), 0.0001)
    }

    @Test
    fun handlesDecimals() {
        assertEquals(4.0, calcExpression("1.5+2.5"), 0.0001)
    }

    @Test
    fun returnsNaN_forEmptyString() {
        assertTrue(calcExpression("").isNaN())
    }

    @Test
    fun returnsNaN_forNonNumericInput() {
        assertTrue(calcExpression("abc").isNaN())
    }

    @Test
    fun returnsInfinity_forDivisionByZero() {
        assertEquals(Double.POSITIVE_INFINITY, calcExpression("5/0"), 0.0)
    }

    @Test
    fun dropsSingleTrailingOperator_insteadOfNaN() {
        // "10+" -> the dangling '+' is ignored, not evaluated as +undefined
        assertEquals(10.0, calcExpression("10+"), 0.0001)
    }

    @Test
    fun dropsMultipleTrailingOperators_insteadOfNaN() {
        // "7+3*5-4+*/-" -> trailing "+*/-" is dropped, leaving "7+3*5-4"
        // left-to-right: 7+3=10, *5=50, -4=46
        assertEquals(46.0, calcExpression("7+3*5-4+*/-"), 0.0001)
    }

    @Test
    fun returnsNaN_whenNothingNumericSurvivesTrimming() {
        assertTrue(calcExpression("+-*/").isNaN())
    }
}
