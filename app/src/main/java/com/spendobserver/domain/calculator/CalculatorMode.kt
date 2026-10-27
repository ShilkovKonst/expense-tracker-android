package com.spendobserver.domain.calculator

/** Selects how the amount-field calculator evaluates `+ - * /` expressions. Parentheses group in both. */
enum class CalculatorMode {
    /** `5+10/2` = `5+(10/2)` = 10 — standard operator precedence; the app's default. */
    ALGEBRAIC,

    /** `5+10/2` = `(5+10)/2` = 7.5 — strictly in typing order, as the original web app did by default. */
    LEFT_TO_RIGHT,
}
