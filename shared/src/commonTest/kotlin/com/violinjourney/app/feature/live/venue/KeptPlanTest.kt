package com.violinjourney.app.feature.live.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeptPlanTest {
    private val plan = KeptPlan<String>()

    /** The marks asked for over [frames] — a mark and whether it stands still. */
    private fun asked(vararg frames: Pair<String, Boolean>): List<String> = frames.filter { (mark, still) -> plan.makes(mark, still) }.map { it.first }

    @Test
    fun `a still picture is tried once while its mark stands`() {
        assertEquals(listOf("A"), asked("A" to true, "A" to true, "A" to true, "A" to false, "A" to true))
    }

    @Test
    fun `a living picture is never tried`() {
        assertEquals(emptyList<String>(), asked("t1" to false, "t2" to false, "t2" to false, "t3" to false))
    }

    @Test
    fun `a still picture made again after the light came and went`() {
        // out: A still; the light comes (marks change), goes out again at the same mark — «убрать анимации», or the same frozen second
        assertEquals(listOf("A", "A"), asked("A" to true, "lamps 0.9" to false, "lamps 0.5" to false, "A" to true, "A" to true))
    }

    @Test
    fun `a new box while dark replaces the image`() {
        assertEquals(listOf("portrait", "landscape"), asked("portrait" to true, "landscape" to true, "landscape" to true))
        assertFalse(plan.wants("portrait"), "the image of the old box is not the picture any more")
        assertTrue(plan.wants("landscape"))
    }

    @Test
    fun `an image is wanted only while no frame of another mark came`() {
        asked("A" to true)
        assertTrue(plan.wants("A"))
        asked("A" to true)
        assertTrue(plan.wants("A"), "more frames of the same picture keep it wanted")
        asked("B" to false)
        assertFalse(plan.wants("A"))
        assertFalse(plan.wants("B"), "a living picture was never asked for")
    }

    @Test
    fun `a picture that left the screen wants nothing`() {
        asked("A" to true)
        plan.forget()
        assertFalse(plan.wants("A"))
    }
}
