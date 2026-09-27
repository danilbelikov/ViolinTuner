package com.violinjourney.app.core.audio.backing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.violinjourney.app.core.audio.playback.PlaybackFocus
import com.violinjourney.app.testing.TestSound
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Listening to the backing on the piece screen (spec 3.32) with the device's `MediaPlayer`: prepared off the main thread,
 * holding the phone's sound while it plays and giving it back when it stops. Emulator only, with
 * `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
 */
@RunWith(AndroidJUnit4::class)
class MediaBackingPreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "backing-preview-test").apply { mkdirs() }
    private val main = InstrumentationRegistry.getInstrumentation()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private class FakeFocus(private val granted: Boolean = true) : PlaybackFocus {
        @Volatile var held = false

        override fun take(onLost: () -> Unit): Boolean {
            held = granted
            return granted
        }

        override fun give() {
            held = false
        }
    }

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("timed out waiting for: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    private fun tone() = TestSound.wav(File(directory, "backing.wav"), rate = 44_100, channels = 2, bits = 16, seconds = 0.5)

    @Test
    fun aPreviewPlaysToItsEndAndGivesTheSoundBack() {
        val focus = FakeFocus()
        val preview = MediaBackingPreview(focus)
        val file = tone()
        val started = System.currentTimeMillis()
        main.runOnMainSync { preview.toggle(file) }
        assertTrue("«stop» shows at once", preview.playing.value)
        assertTrue("the sound held", focus.held)
        await("the end of the backing") { !preview.playing.value }
        // played, not failed: half a second of sound takes about that long to come out
        assertTrue("ended after ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started > 300)
        assertFalse("the sound given back", focus.held)
    }

    @Test
    fun aPreviewStoppedWhileItIsPreparedStaysQuiet() {
        val focus = FakeFocus()
        val preview = MediaBackingPreview(focus)
        val file = tone()
        main.runOnMainSync {
            preview.toggle(file)
            preview.toggle(file)
        }
        assertFalse(preview.playing.value)
        assertFalse(focus.held)
        Thread.sleep(700)
        assertFalse("no late start", preview.playing.value)
    }

    @Test
    fun aRefusedSoundLeavesThePreviewQuiet() {
        val preview = MediaBackingPreview(FakeFocus(granted = false))
        val file = tone()
        main.runOnMainSync { preview.toggle(file) }
        assertFalse(preview.playing.value)
    }

    @Test
    fun aFileThatIsNoSoundEndsTheTryNotTheApp() {
        val focus = FakeFocus()
        val preview = MediaBackingPreview(focus)
        val junk = File(directory, "junk.mp3").apply { writeText("this is not audio") }
        main.runOnMainSync { preview.toggle(junk) }
        await("the failure to stop it") { !preview.playing.value }
        assertFalse(focus.held)
    }
}
