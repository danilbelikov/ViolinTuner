package com.violinjourney.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The colours of the kinds of events (spec 5.29 R9; events-kinds.html, 1): eight by the number a kind keeps — the four saturated ones a
 * tone lighter on their plate, the four light ones the colour itself — and a plate is the colour at 18 % over what it lies on, opaque;
 * none of them near a colour the app already speaks with.
 */
class EventsColorsTest {
    private val colors = DarkEventsColors
    private val ground = Color(0xFF131318)

    @Test
    fun `eight colours by their numbers and the sign on a plate lighter only for the saturated four`() {
        assertEquals(8, colors.kinds.size)
        assertEquals(Color(0xFF4D99E0), colors.of(0).color, "0 — Синий, the lesson by default")
        assertEquals(Color(0xFF5FCFDA), colors.of(5).color, "5 — Бирюза, the rehearsal")
        assertEquals(Color(0xFFF266A5), colors.of(2).color, "2 — Роза, the performance")
        assertEquals(Color(0xFFD7F092), colors.of(7).color, "7 — Лайм, «Другое»")
        (0..3).forEach { assertNotEquals(colors.of(it).color, colors.of(it).onPlate, "a saturated colour is lighter on its plate: $it") }
        (4..7).forEach { assertEquals(colors.of(it).color, colors.of(it).onPlate, "a light colour is itself on its plate: $it") }
        assertEquals(8, colors.kinds.map { it.color }.toSet().size, "no two kinds of the set share a colour")
    }

    @Test
    fun `a plate is the colour at 18 percent over its ground and the tile of the form 12`() {
        val plate = colors.plate(2, ground)
        assertEquals(1f, plate.alpha, "opaque: the sign over it keeps its contrast")
        assertEquals(colors.of(2).color.copy(alpha = 0.18f).compositeOver(ground), plate)
        assertEquals(colors.of(2).color.copy(alpha = 0.12f).compositeOver(ground), colors.tile(2, ground))
    }

    @Test
    fun `a number out of the set reads as the first colour`() {
        assertEquals(colors.of(0), colors.of(8))
        assertEquals(colors.of(0), colors.of(-1))
    }

    /**
     * No kind takes a colour the app already speaks with (spec 3.35 «Виды», 3.36.9 «Меняет», 5.29 R9; events-kinds.html, 1): the zones,
     * the violet tones of the time of practice and the accent, the flame, the brass of «сделано», and — added by 3.36.9 — the coral of
     * danger. Each of the eight stays at least [MIN_DISTANCE] away from each of them by CIEDE2000, as the table of the mockup counts it;
     * the nearest pairs are the ones 5.29 names — Пудра and the coral 16,9, Орхидея and the accent 17,7, Лайм and the brass 24,1.
     */
    @Test
    fun `the colours of the kinds keep away from every colour taken elsewhere and from the coral of danger`() {
        val zones = DarkZoneColors
        val taken = mapOf(
            "the zone in tune" to zones.inTune, "the zone near" to zones.near, "the zone off" to zones.off,
            "the time tone 1" to DarkPracticeColors.fills[0], "the time tone 2" to DarkPracticeColors.fills[1],
            "the time tone 3" to DarkPracticeColors.fills[2], "the accent" to DarkPracticeColors.fills[3],
            "the flame" to FlameOuter, "the brass" to CtrlBrass, "the coral of danger" to DangerSoft,
        )
        colors.kinds.forEachIndexed { index, kind ->
            taken.forEach { (name, colour) ->
                val distance = deltaE2000(kind.color, colour)
                assertTrue(distance >= MIN_DISTANCE, "the colour $index is $distance from $name")
            }
        }
        assertEquals(16.9, deltaE2000(colors.of(POWDER).color, DangerSoft), PRECISION, "Пудра and the coral: the nearest of the set to danger")
        assertEquals(17.7, deltaE2000(colors.of(ORCHID).color, DarkPracticeColors.fills[3]), PRECISION, "Орхидея and the accent")
        assertEquals(24.1, deltaE2000(colors.of(LIME).color, CtrlBrass), PRECISION, "Лайм and the brass")
    }

    /** CIEDE2000 between two opaque sRGB colours (CIE 142-2001; D65): the distance the table of events-kinds.html is counted in. */
    private fun deltaE2000(first: Color, second: Color): Double {
        val (l1, a1, b1) = labOf(first)
        val (l2, a2, b2) = labOf(second)
        val chromaMean = (hypot(a1, b1) + hypot(a2, b2)) / 2
        val g = 0.5 * (1 - sqrt(chromaMean.pow(7) / (chromaMean.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)
        val h1p = hueOf(b1, a1p)
        val h2p = hueOf(b2, a2p)
        val deltaHue = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val deltaH = 2 * sqrt(c1p * c2p) * sin(radians(deltaHue / 2))
        val lMean = (l1 + l2) / 2
        val cMean = (c1p + c2p) / 2
        val hMean = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(radians(hMean - 30)) + 0.24 * cos(radians(2 * hMean)) + 0.32 * cos(radians(3 * hMean + 6)) -
            0.20 * cos(radians(4 * hMean - 63))
        val rotation = 30 * exp(-((hMean - 275) / 25).pow(2))
        val rc = 2 * sqrt(cMean.pow(7) / (cMean.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lMean - 50).pow(2) / sqrt(20 + (lMean - 50).pow(2))
        val sc = 1 + 0.045 * cMean
        val sh = 1 + 0.015 * cMean * t
        val rt = -sin(radians(2 * rotation)) * rc
        val dl = (l2 - l1) / sl
        val dc = (c2p - c1p) / sc
        val dh = deltaH / sh
        return sqrt(dl * dl + dc * dc + dh * dh + rt * dc * dh)
    }

    /** CIELAB of an sRGB colour under D65. */
    private fun labOf(color: Color): Triple<Double, Double, Double> {
        fun linear(channel: Float): Double = if (channel <= 0.04045f) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(value: Double): Double = if (value > 216.0 / 24389) value.pow(1.0 / 3) else (24389.0 / 27 * value + 16) / 116
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun hueOf(b: Double, a: Double): Double = (atan2(b, a) * 180 / PI).mod(360.0)

    private fun radians(degrees: Double): Double = degrees * PI / 180

    private companion object {
        /** The nearest the spec lets a kind come to a taken colour: the zones of the mockups, 16,5 (5.29 R9). */
        const val MIN_DISTANCE = 16.5

        /** The table of the mockup gives one decimal. */
        const val PRECISION = 0.06
        const val ORCHID = 3
        const val POWDER = 6
        const val LIME = 7
    }
}
