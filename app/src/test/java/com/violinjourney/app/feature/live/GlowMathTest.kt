package com.violinjourney.app.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowMathTest {
    @Test
    fun `an idle ring is an opaque plain outline with nothing around it`() {
        assertEquals(1f, GlowMath.outlineAlpha(0f), 0f)
        assertEquals(0f, GlowMath.outlineWhiteShare(0f), 0f)
        assertEquals(0f, GlowMath.softStrokeAlpha(0f), 0f)
        assertTrue(GlowMath.haloStops(0f, level = 1f).all { it.alpha == 0f })
        assertTrue(GlowMath.innerStops(0f).all { it.alpha == 0f })
    }

    @Test
    fun `the outline follows the handoff while a note sounds and does not jump on the way from idle`() {
        assertEquals(0.7f, GlowMath.outlineAlpha(0.25f), 1e-6f)
        assertEquals(0.84f, GlowMath.outlineAlpha(0.6f), 1e-6f)
        assertEquals(1f, GlowMath.outlineAlpha(1f), 1e-6f)
        assertEquals(0.85f, GlowMath.outlineAlpha(0.125f), 1e-6f)
        assertEquals(0.45f, GlowMath.outlineWhiteShare(1f), 1e-6f)
    }

    @Test
    fun `the soft stroke widens and strengthens with the glow`() {
        assertEquals(6f, GlowMath.softStrokeWidthDp(6f, 0f), 0f)
        assertEquals(16f, GlowMath.softStrokeWidthDp(6f, 1f), 0f)
        assertEquals(0.25f, GlowMath.softStrokeAlpha(1f), 0f)
        assertEquals(0.1f, GlowMath.softStrokeAlpha(0.4f), 1e-6f)
    }

    @Test
    fun `only the halo breathes, by four percent, and the layout knows how far it reaches`() {
        assertEquals(1.4f, GlowMath.haloRadius(0f), 0f)
        assertEquals(1.456f, GlowMath.haloRadius(1f), 1e-6f)
        assertEquals(1.456f, GlowMath.haloRadius(3f), 1e-6f)
        assertEquals(1.456f, GlowMath.EXTENT, 1e-6f)
    }

    @Test
    fun `the halo has its edge on the outline wherever the breath puts its rim`() {
        val quiet = GlowMath.haloStops(1f, level = 0f)
        assertEquals(1f / 1.4f, quiet[1].position, 1e-6f)
        assertEquals(0.32f, quiet[1].alpha, 1e-6f)
        assertEquals(0.10f, quiet[2].alpha, 1e-6f)
        assertEquals(0f, quiet.last().alpha, 0f)

        val loud = GlowMath.haloStops(1f, level = 1f)
        assertEquals(1f / 1.456f, loud[1].position, 1e-6f)
        assertTrue(loud.zipWithNext().all { (a, b) -> a.position <= b.position })
        assertEquals(0.08f, GlowMath.haloStops(0.25f, 0f)[1].alpha, 1e-6f)
    }

    @Test
    fun `the inner halo is a rim, not a fill`() {
        val stops = GlowMath.innerStops(1f)
        assertEquals(listOf(0f, 0.6f, 0.88f, 1f), stops.map { it.position })
        assertEquals(listOf(0f, 0f, 0.07f, 0.18f), stops.map { it.alpha })
    }

    @Test
    fun `a wave leaves the ring, reaches a third beyond and is gone by then`() {
        assertEquals(1f, GlowMath.waveRadius(0f), 0f)
        assertEquals(1.35f, GlowMath.waveRadius(1f), 1e-6f)
        assertEquals(0.25f, GlowMath.waveAlpha(GlowMath.WAVE_ALPHA_NEW_NOTE, 0f), 0f)
        assertEquals(0f, GlowMath.waveAlpha(GlowMath.WAVE_ALPHA_REWARD, 1f), 0f)
        assertTrue(GlowMath.waveRadius(1f) < GlowMath.EXTENT)
    }

    private fun run(from: Float, to: Float, ms: Int): Float {
        var glow = from
        repeat(ms / 10) { glow = GlowMath.follow(glow, to, 10f, riseMs = 500, fallMs = 900) }
        return glow
    }

    @Test
    fun `the glow arrives within its time, up faster than down`() {
        assertEquals(0.6f, run(0f, 0.6f, 500), 0.6f * 0.06f)
        assertTrue(run(0f, 1f, 250) > 0.7f)
        assertTrue("half a second is not enough on the way down", run(1f, 0f, 500) > 0.15f)
        assertEquals(0f, run(1f, 0f, 900), 0.06f)
    }

    @Test
    fun `a slip out of the zone for a fifth of a second leaves the ring lit`() {
        // Handoff 12h2: in tune and held, 200 ms of "near", back in tune.
        val dipped = run(1f, 0.4f, 200)
        assertTrue("was $dipped", dipped > 0.65f)
        assertTrue(run(dipped, 0.6f, 100) > 0.6f)
    }

    @Test
    fun `a target that moves every frame is still reached`() {
        // The hold grows the target from .6 to 1 over two seconds, a little every frame.
        var glow = 0f
        for (ms in 0..2_000 step 10) glow = GlowMath.follow(glow, 0.6f + 0.4f * ms / 2_000f, 10f, 500, 900)
        assertEquals(1f, glow, 0.05f)
    }

    @Test
    fun `no time, no change`() {
        assertEquals(0.3f, GlowMath.follow(0.3f, 1f, 0f, 500, 900), 0f)
        assertEquals(0.3f, GlowMath.follow(0.3f, 1f, -5f, 500, 900), 0f)
    }

    @Test
    fun `waves keep their distance in time`() {
        val gate = WaveGate(minIntervalMs = 600)
        assertTrue(gate.tryStart(1_000))
        assertFalse(gate.tryStart(1_250))
        assertFalse(gate.tryStart(1_599))
        assertTrue(gate.tryStart(1_600))
        assertFalse("a refused wave does not push the next one away", gate.tryStart(1_700))
        assertTrue(gate.tryStart(2_200))
    }
}
