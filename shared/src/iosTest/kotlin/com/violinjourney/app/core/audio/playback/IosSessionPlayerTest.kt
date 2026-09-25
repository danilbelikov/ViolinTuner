package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVAudioEngine
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The player of recordings on iOS: a take is prepared, plays through and comes back to its start — where there is a sound output. */
@OptIn(ExperimentalForeignApi::class)
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
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(rate / 2 / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 8_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())

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
}
