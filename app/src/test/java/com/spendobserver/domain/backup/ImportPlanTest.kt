package com.spendobserver.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlanTest {

    @Test
    fun copyTitle_startsAtTwo() {
        assertEquals("домашний (2)", copyTitle("домашний", listOf("домашний")))
    }

    @Test
    fun copyTitle_skipsTakenNumbers_ignoringCase() {
        val taken = listOf("Дом", "дом (2)", "ДОМ (3)", "Работа (4)")

        assertEquals("Дом (4)", copyTitle("Дом", taken))
    }
}
