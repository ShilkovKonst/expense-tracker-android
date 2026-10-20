package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalcExpressionTest {

    /** An expression and what each mode makes of it; NaN means "not a number". */
    private data class Case(val expression: String, val leftToRight: Double, val algebraic: Double)

    private val cases = listOf(
        Case("10+5", 15.0, 15.0),
        Case("20-8", 12.0, 12.0),
        Case("4*3", 12.0, 12.0),
        Case("10/4", 2.5, 2.5),
        // The one place the modes differ: (5+10)/2 vs 5+(10/2), and (2+3)*4 vs 2+(3*4).
        Case("5+10/2", 7.5, 10.0),
        Case("2+3*4", 20.0, 14.0),
        Case("1", 1.0, 1.0),
        Case("0.5", 0.5, 0.5),
        Case("1.5+2.5", 4.0, 4.0),
        Case("-5+10", 5.0, 5.0),
        // A locale that types "125,26" instead of "125.26" must still parse.
        Case("125,26", 125.26, 125.26),
        Case("1,5+2,5", 4.0, 4.0),
        Case("5/0", Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
        // Dangling operators while typing are dropped, not evaluated against nothing.
        Case("10+", 10.0, 10.0),
        Case("7+3*5-4+*/-", 46.0, 18.0),
        Case("+-*/", Double.NaN, Double.NaN),
        Case("", Double.NaN, Double.NaN),
        Case("abc", Double.NaN, Double.NaN),
    )

    @Test
    fun eachModeEvaluatesEveryCase() {
        for (case in cases) {
            // assertEquals on doubles treats NaN as equal to NaN, and infinities as equal to themselves.
            assertEquals("«${case.expression}» left-to-right", case.leftToRight, calcExpression(case.expression, CalculatorMode.LEFT_TO_RIGHT), 0.0001)
            assertEquals("«${case.expression}» algebraic", case.algebraic, calcExpression(case.expression, CalculatorMode.ALGEBRAIC), 0.0001)
        }
    }

    @Test
    fun defaultsToLeftToRight() {
        assertEquals(7.5, calcExpression("5+10/2"), 0.0001)
    }
}
