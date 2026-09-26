package com.violinjourney.app.feature.live.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Who frees the images of a still picture: a fake image counts how often it was freed, the fake maker what it was asked for. */
class KeptImageTest {
    private class Image(val mark: String) {
        var freed = 0
    }

    private val made = mutableListOf<Image>()
    private val asked = mutableListOf<String>()
    private val kept = KeptImage<String, Image> { it.freed++ }

    private fun frame(mark: String, still: Boolean): Image? = kept.frame(mark, still) { asked += it }

    /** The making of [mark] ends: its image comes, as it would from another thread. */
    private fun comes(mark: String): Image = Image(mark).also {
        made += it
        kept.made(mark, it)
    }

    /** Every image made is freed exactly once, [held] aside — the one still laid down. */
    private fun assertFreedOnce(held: Image? = null) {
        for (image in made) assertEquals(if (image === held) 0 else 1, image.freed, "image of ${image.mark}")
    }

    @Test
    fun `a still picture becomes an image once and stays one until its mark changes`() {
        assertNull(frame("A", true), "the first still frame draws the picture: its image is being made")
        assertNull(frame("A", true))
        val a = comes("A")
        repeat(3) { assertSame(a, frame("A", true)) }
        assertSame(a, frame("A", false), "the same mark is the same picture, still or not")
        assertEquals(listOf("A"), asked)
        assertNull(frame("B", false))
        assertEquals(1, a.freed)
    }

    @Test
    fun `a living picture is never made an image`() {
        for (second in 0 until 5) assertNull(frame("t$second", false))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `an image that could not be made is not tried again on every frame`() {
        frame("A", true)
        kept.made("A", null)
        repeat(3) { assertNull(frame("A", true)) }
        assertEquals(listOf("A"), asked)
    }

    @Test
    fun `a still picture made again after the light came and went`() {
        frame("A", true)
        val first = comes("A")
        frame("lamps 0.9", false)
        frame("lamps 0.5", false)
        assertEquals(1, first.freed, "the light coming back lets the dark image go")
        assertNull(frame("A", true))
        val second = comes("A")
        assertSame(second, frame("A", true))
        assertEquals(listOf("A", "A"), asked)
        assertFreedOnce(held = second)
    }

    @Test
    fun `a new box while dark replaces the image`() {
        frame("portrait", true)
        val portrait = comes("portrait")
        assertNull(frame("landscape", true), "the new box draws its picture until its own image comes")
        assertEquals(1, portrait.freed)
        val landscape = comes("landscape")
        assertSame(landscape, frame("landscape", true))
        assertEquals(listOf("portrait", "landscape"), asked)
    }

    @Test
    fun `an image that comes for a picture already gone is let go at once`() {
        frame("A", true)
        frame("B", true)
        val late = comes("A")
        assertEquals(1, late.freed)
        val b = comes("B")
        assertSame(b, frame("B", true))
        assertFreedOnce(held = b)
    }

    @Test
    fun `a picture let go while its image is being made frees the image when it comes`() {
        frame("A", true)
        kept.release()
        comes("A")
        assertFreedOnce()
    }

    @Test
    fun `every image made is freed once whatever the frames and the release`() {
        // the light going out and coming back, a turn of the phone in the dark, a late image, the screen rebuilt
        val frames = listOf("A" to true, "A" to true, "B" to false, "C" to true, "D" to true, "D" to true, "C" to true, "E" to false, "F" to true)
        for ((mark, still) in frames) {
            val before = asked.size
            frame(mark, still)
            if (asked.size > before) comes(mark)
        }
        assertEquals(listOf("A", "C", "D", "C", "F"), asked)
        comes("C")
        kept.release()
        kept.release()
        comes("F")
        assertEquals(asked.size + 2, made.size)
        assertFreedOnce()
    }
}
