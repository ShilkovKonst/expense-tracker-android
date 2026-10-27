package com.spendobserver.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterAmountInputTest {

    private fun assertFiltered(expected: String, input: String) =
        assertEquals("«$input»", expected, filterAmountInput(input))

    @Test
    fun keepsDigitsOperatorsParenthesesAndSeparators() {
        assertFiltered("(12,5+3)*2-1/4", "(12,5+3)*2-1/4")
        assertFiltered("1.5+2,5", "1.5+2,5")
    }

    @Test
    fun dropsSpacesLettersAndOtherSymbols() {
        assertFiltered("1000,50", " 1 000,50 €")
        assertFiltered("10", "10%")
        assertFiltered("5+3", "5+3=")
        assertFiltered("123", "1a2b3")
        // The no-break spaces the app itself groups thousands with in ru/fr.
        assertFiltered("1234", "1 234 ")
    }

    @Test
    fun readsKeyboardSignsAsOperators() {
        assertFiltered("5*3/2-1", "5×3÷2−1")
    }

    @Test
    fun anOperatorAfterAnother_replacesIt() {
        assertFiltered("5*3", "5+*3")
        assertFiltered("5/3", "5+-*/3")
        assertFiltered("5-", "5*-")
        assertFiltered("(5+3)*2", "(5+3)+*2")
    }

    @Test
    fun onlyAMinus_canOpenTheExpressionOrAParenthesis() {
        assertFiltered("5", "+5")
        assertFiltered("5", "*/5")
        assertFiltered("-5", "-5")
        assertFiltered("2*(5", "2*(+5")
        assertFiltered("2*(-5)", "2*(-5)")
        // A minus there replaced by "*" leaves nothing: "*" may not follow "(".
        assertFiltered("(5", "(-*5")
    }

    @Test
    fun aSecondDecimalSeparatorInOneNumber_isDropped() {
        assertFiltered("1.23", "1.2.3")
        assertFiltered("1,55", "1,5.5")
        assertFiltered("1.5+2.5", "1.5+2.5")
        assertFiltered("(1.5)*2.5", "(1.5)*2.5")
    }

    @Test
    fun filteringTwice_changesNothing() {
        for (input in listOf(" 1 000,50 €", "5+-*/3", "(-*5", "1.2.3", "5×3÷2−1", "+(*5")) {
            val once = filterAmountInput(input)
            assertEquals("«$input»", once, filterAmountInput(once))
        }
    }

    @Test
    fun typingKeyByKey_endsWhereFilteringTheWholeTextDoes() {
        // The field re-filters its whole text on every keystroke.
        var field = ""
        for (key in "12+*3.4.5×(2") field = filterAmountInput(field + key)
        assertEquals("12*3.45*(2", field)
    }
}
