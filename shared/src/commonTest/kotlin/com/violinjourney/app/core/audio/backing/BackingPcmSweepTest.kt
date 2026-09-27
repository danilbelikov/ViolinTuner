package com.violinjourney.app.core.audio.backing

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sweep of prepared backings (spec 5.25): a ready file goes with its copy, a `.partial` only when nobody has written
 * to it for the idle time. Until 27.09.2026 a `.partial` was never swept, and one left by a process killed mid-unpack of a
 * backing removed since stayed in the cache — up to ~690 MB for an hour of sound.
 */
class BackingPcmSweepTest {
    private val now = 10 * IDLE

    private fun doomed(names: List<String>, kept: Set<String>, modified: Map<String, Long> = emptyMap()) =
        BackingPcmSweep.doomed(names, kept, now, IDLE) { modified.getValue(it) }

    @Test
    fun `a ready file goes with its backing`() {
        assertEquals(listOf("b-48000.pcm"), doomed(listOf("a-48000.pcm", "a-44100.pcm", "b-48000.pcm"), setOf("a.mp3")))
    }

    @Test
    fun `a partial being written stays whatever backing it is of`() {
        val names = listOf("a-48000.pcm.partial", "b-48000.pcm.partial")
        val modified = names.associateWith { now - 1_000 }
        assertEquals(emptyList(), doomed(names, setOf("a.mp3"), modified))
    }

    @Test
    fun `a partial nobody writes to goes after the idle time whatever backing it is of`() {
        val names = listOf("a-48000.pcm.partial", "b-48000.pcm.partial", "c-48000.pcm.partial")
        val modified = mapOf(names[0] to now - IDLE - 1, names[1] to now - IDLE - 1, names[2] to now - IDLE)
        assertEquals(names.take(2), doomed(names, setOf("a.mp3"), modified))
    }

    @Test
    fun `a uuid with dashes keeps its stem`() {
        val names = listOf("1b2c-3d4e-48000.pcm", "5f6a-7b8c-48000.pcm")
        assertEquals(listOf("5f6a-7b8c-48000.pcm"), doomed(names, setOf("1b2c-3d4e.m4a")))
    }

    private companion object {
        const val IDLE = 60 * 60_000L
    }
}
