package com.example.violintuner.feature.live.venue

import com.example.violintuner.core.domain.Zone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueLookTest {
    private val surface = rgb(0x13, 0x13, 0x18)
    private fun rgb(r: Int, g: Int, b: Int) = floatArrayOf(r / 255f, g / 255f, b / 255f)

    /** The handoff's own `dimC`: desaturate to what is kept, then mix with the surface — per colour. */
    private fun handoff(c: FloatArray, keep: Float, mix: Float): FloatArray {
        val y = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
        return FloatArray(3) { i -> (y + (c[i] - y) * keep).let { it + (surface[i] - it) * mix } }
    }

    private fun applied(m: FloatArray, c: FloatArray) = FloatArray(3) { row ->
        ((m[row * 5] * c[0] * 255 + m[row * 5 + 1] * c[1] * 255 + m[row * 5 + 2] * c[2] * 255 + m[row * 5 + 4]) / 255f).coerceIn(0f, 1f)
    }

    @Test
    fun `with the light out every colour is what the handoff makes of it - 12 percent of the saturation, 76 of the surface`() {
        for (colour in listOf(rgb(0xE2, 0xB7, 0x4E), rgb(0x8E, 0x2F, 0x3F), rgb(0xF3, 0xEE, 0xE2), rgb(0x4E, 0x8E, 0x57))) {
            val expected = handoff(colour, keep = 0.12f, mix = 0.76f)
            assertArrayEquals(expected, VenueLook.dim(colour, 1f, surface), 1e-4f)
            assertArrayEquals(expected, applied(VenueLook.dimMatrix(1f, surface), colour), 1e-3f)
        }
    }

    @Test
    fun `with the light on nothing changes, and halfway it is halfway`() {
        val gold = rgb(0xE2, 0xB7, 0x4E)
        assertArrayEquals(gold, applied(VenueLook.dimMatrix(0f, surface), gold), 1e-4f)
        assertArrayEquals(handoff(gold, keep = 1f - 0.5f * 0.88f, mix = 0.38f), VenueLook.dim(gold, 0.5f, surface), 1e-4f)
    }

    @Test
    fun `the gold of a hall and the red of its seats stop quarrelling with the zones once the light is out`() {
        for (colour in listOf(rgb(0xE2, 0xB7, 0x4E), rgb(0x8E, 0x2F, 0x3F), rgb(0x4E, 0x8E, 0x57))) {
            val dark = VenueLook.dim(colour, 1f, surface)
            // hardly any colour left, and dark: the zone is the only colour on the screen
            assertTrue(dark.max() - dark.min() < 0.03f)
            assertTrue(dark.max() < 0.3f)
        }
    }

    @Test
    fun `the veil lives only while the light is on, the zone light only while it is out, a miss lights less`() {
        assertEquals(0.68f to 0.4624f, VenueLook.veilAlphas(0f))
        assertEquals(0f to 0f, VenueLook.veilAlphas(1f))
        assertEquals(0f to 0f, VenueLook.zoneLightAlphas(glow = 1f, darkness = 0f, zoneScale = 1f))
        val (centre, middle) = VenueLook.zoneLightAlphas(glow = 1f, darkness = 1f, zoneScale = 1f)
        assertEquals(0.22f, centre, 1e-6f)
        assertEquals(0.07f, middle, 1e-6f)
        assertEquals(0.18f * 0.6f * 0.7f, VenueLook.tintAlpha(glow = 0.6f, darkness = 1f, zoneScale = VenueLook.zoneScale(Zone.OFF)), 1e-6f)
        assertEquals(1f, VenueLook.zoneScale(Zone.IN_TUNE))
        assertEquals(1f, VenueLook.zoneScale(Zone.NEAR))
    }

    @Test
    fun `the glowing layers go down to a quarter and the untouched controls to 0,38`() {
        assertEquals(1f, VenueLook.lightAlpha(0f))
        assertEquals(0.25f, VenueLook.lightAlpha(1f))
        assertEquals(1f, VenueLook.chromeAlpha(0f))
        assertEquals(0.38f, VenueLook.chromeAlpha(1f), 1e-6f)
    }
}
