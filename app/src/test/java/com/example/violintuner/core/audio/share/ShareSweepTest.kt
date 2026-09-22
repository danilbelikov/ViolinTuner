package com.example.violintuner.core.audio.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule that empties `cache/share` (spec 5.11). */
class ShareSweepTest {
    private val now = 1_700_000_000_000L

    private fun folder(name: String, agoMs: Long, bytes: Long = 0, heavy: Boolean = false) =
        ShareFolder(name = name, bytes = bytes, touchedAtEpochMs = now - agoMs, heavy = heavy)

    private fun mb(count: Long) = count * 1024 * 1024

    @Test
    fun `nothing prepared, nothing to sweep`() {
        assertTrue(ShareSweep.toDelete(emptyList(), now).isEmpty())
    }

    @Test
    fun `a sound waits a day, a video an hour`() {
        val folders = listOf(
            folder("sound-fresh", agoMs = 23 * HOUR),
            folder("sound-stale", agoMs = 25 * HOUR),
            folder("video-fresh", agoMs = 59 * MINUTE, heavy = true),
            folder("video-stale", agoMs = 2 * HOUR, heavy = true),
        )
        assertEquals(listOf("sound-stale", "video-stale"), ShareSweep.toDelete(folders, now).sorted())
    }

    @Test
    fun `age takes the last folder there is`() {
        assertEquals(listOf("alone"), ShareSweep.toDelete(listOf(folder("alone", agoMs = 30 * HOUR)), now))
    }

    @Test
    fun `over the budget the oldest go until what is left fits`() {
        val folders = listOf(
            folder("newest", agoMs = 1 * MINUTE, bytes = mb(300), heavy = true),
            folder("oldest", agoMs = 40 * MINUTE, bytes = mb(300), heavy = true),
            folder("older", agoMs = 20 * MINUTE, bytes = mb(300), heavy = true),
        )
        // 900 MB: the oldest goes, 600 is still too much, the next goes, 300 fits
        assertEquals(listOf("oldest", "older"), ShareSweep.toDelete(folders, now))
    }

    @Test
    fun `the newest folder is left alone however heavy it is`() {
        val folders = listOf(folder("just-sent", agoMs = 1 * MINUTE, bytes = mb(900), heavy = true))
        assertTrue(ShareSweep.toDelete(folders, now).isEmpty())
    }

    @Test
    fun `second names for takes weigh nothing and are not crowded out`() {
        val folders = (1..10).map { folder("take-$it-original", agoMs = it * MINUTE, heavy = true) }
        assertTrue(ShareSweep.toDelete(folders, now).isEmpty())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
