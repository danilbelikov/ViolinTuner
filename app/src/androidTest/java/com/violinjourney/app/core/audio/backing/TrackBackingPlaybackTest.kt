package com.violinjourney.app.core.audio.backing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real audio output of the device (spec 3.32): a backing that ends before the take has its bar grow to full and stand
 * there. Until 27.09.2026 the end of the file stopped the track, `stop()` zeroed its head, and the bar fell to 0:00 for a
 * second. Emulator only, with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
 */
@RunWith(AndroidJUnit4::class)
class TrackBackingPlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "backing-playback-test").apply { mkdirs() }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** Half a second of a quiet tone, as the backing cache makes it: 16-bit stereo, interleaved. */
    private fun pcm(rate: Int, ms: Int): File {
        val frames = rate * ms / 1_000
        val bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val value = (sin(2 * PI * 440.0 * i / rate) * 3_000).toInt().toShort()
            bytes.putShort(value)
            bytes.putShort(value)
        }
        return File(directory, "tone.pcm").apply { writeBytes(bytes.array()) }
    }

    @Test
    fun theBarNeverGoesBackAndStandsFullAtTheEnd() {
        val playback = TrackBackingPlayback(FakeHeadphoneRoutes())
        playback.start(pcm(RATE, MS), RATE)
        val seen = mutableListOf<Long>()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            playback.position.value?.let { if (seen.lastOrNull() != it) seen += it }
            if (seen.lastOrNull() == MS.toLong()) break
            Thread.sleep(5)
        }
        // the end reached: a while longer, in which the bar must not move
        repeat(40) {
            playback.position.value?.let { if (seen.lastOrNull() != it) seen += it }
            Thread.sleep(5)
        }
        val played = playback.stop()

        assertTrue("the bar went back: $seen", seen.zipWithNext().all { (a, b) -> a <= b })
        assertEquals("the bar stands full: $seen", MS.toLong(), seen.last())
        assertEquals(MS.toLong(), played)
    }

    private companion object {
        const val RATE = 48_000
        const val MS = 500
    }
}
