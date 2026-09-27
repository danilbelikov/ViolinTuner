package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.IosAudioSession
import com.violinjourney.app.core.audio.recording.Tone
import com.violinjourney.app.core.audio.recording.writeAacTones
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.openOutput
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
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
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioEngineManualRenderingModeRealtime
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.usleep

/**
 * The player of recordings on iOS: a take is prepared, plays through and comes back to its start, goes where a seek sends
 * it, and sounds both as it was recorded and processed (A/B). The process of the tests has no sound output, so what the
 * speaker does is heard only on a device; the output the player takes and lets go, and what goes into it, are checked
 * here on an engine that renders by hand, pulled by the test at the pace of a real output.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionPlayerTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"
    private val wavPath = NSTemporaryDirectory() + NSUUID().UUIDString + ".wav"
    private val pcmPath = NSTemporaryDirectory() + NSUUID().UUIDString + ".pcm"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        listOf(path, wavPath, pcmPath).forEach { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }

    @Test
    fun `a take plays to its end and is ready to play again`() = runBlocking {
        writeTone(seconds = 0.5)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig())
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
    fun `a seek back while playing plays on from the new place`() = runBlocking {
        writeTone(seconds = 2.0)
        val (player, output) = playing()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 900 } }

        // every place the ear is told from now until the end of the take
        val told = mutableListOf<Long>()
        val watch = scope.launch(start = CoroutineStart.UNDISPATCHED) { player.state.takeWhile { it.playing }.collect { told += it.positionMs } }
        player.seekTo(200)
        withTimeout(10.seconds) { watch.join() }
        // a player that did not go back would tell nothing below where it was until its end
        assertTrue(told.any { it in 300L..800L }, "played on from 200: $told")
        assertEquals(0, player.state.value.positionMs, "and back to the start at the end")
        assertFalse(player.state.value.failed)
        output.cancel()
        player.release()
    }

    @Test
    fun `a seek while paused waits there and play goes on from it`() = runBlocking {
        writeTone(seconds = 2.0)
        val (player, output) = playing()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 900 } }
        player.pause()
        player.seekTo(100)
        assertEquals(100, player.state.value.positionMs)
        assertFalse(player.state.value.playing)

        val told = mutableListOf<Long>()
        val watch = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            player.state.first { state ->
                if (state.playing) told += state.positionMs
                state.playing && state.positionMs > 850
            }
        }
        player.play()
        withTimeout(10.seconds) { watch.join() }
        // a player that forgot the seek would go on from past 900 at once
        val back = told.indexOfFirst { it in 150L..600L }
        assertTrue(back >= 0 && back < told.indexOfFirst { it > 850 }, "played on from 100: $told")
        output.cancel()
        player.release()
    }

    @Test
    fun `A and B both play on and both are heard`() = runBlocking {
        writeTone(seconds = 2.0)
        val peak = Peak()
        val (player, output) = playing(peak) {
            it.setSound(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, SoundConfig()))
        }
        assertTrue(player.state.value.processed, "${player.state.value}")
        withTimeout(5.seconds) { player.state.first { it.positionMs > 150 } }

        suspend fun heardAfterSwitch(original: Boolean) {
            player.setOriginal(original)
            assertEquals(original, player.state.value.original)
            // the chunks queued before the switch and the crossfade go by first
            val switchedAt = player.state.value.positionMs
            withTimeout(5.seconds) { player.state.first { it.positionMs >= switchedAt + SETTLE_MS } }
            peak.reset()
            val from = player.state.value.positionMs
            withTimeout(5.seconds) { player.state.first { it.positionMs >= from + LISTEN_MS } }
            assertTrue(peak.value > HEARD, "the ${if (original) "original" else "processed"} sound is heard: ${peak.value}")
        }
        heardAfterSwitch(original = true)
        heardAfterSwitch(original = false)

        withTimeout(10.seconds) { player.state.first { !it.playing } }
        assertFalse(player.state.value.failed)
        assertEquals(0, player.state.value.positionMs)
        output.cancel()
        player.release()
    }

    /** A player of the tone on an engine rendered by hand, pulled by the test, playing; [prepare] is told before play. */
    private suspend fun playing(peak: Peak? = null, prepare: (IosSessionPlayer) -> Unit = {}): Pair<IosSessionPlayer, Job> {
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        prepare(player)
        val output = scope.launch { pull(engine, peak = peak) }
        player.play()
        return player to output
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

    @Test
    fun `a call pauses the player where it was and play goes on from there`() = runBlocking {
        pausedFromOutside(
            AVAudioSessionInterruptionNotification!!,
            mapOf<Any?, Any?>(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = AVAudioSessionInterruptionTypeBegan)),
        )
    }

    @Test
    fun `headphones taken off pause the player where it was and play goes on from there`() = runBlocking {
        pausedFromOutside(
            AVAudioSessionRouteChangeNotification!!,
            mapOf<Any?, Any?>(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable)),
        )
    }

    /** What iOS says of a call or of headphones taken off, said while the take plays: a pause where the ear was. */
    private suspend fun pausedFromOutside(name: String, userInfo: Map<Any?, *>) {
        writeTone(seconds = 1.5)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        val output = scope.launch { pull(engine) }

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 300 } }
        NSNotificationCenter.defaultCenter.postNotificationName(name, null, userInfo)
        withTimeout(5.seconds) { player.state.first { !it.playing } }
        delay(OUTPUT_KEPT_MS) // the worker has taken the wish by now
        val paused = player.state.value
        assertFalse(paused.playing, "no «playing» in silence")
        assertFalse(paused.failed)
        assertTrue(paused.positionMs in 300 until paused.durationMs, "paused where the sound was, not at the start: ${paused.positionMs}")

        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > paused.positionMs + 100 } }
        output.cancel()
        player.release()
    }

    @Test
    fun `the session is kept while another recording is loaded and let go when the player is`() = runBlocking {
        writeTone(seconds = 1.5)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        val before = IosAudioSession.count
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        val output = scope.launch { pull(engine) }
        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 150 } }
        assertEquals(before + 1, IosAudioSession.count, "the first play takes the session")

        // «Слушать на…» on «Звук»: another recording in the same player — other apps' music is not let back in between
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        delay(LET_GO_MS) // the worker of the file before has ended by now
        assertEquals(before + 1, IosAudioSession.count, "another file keeps the session")
        player.play()
        withTimeout(5.seconds) { player.state.first { it.positionMs > 150 } }
        assertEquals(before + 1, IosAudioSession.count, "and plays in it without taking it twice")

        player.release()
        withTimeout(3.seconds) { while (IosAudioSession.count != before) delay(POLL_MS) }
        output.cancel()
    }

    @Test
    fun `a file let go while it waited for the session leaves the play of the next file alone`() = runBlocking {
        writeTone(seconds = 1.5)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        val output = scope.launch { pull(engine) }
        // another user opening the session slowly: the player's first play waits for it inside its own opening
        val inside = AtomicInt(0)
        val opening = AtomicInt(1)
        val other = scope.launch {
            IosAudioSession.enter { _ ->
                inside.value = 1
                while (opening.value == 1) usleep(POLL_US)
            }
        }
        withTimeout(5.seconds) { while (inside.value == 0) delay(POLL_MS) }

        player.play()
        delay(LET_GO_MS) // the first file's worker waits at the session by now
        // «Слушать на…» on «Звук»: another recording picked and played while that worker still waits
        player.load(PlatformFile(path))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.ready, "${player.state.value}")
        player.play()
        opening.value = 0
        other.join()

        withTimeout(5.seconds) { player.state.first { it.positionMs > 300 } }
        assertTrue(player.state.value.playing, "${player.state.value}")
        output.cancel()
        player.release()
        IosAudioSession.leave() // the other user's
    }

    @Test
    fun `a player let go while the backing is made shows nothing of it`() = runBlocking {
        writeTone(seconds = 0.5)
        val open = AtomicInt(0)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig())
        // an unpack that cannot be cut short: it ends only when the test says so, after the player is let go
        player.loadWithBacking(
            PlatformFile(path),
            PlayerBacking(pcm = { while (open.value == 0) usleep(POLL_US); null }, offsetMs = 0, gainDb = 0f),
        )
        withTimeout(5.seconds) { player.state.first { it.preparingBacking } }
        player.release()
        open.value = 1
        delay(LET_GO_MS)
        val state = player.state.value
        assertFalse(state.ready, "the file let go does not come back as ready: $state")
        assertFalse(state.preparingBacking, "$state")
        assertFalse(state.failed, "$state")
    }

    @Test
    fun `the backing sounds against the violin it was recorded with as in the file that is sent`() = runBlocking {
        // a click in the take and one in the backing at the same moment, the shift zero: one frame must carry both
        writeClickWav(at = CLICK_AT, frames = TAKE_FRAMES, value = VIOLIN_CLICK)
        writeClickPcm(at = CLICK_AT, frames = TAKE_FRAMES, value = BACKING_CLICK)
        val backing = PlatformFile(pcmPath)
        val engine = AtomicReference<AVAudioEngine?>(null)
        val player = IosSessionPlayer(scope, SoundConfig(), BackingConfig()) { byHand().also { engine.value = it } }
        player.loadWithBacking(PlatformFile(wavPath), PlayerBacking(pcm = { backing }, offsetMs = 0, gainDb = 0f, cached = { backing }))
        withTimeout(5.seconds) { player.state.first { it.ready || it.failed } }
        assertTrue(player.state.value.hasBacking, "${player.state.value}")
        val heard = FloatArrayList()
        val output = scope.launch { pull(engine, heard) }
        player.play()
        // half a second of the output: the take's first tenth of a second is well inside it
        withTimeout(10.seconds) { while (heard.size < TAKE_FRAMES) delay(POLL_MS) }
        output.cancelAndJoin()
        player.release()

        val peaks = (0 until heard.size).filter { abs(heard[it]) > PEAK_FLOOR }
        assertEquals(1, peaks.size, "one frame with both clicks, not two apart: ${peaks.map { it to heard[it] }}")
        assertTrue(abs(heard[peaks.single()] - (VIOLIN_CLICK + BACKING_CLICK)) < 0.01f, "${heard[peaks.single()]}")
    }

    /** A take of 16-bit mono PCM at 48 kHz, silent but for one sample of [value] at [at]. */
    private fun writeClickWav(at: Int, frames: Int, value: Float) {
        val data = frames * 2
        val bytes = ByteArray(WAV_HEADER + data)
        fun int(offset: Int, v: Int, size: Int) {
            for (i in 0 until size) bytes[offset + i] = (v shr (8 * i)).toByte()
        }
        "RIFF".encodeToByteArray().copyInto(bytes, 0)
        int(4, WAV_HEADER - 8 + data, 4)
        "WAVEfmt ".encodeToByteArray().copyInto(bytes, 8)
        int(16, 16, 4)
        int(20, 1, 2) // PCM
        int(22, 1, 2) // mono
        int(24, RATE, 4)
        int(28, RATE * 2, 4)
        int(32, 2, 2)
        int(34, 16, 2)
        "data".encodeToByteArray().copyInto(bytes, 36)
        int(40, data, 4)
        int(WAV_HEADER + at * 2, (value * FULL_SCALE).roundToInt(), 2)
        PlatformFile(wavPath).openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    /** The backing's sound as the cache makes it: 16-bit stereo, interleaved, one frame of [value] at [at]. */
    private fun writeClickPcm(at: Int, frames: Int, value: Float) {
        val bytes = ByteArray(frames * 4)
        val sample = (value * FULL_SCALE).roundToInt()
        for (channel in 0..1) {
            bytes[at * 4 + channel * 2] = sample.toByte()
            bytes[at * 4 + channel * 2 + 1] = (sample shr 8).toByte()
        }
        PlatformFile(pcmPath).openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    /** Samples of the left channel as the output takes them; one writer (the pull), one reader after it is cancelled. */
    private class FloatArrayList {
        private var values = FloatArray(1 shl 16)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        operator fun get(index: Int): Float = values[index]
    }

    /** An engine with no hardware behind it: it renders only when [pull] asks it to. */
    private fun byHand(): AVAudioEngine = AVAudioEngine().also { engine ->
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val on = engine.enableManualRenderingMode(AVAudioEngineManualRenderingModeRealtime, renderFormat, RENDER_FRAMES, error.ptr)
            check(on) { "no manual rendering: ${error.value?.localizedDescription}" }
        }
    }

    /** The loudest sample of the left channel the output took since the last [reset]; one writer, the pull. */
    private class Peak {
        private val bits = AtomicInt(0)

        fun add(sample: Float) {
            val level = abs(sample)
            while (true) {
                val old = bits.value
                if (Float.fromBits(old) >= level || bits.compareAndSet(old, level.toRawBits())) return
            }
        }

        fun reset() {
            bits.value = 0
        }

        val value: Float get() = Float.fromBits(bits.value)
    }

    /**
     * The sound output's part: takes the engine's sound a block at a time, about as fast as a speaker would, while it runs;
     * what it took of the left channel goes into [heard], its loudest sample into [peak].
     */
    private suspend fun pull(engine: AtomicReference<AVAudioEngine?>, heard: FloatArrayList? = null, peak: Peak? = null) {
        val buffer = AVAudioPCMBuffer(pCMFormat = renderFormat, frameCapacity = RENDER_FRAMES)
        while (kotlin.coroutines.coroutineContext.isActive) {
            val running = engine.value?.takeIf { it.running }
            if (running != null) {
                buffer.frameLength = RENDER_FRAMES
                running.manualRenderingBlock?.invoke(RENDER_FRAMES, buffer.mutableAudioBufferList, null)
                val left = buffer.floatChannelData?.get(0)
                if (left != null) {
                    for (i in 0 until buffer.frameLength.toInt()) {
                        heard?.add(left[i])
                        peak?.add(left[i])
                    }
                }
            }
            delay(RENDER_EVERY_MS)
        }
    }

    private suspend fun awaitPaused(engine: AVAudioEngine) = withTimeout(3.seconds) {
        while (engine.running) delay(POLL_MS)
    }

    private fun writeTone(seconds: Double) = writeAacTones(path, RATE, listOf(Tone(440.0, seconds)), amplitude = 8_000.0)

    private companion object {
        val renderFormat = AVAudioFormat(standardFormatWithSampleRate = 48_000.0, channels = 2u)
        const val RENDER_FRAMES = 512u
        const val RENDER_EVERY_MS = 10L
        const val SETTLE_MS = 250L
        const val LISTEN_MS = 150L
        const val HEARD = 0.01f
        const val POLL_MS = 20L
        const val POLL_US = 1_000u
        const val LET_GO_MS = 500L
        const val RATE = 48_000
        const val WAV_HEADER = 44
        const val FULL_SCALE = 32_768f
        const val TAKE_FRAMES = 24_000
        const val CLICK_AT = 4_800
        const val VIOLIN_CLICK = 0.25f
        const val BACKING_CLICK = 0.5f
        const val PEAK_FLOOR = 0.1f

        /** Well inside [IosSessionPlayer.OUTPUT_LINGER_MS]: a late worker only lets the output go later, never sooner. */
        const val OUTPUT_KEPT_MS = IosSessionPlayer.OUTPUT_LINGER_MS / 2
    }
}
