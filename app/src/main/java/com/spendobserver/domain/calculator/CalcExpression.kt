package com.spendobserver.domain.calculator

import kotlin.math.floor

private val TOKEN_REGEX = Regex("""(?:\d+\.\d+|\d+\.|\.\d+|\d+)|[+\-*/]""")

/**
 * JS `Math.round` rounds .5 towards +Infinity (round(-2.5) == -2), unlike
 * [kotlin.math.round] which rounds ties to even. Kept separate to preserve
 * exact numeric parity with the original web calculator.
 */
private fun jsRound(value: Double): Double = floor(value + 0.5)

/**
 * Splits [expression] into number/operator tokens, folding a leading unary
 * minus into the first number (e.g. `-5+10` -> `["-5", "+", "10"]`), then
 * drops any trailing run of dangling operators so an incomplete tail never
 * reaches the evaluator as a missing operand (e.g. a string ending in
 * several chained operators collapses back to its last complete number) —
 * that's what used to turn into a silent NaN (and, further downstream in
 * the original web app, a silent 0). Returns `null` when nothing numeric is
 * left to evaluate.
 */
private fun tokenize(expression: String): List<String>? {
    val rawTokens = TOKEN_REGEX.findAll(expression).map { it.value }.toList()
    if (rawTokens.isEmpty()) return null

    val merged = if (rawTokens[0] == "-" && rawTokens.getOrNull(1)?.toDoubleOrNull() != null) {
        listOf(rawTokens[0] + rawTokens[1]) + rawTokens.drop(2)
    } else {
        rawTokens
    }

    val trimmed = merged.dropLastWhile { it.toDoubleOrNull() == null }
    return trimmed.ifEmpty { null }
}

/**
 * Evaluates [expression] left-to-right (no algebraic operator precedence),
 * mirroring the web app's default amount-field calculator. Returns
 * [Double.NaN] for empty/non-numeric input.
 */
fun calcExpressionLeftToRight(expression: String): Double {
    val tokens = tokenize(expression) ?: return Double.NaN
    var result = tokens[0].toDoubleOrNull() ?: return Double.NaN

    for (i in 1 until tokens.size step 2) {
        val op = tokens[i]
        val next = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: Double.NaN
        result = when (op) {
            "+" -> result + next
            "-" -> result - next
            "*" -> result * next
            "/" -> result / next
            else -> result
        }
    }

    return jsRound(result * 100) / 100
}

/**
 * Evaluates [expression] with standard algebraic precedence (`*`/`/` before
 * `+`/`-`), e.g. `5+10/2` = 10. Returns [Double.NaN] for empty/non-numeric
 * input.
 */
fun calcExpressionAlgebraic(expression: String): Double {
    val tokens = tokenize(expression) ?: return Double.NaN
    if (tokens[0].toDoubleOrNull() == null) return Double.NaN

    // First pass: collapse every '*'/'/' with its neighbors, left-to-right,
    // leaving only numbers and +/- for the second pass.
    val collapsed = mutableListOf<String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        if (token == "*" || token == "/") {
            val prev = collapsed.removeLastOrNull()
            if (prev != null && i < tokens.size - 1) {
                i++
                val next = tokens[i]
                val prevValue = prev.toDoubleOrNull()
                val nextValue = next.toDoubleOrNull()
                if (prevValue != null && nextValue != null) {
                    val res = if (token == "*") {
                        jsRound(prevValue * nextValue * 100) / 100
                    } else {
                        jsRound(prevValue / nextValue * 100) / 100
                    }
                    collapsed.add(res.toString())
                }
            }
        } else {
            collapsed.add(token)
        }
        i++
    }

    var result = collapsed[0].toDoubleOrNull() ?: return Double.NaN
    for (j in 1 until collapsed.size step 2) {
        val op = collapsed[j]
        val next = collapsed.getOrNull(j + 1)?.toDoubleOrNull() ?: Double.NaN
        result = when (op) {
            "+" -> result + next
            "-" -> result - next
            else -> result
        }
    }

    return jsRound(result * 100) / 100
}

/** Evaluates [expression] using whichever [CalculatorMode] the user has selected in settings. */
fun calcExpression(expression: String, mode: CalculatorMode = CalculatorMode.LEFT_TO_RIGHT): Double =
    when (mode) {
        CalculatorMode.LEFT_TO_RIGHT -> calcExpressionLeftToRight(expression)
        CalculatorMode.ALGEBRAIC -> calcExpressionAlgebraic(expression)
    }
