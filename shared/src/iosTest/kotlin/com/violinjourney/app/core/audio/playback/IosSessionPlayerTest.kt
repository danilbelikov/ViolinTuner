package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.io.PlatformFile
import kotlin.concurrent.AtomicReference
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioEngineManualRenderingModeRealtime
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The player of recordings on iOS: a take is prepared, plays through and comes back to its start. The process of the
 * tests has no sound output, so what the speaker does is heard only on a device; the output the player takes and lets
 * go is checked here on an engine that renders by hand, pulled by the test at the pace of a real output.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionPlayerTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun `a take plays to its end and is ready to play again`() = runBlocking {
        writeTone(seconds = 0.5)
        val player = IosSessionPlayer(scope, SoundConfig())
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        assertTrue(player.state.value.durationMs in 450L..600L, "duration ${player.state.value.durationMs}")
        player.play()
        if (AVAudioEngine().outputNode.outputFormatForBus(0u).channelCount == 0u) {
            // the process of the tests has no sound output: the player says so rather than hang
            withTimeout(5.seconds) { player.state.first { it.failed } }
        } else {
            withTimeout(5.seconds) { player.state.first { it.positionMs > 200 } }
            // the end: back to the start, not playing (spec 3.10)
            withTimeout(5.seconds) { player.state.first { !it.playing } }
            assertEquals(0, player.state.value.positionMs)
        }
        player.release()
    }

    @Test
    fun `the output is let go half a second after a pause or the end and taken again on play`() = runBlocking {
        writeTone(seconds = 1.5)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        val output = scope.launch { pull(engine) }

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 150 } }
        val running = engine.value!!
        assertTrue(running.running)

        player.pause()
        val pausedAt = player.state.value.positionMs
        delay(OUTPUT_KEPT_MS)
        assertTrue(running.running, "a pause keeps the output for a while")
        awaitPaused(running)

        player.play()
        assertTrue(withTimeout(5.seconds) { player.state.first { it.positionMs != pausedAt } }.positionMs > pausedAt, "plays on from where it stopped")
        assertTrue(running.running)
        // the end: back to the start, not playing (spec 3.10), and the output goes again
        withTimeout(5.seconds) { player.state.first { !it.playing } }
        assertEquals(0, player.state.value.positionMs)
        awaitPaused(running)

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 150 } }
        assertFalse(player.state.value.failed)
        output.cancel()
        player.release()
    }

    @Test
    fun `when iOS stops the engine by itself the player pauses where the sound was and plays on from there`() = runBlocking {
        writeTone(seconds = 1.5)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        val output = scope.launch { pull(engine) }

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 300 } }
        val running = engine.value!!
        // what iOS does when headphones come or go: the engine stops, and only a notification says so
        running.stop()
        NSNotificationCenter.defaultCenter.postNotificationName(AVAudioEngineConfigurationChangeNotification, running)
        withTimeout(5.seconds) { player.state.first { !it.playing } }
        delay(OUTPUT_KEPT_MS) // the worker has taken the wish by now
        val paused = player.state.value
        assertFalse(paused.playing, "no «playing» in silence")
        assertFalse(paused.failed)
        assertTrue(paused.positionMs in 300 until paused.durationMs, "paused where the sound was, not at the start: ${paused.positionMs}")

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > paused.positionMs + 100 } }
        assertTrue(running.running, "play took the output again")
        output.cancel()
        player.release()
    }

    /** An engine with no hardware behind it: it renders only when [pull] asks it to. */
    private fun byHand(): AVAudioEngine = AVAudioEngine().also { engine ->
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val on = engine.enableManualRenderingMode(AVAudioEngineManualRenderingModeRealtime, renderFormat, RENDER_FRAMES, error.ptr)
            check(on) { "no manual rendering: ${error.value?.localizedDescription}" }
        }
    }

    /** The sound output's part: takes the engine's sound a block at a time, about as fast as a speaker would, while it runs. */
    private suspend fun pull(engine: AtomicReference<AVAudioEngine?>) {
        val buffer = AVAudioPCMBuffer(pCMFormat = renderFormat, frameCapacity = RENDER_FRAMES)
        while (kotlin.coroutines.coroutineContext.isActive) {
            val running = engine.value?.takeIf { it.running }
            if (running != null) {
                buffer.frameLength = RENDER_FRAMES
                running.manualRenderingBlock?.invoke(RENDER_FRAMES, buffer.mutableAudioBufferList, null)
            }
            delay(RENDER_EVERY_MS)
        }
    }

    private suspend fun awaitPaused(engine: AVAudioEngine) = withTimeout(3.seconds) {
        while (engine.running) delay(POLL_MS)
    }

    private fun writeTone(seconds: Double) {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat((rate * seconds).toInt() / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 8_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())
    }

    private companion object {
        val renderFormat = AVAudioFormat(standardFormatWithSampleRate = 48_000.0, channels = 2u)
        const val RENDER_FRAMES = 512u
        const val RENDER_EVERY_MS = 10L
        const val POLL_MS = 20L

        /** Well inside [IosSessionPlayer.OUTPUT_LINGER_MS]: a late worker only lets the output go later, never sooner. */
        const val OUTPUT_KEPT_MS = IosSessionPlayer.OUTPUT_LINGER_MS / 2
    }
}
