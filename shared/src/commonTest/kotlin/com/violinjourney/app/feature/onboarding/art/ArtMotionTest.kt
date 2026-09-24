package com.violinjourney.app.feature.onboarding.art

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ArtMotionTest {
    private val eps = 1e-3f

    @Test
    fun `motions of the handoff are read`() {
        assertEquals(ArtMotion.Twinkle(3.2f, 0f), ArtMotion.parse("twinkle:3.2:0"))
        assertEquals(ArtMotion.Sink(20f, 14f), ArtMotion.parse("sink:20:14"))
        assertEquals(ArtMotion.Appear(0.4f), ArtMotion.parse("appear:.4"))
        assertEquals(ArtMotion.Walk, ArtMotion.parse("walk"))
        assertEquals(ArtMotion.Copy, ArtMotion.parse("copy"))
        assertEquals(ArtMotion.Mark(LiveDemo.Shown.SHARP), ArtMotion.parse("mark:up"))
        assertEquals(null, ArtMotion.parse(null))
    }

    @Test
    fun `a star dims to 0_3 at half its period and is full when motion is removed`() {
        val star = ArtMotion.Twinkle(4f, 1f)
        assertEquals(1f, star.alpha(0.5f, still = false), eps)
        assertEquals(0.3f, star.alpha(3f, still = false), eps)
        assertEquals(1f, star.alpha(5f, still = false), eps)
        assertEquals(1f, star.alpha(3f, still = true), eps)
    }

    @Test
    fun `what moves once ends where it is when motion is removed`() {
        assertEquals(ArtShift(0f, 14f), ArtMotion.Sink(20f, 14f).shift(0f, still = true))
        assertEquals(ArtShift(0f, 14f), ArtMotion.Sink(20f, 14f).shift(30f, still = false))
        assertEquals(ArtShift(22f, -11f), ArtMotion.Walk.shift(0f, still = true))
        assertEquals(1f, ArtMotion.Appear(1.8f).alpha(0f, still = true), eps)
        assertEquals(0f, ArtMotion.Appear(1.8f).alpha(1f, still = false), eps)
    }

    @Test
    fun `the copy is not there before it sets off and flies through the middle point into the folder`() {
        val copy = ArtMotion.Copy
        assertEquals(0f, copy.alpha(0.2f, still = false), eps)
        assertEquals(ArtShift(-120f, -100f), copy.shift(0.2f, still = false))
        val end = copy.shift(10f, still = false)
        assertEquals(0f, end.dx, eps)
        assertEquals(0f, end.dy, eps)
        val still = copy.shift(0f, still = true)
        assertEquals(0f, still.dx, eps)
        assertEquals(0f, still.dy, eps)
        assertEquals(1f, copy.alpha(0f, still = true), eps)
    }

    @Test
    fun `the phone goes round in tune — sharp — in tune — flat and crossfades at the end of each colour`() {
        assertEquals(LiveDemo.Mix(LiveDemo.Shown.IN_TUNE, LiveDemo.Shown.SHARP, 0f), LiveDemo.at(1f, still = false))
        assertEquals(0.5f, LiveDemo.at(3.8f, still = false).fraction, eps)
        assertEquals(LiveDemo.Shown.SHARP, LiveDemo.at(5f, still = false).from)
        assertEquals(LiveDemo.Shown.FLAT, LiveDemo.at(13f, still = false).from)
        assertEquals(LiveDemo.Shown.IN_TUNE, LiveDemo.at(17f, still = false).from)
        assertEquals(1f, LiveDemo.weight(LiveDemo.Shown.IN_TUNE, 13f, still = true), eps)
    }

    @Test
    fun `sharp is shown by the arrow up and flat by the arrow down — as on Live`() {
        assertEquals(1f, ArtMotion.Mark(LiveDemo.Shown.SHARP).alpha(5f, still = false), eps)
        assertEquals(0f, ArtMotion.Mark(LiveDemo.Shown.FLAT).alpha(5f, still = false), eps)
        assertEquals(1f, ArtMotion.Mark(LiveDemo.Shown.FLAT).alpha(13f, still = false), eps)
        val weights = LiveDemo.Shown.entries.sumOf { LiveDemo.weight(it, 3.8f, still = false).toDouble() }
        assertTrue(kotlin.math.abs(weights - 1.0) < 1e-4)
    }
}
