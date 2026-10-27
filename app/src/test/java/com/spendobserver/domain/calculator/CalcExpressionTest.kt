package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalcExpressionTest {

    /** An expression and what each mode makes of it; NaN means "not a number". */
    private data class Case(val expression: String, val algebraic: Double, val leftToRight: Double)

    private val cases = listOf(
        Case("10+5", 15.0, 15.0),
        Case("20-8", 12.0, 12.0),
        Case("4*3", 12.0, 12.0),
        Case("10/4", 2.5, 2.5),
        // The one place the modes differ: 5+(10/2) vs (5+10)/2, and 2+(3*4) vs (2+3)*4.
        Case("5+10/2", 10.0, 7.5),
        Case("2+3*4", 14.0, 20.0),
        Case("10-2*3", 4.0, 24.0),
        Case("1", 1.0, 1.0),
        Case("0.5", 0.5, 0.5),
        Case(".5", 0.5, 0.5),
        Case("12.", 12.0, 12.0),
        Case("1.5+2.5", 4.0, 4.0),
        Case("-5+10", 5.0, 5.0),
        // A locale that types "125,26" instead of "125.26" must still parse.
        Case("125,26", 125.26, 125.26),
        Case("1,5+2,5", 4.0, 4.0),
        Case("5/0", Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),

        // Parentheses group in both modes.
        Case("(5+3)*2", 16.0, 16.0),
        Case("2*(3+4)", 14.0, 14.0),
        Case("10-(2+3)", 5.0, 5.0),
        Case("-(2+3)+10", 5.0, 5.0),
        Case("((1+1)*(2+3))/2", 5.0, 5.0),

        // Signs a keyboard or a paste brings in.
        Case("5×3", 15.0, 15.0),
        Case("10÷4", 2.5, 2.5),
        Case("8−3", 5.0, 5.0),

        // Rounded to cents once, at the end — not after each step (that made 10/3*3 = 9.99).
        Case("10/3*3", 10.0, 10.0),
        Case("1/3", 0.33, 0.33),
        Case("0.125", 0.13, 0.13),

        // An unfinished tail while typing is forgiven: dangling operators and "(" are
        // dropped, open parentheses closed.
        Case("10+", 10.0, 10.0),
        Case("7+3*5-4+*/-", 18.0, 46.0),
        Case("5*(", 5.0, 5.0),
        Case("(5+3", 8.0, 8.0),
        Case("2*(3+4", 14.0, 14.0),

        // Anything malformed before the end is not a number — never a silently wrong amount.
        Case("5+*3", Double.NaN, Double.NaN),
        Case("1.2.3", Double.NaN, Double.NaN),
        Case("1,234.56", Double.NaN, Double.NaN),
        Case("(5+3))", Double.NaN, Double.NaN),
        Case(")5", Double.NaN, Double.NaN),
        Case("()", Double.NaN, Double.NaN),
        Case("5(3)", Double.NaN, Double.NaN),
        Case("+-*/", Double.NaN, Double.NaN),
        Case("", Double.NaN, Double.NaN),
        Case(".", Double.NaN, Double.NaN),
        // Characters the field's filter drops are refused here too.
        Case("5 + 3", Double.NaN, Double.NaN),
        Case("10%", Double.NaN, Double.NaN),
        Case("12abc3", Double.NaN, Double.NaN),
        Case("abc", Double.NaN, Double.NaN),
    )

    @Test
    fun eachModeEvaluatesEveryCase() {
        for (case in cases) {
            // assertEquals on doubles treats NaN as equal to NaN, and infinities as equal to themselves.
            assertEquals("«${case.expression}» algebraic", case.algebraic, calcExpression(case.expression, CalculatorMode.ALGEBRAIC), 0.0001)
            assertEquals("«${case.expression}» left-to-right", case.leftToRight, calcExpression(case.expression, CalculatorMode.LEFT_TO_RIGHT), 0.0001)
        }
    }

    @Test
    fun defaultsToAlgebraic() {
        assertEquals(10.0, calcExpression("5+10/2"), 0.0001)
    }
}
