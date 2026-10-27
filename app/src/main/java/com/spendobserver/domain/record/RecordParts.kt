package com.spendobserver.domain.record

import com.spendobserver.domain.calculator.CalculatorMode
import com.spendobserver.domain.calculator.amountCentsFrom
import com.spendobserver.domain.calculator.filterAmountInput
import com.spendobserver.domain.summary.formatCents

/** One part of a record as it's saved: an amount and its local tags. */
data class PartDraft(val amountCents: Long, val tagIds: Set<Long>)

/**
 * One part as the user is editing it. [id] is a form-local key (stable
 * across edits, so the UI can address a part even as others are added or
 * removed) — unrelated to the database id. [amountExpression] is raw
 * calculator input.
 */
data class PartInput(
    val id: Int,
    val amountExpression: String = "",
    val tagIds: Set<Long> = emptySet(),
)

/**
 * The editable parts of a record (always at least one), optionally with a
 * receipt total.
 *
 * - No [receiptTotalExpression], one part: a "simple" record — the form
 *   shows a single amount field, like before parts existed.
 * - With a [receiptTotalExpression]: the **last** part is the remainder —
 *   its amount is never typed but computed as the receipt total minus all
 *   other parts, so the user only types the parts they want to single out.
 *
 * Every operation returns a new value; this class never mutates.
 */
data class PartsInput(
    val parts: List<PartInput> = listOf(PartInput(id = 0)),
    val receiptTotalExpression: String? = null,
) {
    init {
        require(parts.isNotEmpty()) { "A record needs at least one part" }
    }

    val isSplit: Boolean get() = parts.size > 1 || receiptTotalExpression != null

    /** The part whose amount is computed, or `null` when there's no receipt total. */
    val remainderPartId: Int? get() = if (receiptTotalExpression != null) parts.last().id else null

    private fun nextId() = parts.maxOf { it.id } + 1

    /**
     * Simple → split: whatever was typed into the single amount field
     * becomes the receipt total, the existing part (with its tags) becomes
     * the remainder, and a fresh part is added before it to single out.
     */
    fun split(): PartsInput {
        if (isSplit) return this
        val only = parts.single()
        return PartsInput(
            parts = listOf(PartInput(id = nextId()), only.copy(amountExpression = "")),
            receiptTotalExpression = only.amountExpression,
        )
    }

    /** A new empty part — before the remainder, which always stays last. */
    fun addPart(): PartsInput {
        val newPart = PartInput(id = nextId())
        val newParts = if (receiptTotalExpression != null) {
            parts.dropLast(1) + newPart + parts.last()
        } else {
            parts + newPart
        }
        return copy(parts = newParts)
    }

    /** Ignored for the remainder and for the last remaining part. */
    fun removePart(id: Int): PartsInput {
        if (parts.size == 1 || id == remainderPartId) return this
        return copy(parts = parts.filterNot { it.id == id })
    }

    /** [expression] is stored [filterAmountInput]-ed: a field only ever shows what the calculator accepts. */
    fun setPartAmount(id: Int, expression: String): PartsInput {
        val filtered = filterAmountInput(expression)
        return copy(parts = parts.map { if (it.id == id) it.copy(amountExpression = filtered) else it })
    }

    fun togglePartTag(id: Int, tagId: Long): PartsInput = copy(
        parts = parts.map { part ->
            if (part.id != id) part else part.copy(tagIds = if (tagId in part.tagIds) part.tagIds - tagId else part.tagIds + tagId)
        },
    )

    /** Filtered like [setPartAmount]. */
    fun setReceiptTotal(expression: String): PartsInput = copy(receiptTotalExpression = filterAmountInput(expression))

    /**
     * Drops the receipt total; the remainder part keeps the amount it was
     * showing (as a typed value now), so nothing visibly jumps. With a
     * single part left this turns the record back into a simple one.
     */
    fun clearReceiptTotal(mode: CalculatorMode = CalculatorMode.ALGEBRAIC): PartsInput {
        val remainderId = remainderPartId ?: return this
        val remainder = evaluate(mode).partCents[remainderId]
        return PartsInput(
            parts = parts.map { if (it.id == remainderId) it.copy(amountExpression = remainder?.let(::formatCents) ?: "") else it },
            receiptTotalExpression = null,
        )
    }

    fun evaluate(mode: CalculatorMode = CalculatorMode.ALGEBRAIC): PartsEvaluation {
        val errors = mutableListOf<PartsError>()
        val typedCents = parts
            .filter { it.id != remainderPartId }
            .associate { it.id to amountCentsFrom(it.amountExpression, mode) }
        typedCents.forEach { (id, cents) -> if (cents == null) errors += PartsError.InvalidAmount(id) }

        val partCents: Map<Int, Long?>
        val totalCents: Long?
        val remainderId = remainderPartId
        if (remainderId == null) {
            partCents = typedCents
            totalCents = if (typedCents.values.any { it == null }) null else typedCents.values.sumOf { it!! }
        } else {
            val receiptCents = amountCentsFrom(receiptTotalExpression.orEmpty(), mode)
            if (receiptCents == null) errors += PartsError.InvalidReceiptTotal
            val typedSum = if (typedCents.values.any { it == null }) null else typedCents.values.sumOf { it!! }
            val remainder = if (receiptCents != null && typedSum != null) receiptCents - typedSum else null
            if (remainder != null && remainder <= 0) errors += PartsError.PartsExceedReceiptTotal
            partCents = typedCents + (remainderId to remainder?.takeIf { it > 0 })
            totalCents = receiptCents
        }

        parts.groupBy { it.tagIds }.values
            .filter { it.size > 1 }
            .forEach { sameTags -> errors += PartsError.DuplicateTags(sameTags.map { it.id }) }

        return PartsEvaluation(partCents = partCents, totalCents = totalCents, errors = errors)
    }

    /** The parts to save, or `null` while [evaluate] reports any error. */
    fun toDrafts(mode: CalculatorMode = CalculatorMode.ALGEBRAIC): List<PartDraft>? {
        val evaluation = evaluate(mode)
        if (evaluation.errors.isNotEmpty()) return null
        return parts.map { PartDraft(amountCents = evaluation.partCents.getValue(it.id)!!, tagIds = it.tagIds) }
    }

    companion object {
        /** Editing an existing record: every saved part becomes a typed one (receipt totals aren't stored). */
        fun fromSaved(parts: List<PartDraft>): PartsInput = PartsInput(
            parts = parts.mapIndexed { index, draft ->
                PartInput(id = index, amountExpression = formatCents(draft.amountCents), tagIds = draft.tagIds)
            },
        )
    }
}

/**
 * [partCents] holds every part's amount (the remainder's included), `null`
 * where it can't be computed yet; [totalCents] is the record's total —
 * the receipt total when there is one, otherwise the sum of the parts.
 */
data class PartsEvaluation(
    val partCents: Map<Int, Long?>,
    val totalCents: Long?,
    val errors: List<PartsError>,
)

sealed interface PartsError {
    /** A typed part amount is empty, malformed, or not positive. */
    data class InvalidAmount(val partId: Int) : PartsError

    data object InvalidReceiptTotal : PartsError

    /** The typed parts leave nothing (or less than nothing) for the remainder. */
    data object PartsExceedReceiptTotal : PartsError

    /** Parts with identical tag sets are the same category and should be one part. */
    data class DuplicateTags(val partIds: List<Int>) : PartsError
}
