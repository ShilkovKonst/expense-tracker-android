package com.spendobserver.domain.calculator

/** Selects how the amount-field calculator evaluates `+ - * /` expressions. */
enum class CalculatorMode {
    /** `5+10/2` = `(5+10)/2` = 7.5 — matches the original web app's default behavior. */
    LEFT_TO_RIGHT,

    /** `5+10/2` = `5+(10/2)` = 10 — standard algebraic operator precedence. */
    ALGEBRAIC,
}
