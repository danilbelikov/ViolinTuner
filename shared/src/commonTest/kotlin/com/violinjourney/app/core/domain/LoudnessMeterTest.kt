package com.violinjourney.app.core.domain

import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class LoudnessMeterTest {
    private val config = IntonationConfig()

    private fun rmsOf(dbfs: Double) = 10.0.pow(dbfs / 20.0)

    @Test
    fun `the scale runs from the silence threshold to the ceiling`() {
        assertEquals(0.0, LoudnessMeter.rawLevel(rmsOf(-45.0), config), 1e-9)
        assertEquals(0.0, LoudnessMeter.rawLevel(rmsOf(-70.0), config), 1e-9)
        assertEquals(1.0, LoudnessMeter.rawLevel(rmsOf(-10.0), config), 1e-9)
        assertEquals(1.0, LoudnessMeter.rawLevel(rmsOf(-1.0), config), 1e-9)
        assertEquals(0.5, LoudnessMeter.rawLevel(rmsOf(-27.5), config), 1e-9)
    }

    @Test
    fun `exact zeros of a dead input read as nothing — not as a number`() {
        assertEquals(0.0, LoudnessMeter.rawLevel(0.0, config), 0.0)
        assertEquals(0f, LoudnessMeter(config).process(0, 0.0), 0f)
    }

    @Test
    fun `the first frame is taken as it is`() {
        assertEquals(1f, LoudnessMeter(config).process(0, rmsOf(-10.0)), 1e-6f)
    }

    @Test
    fun `it rises fast and falls slowly`() {
        val meter = LoudnessMeter(config)
        meter.process(0, rmsOf(-60.0))
        // One attack time constant: 63 % of the way up.
        val risen = meter.process(config.levelAttackMs, rmsOf(-10.0))
        assertEquals(0.632f, risen, 0.01f)

        val loud = LoudnessMeter(config)
        loud.process(0, rmsOf(-10.0))
        // The same 50 ms on the way down take off far less…
        val fallen = loud.process(config.levelAttackMs, rmsOf(-60.0))
        assertTrue(fallen > 0.8f)
        // …and one release time constant leaves 37 %.
        val released = LoudnessMeter(config).also { it.process(0, rmsOf(-10.0)) }.process(config.levelReleaseMs, rmsOf(-60.0))
        assertEquals(0.368f, released, 0.01f)
    }

    @Test
    fun `a steady sound settles at its level whatever the frame rate`() {
        val meter = LoudnessMeter(config)
        var level = 0f
        TestFrames.times(0, 2_000).forEach { level = meter.process(it, rmsOf(-27.5)) }
        assertEquals(0.5f, level, 0.001f)
    }

    @Test
    fun `a clock that stands still or runs back changes nothing`() {
        val meter = LoudnessMeter(config)
        meter.process(100, rmsOf(-60.0))
        assertEquals(0f, meter.process(100, rmsOf(-10.0)), 0f)
        assertEquals(0f, meter.process(50, rmsOf(-10.0)), 0f)
    }

    @Test
    fun `reset forgets the level`() {
        val meter = LoudnessMeter(config)
        meter.process(0, rmsOf(-10.0))
        meter.reset()
        assertEquals(0f, meter.process(10, rmsOf(-60.0)), 0f)
    }
}
