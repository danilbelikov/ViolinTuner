package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.openOutput
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineManualRenderingModeRealtime
import platform.AVFAudio.AVAudioFormat
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The backing played into the headphones during a take on iOS (spec 3.32): the moment its first frame leaves the output
 * and how much of the headphones' lag that moment holds outlive its stop — the take reads both once the backing has
 * stopped, and works the shift out of them (spec 5.25). On an engine with no hardware behind it: the process of the
 * tests has no sound output.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBackingPlaybackTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".pcm"
    private val routes = object : AudioRoutes {
        override fun current() = AudioRoute(BackingOutput.BLUETOOTH, "AirPods")

        override val changes: Flow<AudioRoute> = emptyFlow()
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun `when the backing left the output is still known after it stopped`() {
        // a second of silence, 16-bit stereo, as the cache makes it
        val silence = ByteArray(RATE * 4)
        PlatformFile(path).openOutput()!!.use { it.write(silence, 0, silence.size) }
        val playback = IosBackingPlayback(routes, newEngine = ::byHand)

        playback.start(PlatformFile(path), RATE)
        val started = assertNotNull(playback.startNanos, "the start is known once it plays")
        val held = playback.includedLatencyMs
        assertTrue(held >= 0)
        playback.stop()
        assertEquals(started, playback.startNanos, "the take reads it after the stop")
        assertEquals(held, playback.includedLatencyMs)

        // the next take starts afresh: a backing that cannot be read leaves no start of the one before
        NSFileManager.defaultManager.removeItemAtPath(path, null)
        playback.start(PlatformFile(path), RATE)
        assertNull(playback.startNanos)
        assertEquals(0, playback.includedLatencyMs)
    }

    /** An engine with no hardware behind it, as the player's tests use. */
    private fun byHand(): AVAudioEngine = AVAudioEngine().also { engine ->
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val format = AVAudioFormat(standardFormatWithSampleRate = RATE.toDouble(), channels = 2u)
            val on = engine.enableManualRenderingMode(AVAudioEngineManualRenderingModeRealtime, format, RENDER_FRAMES, error.ptr)
            check(on) { "no manual rendering: ${error.value?.localizedDescription}" }
        }
    }

    private companion object {
        const val RATE = 48_000
        const val RENDER_FRAMES = 512u
    }
}
