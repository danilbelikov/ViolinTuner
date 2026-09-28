package com.violinjourney.app.feature.practice.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The smallest size of the number of the stepper (5.29 R3): 22 dp whatever the system font, never above it, and on the grid of 2 sp
 * down from 40 — the full size stays 40 sp exactly.
 */
class StepperSizeTest {
    @Test
    fun `at the usual font the number gets down to 22 sp`() {
        assertEquals(22f, smallestValueSp(22f))
    }

    @Test
    fun `a larger font does not raise the floor - it lies at or below 22 dp on the grid down from 40`() {
        // 22 dp at 1.3 (linear) is 16.9 sp; at 2.0 — 11 sp
        listOf(22f / 1.3f, 22f / 1.5f, 22f / 2f).forEach { floor ->
            val smallest = smallestValueSp(floor)
            assertTrue(smallest <= floor, "$smallest at the floor of $floor")
            assertTrue(floor - smallest < 2f, "not a whole step below it: $smallest at $floor")
            assertEquals(0f, (40f - smallest) % 2f, "40 is a whole number of steps up from $smallest")
        }
        assertEquals(16f, smallestValueSp(22f / 1.3f))
    }

    @Test
    fun `a smaller font never lifts the floor above the number itself`() {
        assertEquals(24f, smallestValueSp(22f / 0.9f))
        assertEquals(40f, smallestValueSp(60f))
    }
}
