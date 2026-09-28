package com.violinjourney.app.feature.practice.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.datetime.YearMonth

/** The time of the month over the calendar (spec 3.36.2, «Движение»): it rolls as it grows, not when it is another number. */
class MonthRollScopeTest {
    private val september = YearMonth(2026, 9)

    @Test
    fun `the month that grows rolls`() {
        assertEquals(monthRollScope(september, loading = false, monthDays = 3), monthRollScope(september, loading = false, monthDays = 4))
    }

    @Test
    fun `another month and the first data are other numbers`() {
        assertNotEquals(monthRollScope(september, false, 3), monthRollScope(YearMonth(2026, 8), false, 3), "an arrow shows another month")
        assertNotEquals(monthRollScope(september, true, 0), monthRollScope(september, false, 3), "the first data after loading")
    }

    @Test
    fun `the first day of a month appears at its size - never rolled up from zero`() {
        assertNotEquals(monthRollScope(september, false, 0), monthRollScope(september, false, 1))
    }
}
