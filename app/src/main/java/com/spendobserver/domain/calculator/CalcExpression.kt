package com.spendobserver.domain.calculator

import kotlin.math.floor

/**
 * JS `Math.round` rounds .5 towards +Infinity (round(-2.5) == -2), unlike
 * [kotlin.math.round] which rounds ties to even. Kept from the original web
 * calculator so a result ending in exactly half a cent still rounds up.
 */
private fun jsRound(value: Double): Double = floor(value + 0.5)

/** Signs a keyboard or a paste may bring, read as the four operators. */
private val OPERATOR_ALIASES = mapOf('×' to '*', '÷' to '/', '−' to '-')

private const val OPERATORS = "+-*/"

private fun Char.isDecimalSeparator() = this == '.' || this == ','

/**
 * Cleans the amount field's text as it's typed (or pasted), so it only ever
 * holds what [calcExpression] understands: digits, one decimal separator
 * per number (a dot or a comma — kept as typed), `+ - * /` (`× ÷ −` become
 * those), and parentheses. Everything else — spaces, letters, `%` — is
 * dropped. Several separators in one number are resolved by [normalizeNumber].
 *
 * An operator right after another one replaces it, as on a pocket
 * calculator: `5+` then `*` gives `5*`, so `5+*3` can't be typed. The only
 * operator allowed at the very start or right after `(` is a minus.
 */
fun filterAmountInput(text: String): String {
    val out = StringBuilder()
    for (raw in text) {
        val c = OPERATOR_ALIASES[raw] ?: raw
        when {
            c in '0'..'9' || c == '(' || c == ')' -> out.append(c)
            c.isDecimalSeparator() -> out.append(c)
            c in OPERATORS -> {
                while (out.isNotEmpty() && out.last() in OPERATORS) out.deleteAt(out.lastIndex)
                val prev = out.lastOrNull()
                if (c == '-' || (prev != null && prev != '(')) out.append(c)
            }
        }
    }
    return NUMBER_RUN.replace(out) { normalizeNumber(it.value) }
}

private val NUMBER_RUN = Regex("[0-9.,]+")

/**
 * Leaves at most one decimal separator in a run of digits and separators.
 *
 * - A separator at the end goes: that's a key just pressed, and the field
 *   re-filters on every keystroke, so a second one simply can't be typed.
 * - What remains with several can only be a paste. A number written with
 *   thousands groups keeps its value: `1,234.56` and `1.234,56` become
 *   `1234.56` and `1234,56`, `1,234,567` becomes `1234567`.
 * - Anything else (`1.2.3`) isn't a way of writing a number, so only the first
 *   separator counts: `1.23`.
 */
private fun normalizeNumber(run: String): String {
    var number = run
    while (number.count { it.isDecimalSeparator() } > 1 && number.last().isDecimalSeparator()) {
        number = number.dropLast(1)
    }
    val separators = number.filter { it.isDecimalSeparator() }
    if (separators.length < 2) return number

    val groups = number.split('.', ',')
    val isGrouped = groups.first().length in 1..3 && groups.subList(1, groups.lastIndex).all { it.length == 3 }
    val grouping = separators.dropLast(1)
    if (isGrouped && grouping.all { it == grouping.first() }) {
        val digitsBeforeLast = groups.dropLast(1).joinToString("")
        when {
            separators.last() != grouping.first() -> return digitsBeforeLast + separators.last() + groups.last()
            groups.last().length == 3 -> return digitsBeforeLast + groups.last()
        }
    }
    val first = number.indexOfFirst { it.isDecimalSeparator() }
    return number.take(first + 1) + number.drop(first + 1).filterNot { it.isDecimalSeparator() }
}

private sealed interface Token {
    data class Number(val value: Double) : Token
    data class Operator(val symbol: Char) : Token
    data object Open : Token
    data object Close : Token
}

/**
 * Splits [expression] into tokens, or `null` if it holds anything
 * [filterAmountInput] wouldn't let through. A comma counts as a decimal
 * point (`125,26` = `125.26`).
 *
 * The unfinished tail of an expression being typed is forgiven: trailing
 * operators and `(` are dropped, and parentheses still open at the end are
 * closed — `10+` reads as `10`, `(5+3` as `(5+3)`. Anything malformed
 * *before* the end (`5+*3`, `1.2.3`, `)5(`) is left for the parser to reject.
 */
private fun tokenize(expression: String): List<Token>? {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < expression.length) {
        val c = OPERATOR_ALIASES[expression[i]] ?: expression[i]
        when {
            c.isDigit() || c.isDecimalSeparator() -> {
                val start = i
                var separators = 0
                while (i < expression.length && (expression[i].isDigit() || expression[i].isDecimalSeparator())) {
                    if (expression[i].isDecimalSeparator() && ++separators > 1) break
                    i++
                }
                val number = expression.substring(start, i).replace(',', '.')
                tokens += Token.Number(number.toDoubleOrNull() ?: return null)
                continue
            }
            c in OPERATORS -> tokens += Token.Operator(c)
            c == '(' -> tokens += Token.Open
            c == ')' -> tokens += Token.Close
            else -> return null
        }
        i++
    }

    while (tokens.lastOrNull().let { it is Token.Operator || it == Token.Open }) tokens.removeAt(tokens.lastIndex)
    val unclosed = tokens.count { it == Token.Open } - tokens.count { it == Token.Close }
    repeat(unclosed) { tokens += Token.Close }
    return tokens.ifEmpty { null }
}

/**
 * Recursive descent over [tokens]. Algebraic grammar:
 *
 * ```
 * expr   = term   (("+" | "-") term)*
 * term   = factor (("*" | "/") factor)*
 * factor = "-" factor | "(" expr ")" | number
 * ```
 *
 * [CalculatorMode.LEFT_TO_RIGHT] collapses `expr` and `term` into one level,
 * so all four operators chain in typing order; parentheses still group.
 * Any token out of place throws [MalformedExpression].
 */
private class Parser(private val tokens: List<Token>, private val mode: CalculatorMode) {
    private var pos = 0
    private var depth = 0

    fun parse(): Double {
        val value = expr()
        if (pos != tokens.size) throw MalformedExpression
        return value
    }

    private fun peekOperator(symbols: String): Char? =
        (tokens.getOrNull(pos) as? Token.Operator)?.symbol?.takeIf { it in symbols }

    private fun expr(): Double = when (mode) {
        CalculatorMode.ALGEBRAIC -> chain("+-", ::term)
        CalculatorMode.LEFT_TO_RIGHT -> chain(OPERATORS, ::factor)
    }

    private fun term(): Double = chain("*/", ::factor)

    /** `operand (op operand)*` for the operators in [symbols], applied left to right. */
    private fun chain(symbols: String, operand: () -> Double): Double {
        var result = operand()
        while (true) {
            val op = peekOperator(symbols) ?: return result
            pos++
            val next = operand()
            result = when (op) {
                '+' -> result + next
                '-' -> result - next
                '*' -> result * next
                else -> result / next
            }
        }
    }

    private fun factor(): Double {
        if (++depth > MAX_NESTING) throw MalformedExpression
        val value = when (val token = tokens.getOrNull(pos++)) {
            is Token.Number -> token.value
            Token.Operator('-') -> -factor()
            Token.Open -> expr().also { if (tokens.getOrNull(pos++) != Token.Close) throw MalformedExpression }
            else -> throw MalformedExpression
        }
        depth--
        return value
    }
}

/** Beyond any real expression; a pasted wall of `(((` must end in "malformed", not a stack overflow. */
private const val MAX_NESTING = 100

/** Thrown by [Parser] only, caught right away by [calcExpression]; no stack trace needed. */
private object MalformedExpression : RuntimeException(null, null, false, false)

/**
 * Evaluates the amount field's [expression], rounded to cents only once, at
 * the end — so `10/3*3` is 10, not 9.99. [Double.NaN] for anything that
 * isn't a complete expression (empty, `5+*3`, a stray character); division
 * by zero gives an infinity. Both are refused by [amountCentsFrom].
 */
fun calcExpression(expression: String, mode: CalculatorMode = CalculatorMode.ALGEBRAIC): Double {
    val tokens = tokenize(expression) ?: return Double.NaN
    val value = try {
        Parser(tokens, mode).parse()
    } catch (_: MalformedExpression) {
        return Double.NaN
    }
    return jsRound(value * 100) / 100
}

/**
 * The largest amount of one part: 100 billion. Far beyond any real expense, and
 * low enough that sums of parts neither overflow a `Long` (SQLite's `SUM()` would
 * throw on every read of the totals) nor lose precision in a `Double`.
 */
const val MAX_AMOUNT_CENTS = 10_000_000_000_000L

/**
 * Evaluates the amount field's calculator [expression] into whole cents for
 * storage as [com.spendobserver.data.local.entity.RecordPart.amountCents].
 * Returns `null` for anything that isn't a finite amount of zero or more
 * (empty input, a malformed expression, a negative result) or that's over
 * [MAX_AMOUNT_CENTS]. Zero is a real
 * amount: work logged without pay. Amounts are never negative, with
 * income/cost conveyed separately by
 * [com.spendobserver.data.local.entity.RecordType].
 */
fun amountCentsFrom(expression: String, mode: CalculatorMode = CalculatorMode.ALGEBRAIC): Long? {
    val value = calcExpression(expression, mode)
    if (!value.isFinite() || value < 0.0) return null
    return Math.round(value * 100).takeIf { it <= MAX_AMOUNT_CENTS }
}
