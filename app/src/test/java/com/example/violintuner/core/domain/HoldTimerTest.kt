package com.example.violintuner.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldTimerTest {
    private val timer = HoldTimer(IntonationConfig())

    @Test
    fun `fills in exactly two seconds and stays full`() {
        assertEquals(0.0, timer.update(1_000, inTune = true), EPS)
        assertEquals(0.5, timer.update(2_000, inTune = true), EPS)
        assertEquals(1.0, timer.update(3_000, inTune = true), EPS)
        assertEquals(1.0, timer.update(5_000, inTune = true), EPS)
    }

    @Test
    fun `leaving the zone resets the fill`() {
        timer.update(0, inTune = true)
        timer.update(1_500, inTune = true)
        assertEquals(0.0, timer.update(1_510, inTune = false), EPS)
        assertEquals(0.0, timer.update(1_520, inTune = true), EPS)
        assertEquals(0.25, timer.update(2_020, inTune = true), EPS)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
