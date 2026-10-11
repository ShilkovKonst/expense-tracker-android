package com.spendobserver.domain.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordPartsTest {

    private val groceries = 1L
    private val alcohol = 2L
    private val snacks = 3L

    /** The Ашан receipt: total 3450, alcohol 800 and snacks 300 singled out, groceries are the rest. */
    private fun ashanReceipt(): PartsInput {
        var input = PartsInput()
            .setPartAmount(0, "3450")
            .togglePartTag(0, groceries)
            .split()
        val alcoholPart = input.parts.first().id
        input = input.setPartAmount(alcoholPart, "800").togglePartTag(alcoholPart, alcohol).addPart()
        val snacksPart = input.parts[1].id
        return input.setPartAmount(snacksPart, "300").togglePartTag(snacksPart, snacks)
    }

    @Test
    fun newInput_isASimpleSinglePart() {
        val input = PartsInput()

        assertFalse(input.isSplit)
        assertEquals(1, input.parts.size)
        assertNull(input.remainderPartId)
    }

    @Test
    fun simpleRecord_savesOnePartWithItsTags() {
        val input = PartsInput().setPartAmount(0, "120+30").togglePartTag(0, groceries)

        assertEquals(listOf(PartDraft(15000, setOf(groceries))), input.toDrafts())
    }

    @Test
    fun simpleRecord_invalidAmount_hasErrorAndNoDrafts() {
        val input = PartsInput().setPartAmount(0, "")

        assertEquals(listOf(PartsError.InvalidAmount(0)), input.evaluate().errors)
        assertNull(input.toDrafts())
    }

    @Test
    fun split_typedAmountBecomesReceiptTotal_andExistingPartBecomesRemainderKeepingItsTags() {
        val input = PartsInput().setPartAmount(0, "3450").togglePartTag(0, groceries).split()

        assertTrue(input.isSplit)
        assertEquals("3450", input.receiptTotalExpression)
        assertEquals(2, input.parts.size)
        assertEquals(0, input.remainderPartId)
        assertEquals(setOf(groceries), input.parts.last().tagIds)
    }

    @Test
    fun receipt_remainderIsTotalMinusTypedParts() {
        val evaluation = ashanReceipt().evaluate()

        assertEquals(listOf<PartsError>(), evaluation.errors)
        assertEquals(345000L, evaluation.totalCents)
        assertEquals(235000L, evaluation.partCents[0])
    }

    @Test
    fun receipt_draftsKeepFormOrderWithRemainderLast() {
        val drafts = ashanReceipt().toDrafts()

        assertEquals(
            listOf(
                PartDraft(80000, setOf(alcohol)),
                PartDraft(30000, setOf(snacks)),
                PartDraft(235000, setOf(groceries)),
            ),
            drafts,
        )
    }

    @Test
    fun addPart_insertsBeforeTheRemainder() {
        val input = ashanReceipt().addPart()

        assertEquals(0, input.parts.last().id)
        assertEquals(4, input.parts.size)
    }

    @Test
    fun partsReachingTheReceiptTotal_isAnError() {
        val input = PartsInput().setPartAmount(0, "1000").split().let { it.setPartAmount(it.parts.first().id, "1000") }

        assertTrue(PartsError.PartsExceedReceiptTotal in input.evaluate().errors)
        assertNull(input.evaluate().partCents[0])
    }

    @Test
    fun invalidReceiptTotal_isAnError() {
        val input = PartsInput().split().let { it.setPartAmount(it.parts.first().id, "100") }

        assertTrue(PartsError.InvalidReceiptTotal in input.evaluate().errors)
    }

    @Test
    fun partsWithTheSameTags_areReportedAsDuplicates() {
        var input = PartsInput().setPartAmount(0, "100").togglePartTag(0, alcohol).addPart()
        val second = input.parts.last().id
        input = input.setPartAmount(second, "200").togglePartTag(second, alcohol)

        assertEquals(listOf(PartsError.DuplicateTags(listOf(0, second))), input.evaluate().errors)
    }

    @Test
    fun withoutReceiptTotal_recordTotalIsTheSumOfParts() {
        var input = PartsInput().setPartAmount(0, "100").togglePartTag(0, alcohol).addPart()
        input = input.setPartAmount(input.parts.last().id, "250")

        assertEquals(35000L, input.evaluate().totalCents)
        assertNull(input.remainderPartId)
    }

    @Test
    fun removePart_neverRemovesTheRemainderOrTheLastPart() {
        val receipt = ashanReceipt()
        assertEquals(receipt, receipt.removePart(0))

        val simple = PartsInput()
        assertEquals(simple, simple.removePart(0))
    }

    @Test
    fun removePart_dropsATypedPart_andRemainderGrows() {
        val receipt = ashanReceipt()
        val alcoholPart = receipt.parts.first().id

        val after = receipt.removePart(alcoholPart)

        assertEquals(315000L, after.evaluate().partCents[0])
    }

    @Test
    fun clearReceiptTotal_remainderKeepsItsAmountAsTypedValue() {
        val cleared = ashanReceipt().clearReceiptTotal()

        assertNull(cleared.receiptTotalExpression)
        assertEquals("2350.00", cleared.parts.last().amountExpression)
        assertEquals(345000L, cleared.evaluate().totalCents)
    }

    @Test
    fun clearReceiptTotal_withOnePartLeft_returnsToASimpleRecord() {
        val input = PartsInput().setPartAmount(0, "500").split()
        val onlyRemainder = input.removePart(input.parts.first().id)

        val cleared = onlyRemainder.clearReceiptTotal()

        assertFalse(cleared.isSplit)
        assertEquals("500.00", cleared.parts.single().amountExpression)
    }

    @Test
    fun fromSaved_restoresEveryPartAsTyped() {
        val input = PartsInput.fromSaved(listOf(PartDraft(80000, setOf(alcohol)), PartDraft(235000, setOf(groceries))))

        assertNull(input.receiptTotalExpression)
        assertEquals(listOf("800.00", "2350.00"), input.parts.map { it.amountExpression })
        assertEquals(315000L, input.evaluate().totalCents)
    }
}
