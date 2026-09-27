package com.violinjourney.app.feature.journey.art

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the try-on relies on (spec 3.24): a thing in front of the one tried on, shaded towards the veil's colour by the
 * veil's alpha and drawn over the veiled room, looks exactly as the thing drawn and then veiled.
 */
class SceneShadeTest {
    private fun channel(color: Long, shift: Int): Float = ((color shr shift) and 0xFF) / 255f

    /** [top] at [alpha] over [under], one channel. */
    private fun over(top: Float, alpha: Float, under: Float) = alpha * top + (1 - alpha) * under

    @Test
    fun `a shade keeps the alpha and takes the colour towards the veil`() {
        val red = 0x80FF0000L
        val shaded = ScenePalette.shade(red, 0.5f)
        assertEquals(0x80L, shaded ushr 24, "the alpha is the layer's own")
        assertEquals(ScenePalette.mix(0xFFFF0000L, ScenePalette.SHADE, 0.5f) and 0xFFFFFF, shaded and 0xFFFFFF)
        assertEquals(0x33445566L, ScenePalette.shade(0x33445566L, 0f))
        assertEquals(ScenePalette.SHADE and 0xFFFFFF, ScenePalette.shade(0x00FFFFFFL, 1f) and 0xFFFFFF)
    }

    @Test
    fun `a shaded thing over the veiled room is the thing veiled - for every alpha of the thing`() {
        val veil = 0.12f
        val colours = listOf(0xFFFFFFFFL, 0xFF000000L, 0xFFD9B26BL, 0xFF3E3652L, 0xFF7BB56AL)
        val rooms = listOf(0xFF000000L, 0xFFFFFFFFL, 0xFF5A4A40L)
        for (colour in colours) for (room in rooms) for (alpha in listOf(1f, 0.55f, 0.2f)) for (shift in listOf(16, 8, 0)) {
            val c = channel(colour, shift)
            val b = channel(room, shift)
            val s = channel(ScenePalette.SHADE, shift)
            val shaded = channel(ScenePalette.shade(colour, veil), shift)
            val now = over(shaded, alpha, over(s, veil, b))
            val then = over(s, veil, over(c, alpha, b))
            assertTrue(abs(now - then) <= 1f / 255, "colour ${colour.toString(16)} over ${room.toString(16)} at $alpha, channel $shift: $now against $then")
        }
    }
}
