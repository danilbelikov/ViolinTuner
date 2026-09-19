package com.example.violintuner.core.audio.playback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.audio.recording.AacFileEncoder
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real codec, extractor and audio output of the device: `./gradlew :app:connectedDebugAndroidTest`.
 * The files are made by the app's own encoder — what a session really is.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.cacheDir, "playback-test").apply { mkdirs() }
    private val rate = 48_000

    @After
    fun tearDown() {
        directory.deleteRecursively()
        File(context.filesDir, "waveforms").listFiles().orEmpty().filter { it.name.startsWith("test-") }.forEach { it.delete() }
    }

    /** A tone whose pitch tells the time: [firstHz] for the first half, [secondHz] for the second; quiet, then loud. */
    private fun encode(name: String, seconds: Int, firstHz: Double = 440.0, secondHz: Double = 880.0): File {
        val file = File(directory, name)
        val encoder = AacFileEncoder(file, rate)
        val hop = ShortArray(512)
        var sample = 0L
        val half = rate.toLong() * seconds / 2
        repeat(rate * seconds / hop.size) {
            for (i in hop.indices) {
                val hz = if (sample < half) firstHz else secondHz
                val level = if (sample < half) 3_000 else 12_000
                hop[i] = (sin(2 * PI * hz * sample++ / rate) * level).toInt().toShort()
            }
            assertTrue(encoder.offer(hop, hop.size))
            Thread.sleep(4)
        }
        assertTrue(encoder.finish())
        return file
    }

    /** Pitch by zero crossings — crude, and plenty to tell 440 from 880. */
    private fun pitchOf(samples: ShortArray, count: Int): Double {
        var crossings = 0
        for (i in 1 until count) if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++
        return crossings / 2.0 * rate / count
    }

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("timed out waiting for: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    @Test
    fun theDecoderHandsOutTheWholeRecordingAsMonoPcm() {
        val decoder = assertNotNullAnd(PcmDecoder.open(encode("whole.m4a", seconds = 4)))
        assertEquals(rate, decoder.sampleRate)
        assertTrue("duration ${decoder.durationUs}", decoder.durationUs in 3_900_000..4_150_000)

        val chunk = ShortArray(4_096)
        var total = 0L
        var firstPitch = 0.0
        var lastPitch = 0.0
        while (true) {
            val count = decoder.read(chunk)
            if (count == PcmDecoder.END) break
            if (total in rate..(rate + chunk.size)) firstPitch = pitchOf(chunk, count)
            if (total in (rate * 3L)..(rate * 3L + chunk.size)) lastPitch = pitchOf(chunk, count)
            total += count
        }
        decoder.release()
        assertTrue("decoded $total samples", abs(total - 4L * rate) < rate / 10)
        assertEquals(440.0, firstPitch, 15.0)
        assertEquals(880.0, lastPitch, 25.0)
    }

    @Test
    fun aSeekLandsWhereItWasAskedTo() {
        val decoder = assertNotNullAnd(PcmDecoder.open(encode("seek.m4a", seconds = 4)))
        val chunk = ShortArray(4_096)

        decoder.seekTo(3_000_000)
        assertEquals("the second half", 880.0, pitchOf(chunk, decoder.read(chunk)), 25.0)
        decoder.seekTo(500_000)
        assertEquals("back in the first", 440.0, pitchOf(chunk, decoder.read(chunk)), 15.0)

        // To the sample: what is left after a seek to 1.9 s is 2.1 s of sound, give or take a codec frame.
        decoder.seekTo(1_900_000)
        var left = 0L
        while (true) {
            val count = decoder.read(chunk)
            if (count == PcmDecoder.END) break
            left += count
        }
        decoder.release()
        assertTrue("left $left samples", abs(left - (2.1 * rate).toLong()) < 3_000)
    }

    @Test
    fun aFileThatIsNoSoundIsRefusedNotCrashedOn() {
        val junk = File(directory, "junk.m4a").apply { writeText("this is not audio") }
        assertNull(PcmDecoder.open(junk))
        assertNull(PcmDecoder.open(File(directory, "missing.m4a")))

        val player = ChainSessionPlayer(SoundConfig())
        player.load(junk)
        await("the failure to be told") { player.state.value.failed }
        assertFalse(player.state.value.ready)
        player.release()
    }

    @Test
    fun thePlayerPlaysPausesSeeksAndComesBackToTheStart() {
        val player = ChainSessionPlayer(SoundConfig())
        player.load(encode("play.m4a", seconds = 3))
        await("ready") { player.state.value.ready }
        assertTrue("duration ${player.state.value.durationMs}", player.state.value.durationMs in 2_900..3_150)

        player.play()
        await("the position to move") { player.state.value.positionMs > 300 }
        player.pause()
        Thread.sleep(200)
        val paused = player.state.value.positionMs
        Thread.sleep(400)
        assertEquals("a paused player stands still", paused, player.state.value.positionMs)

        player.seekTo(2_000)
        assertEquals(2_000L, player.state.value.positionMs)
        player.play()
        await("the end, and the way back to the start") { !player.state.value.playing && player.state.value.positionMs == 0L }
        assertTrue(player.state.value.ready)

        player.play()
        await("a second playing") { player.state.value.positionMs > 200 }
        player.release()
        assertFalse(player.state.value.ready)
    }

    @Test
    fun processingIsHeardThroughTheChainAndTheTailRingsOnAfterTheEnd() {
        val config = SoundConfig()
        val player = ChainSessionPlayer(config)
        player.setSound(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config))
        player.load(encode("hall.m4a", seconds = 2))
        await("ready") { player.state.value.ready }
        assertTrue(player.state.value.processed)
        assertNull("nothing plays yet", player.meters.value)

        val started = System.currentTimeMillis()
        player.play()
        // the very first reading falls on the silence a codec starts a file with
        await("the meters to show the sound") { (player.meters.value?.outputPeakDb ?: -200.0) > -60 }

        // Settings and A/B while it plays: nothing breaks, the state follows.
        player.setOriginal(true)
        player.setSound(SoundPresets.settingsOf(BuiltInPreset.WARM, config))
        player.setOriginal(false)
        assertFalse(player.state.value.original)

        await("the end", timeoutMs = 10_000) { !player.state.value.playing }
        val took = System.currentTimeMillis() - started
        // Two seconds of sound and the tail of a room on top — but not the six seconds of a cathedral.
        assertTrue("took $took ms", took in 2_300..6_000)
        assertNull(player.meters.value)
        player.release()
    }

    @Test
    fun theWaveformShowsWhereTheLoudPartIsAndIsKeptForNextTime() = runBlocking {
        val audio = encode("test-wave.m4a", seconds = 4)
        val waveforms = AppSessionWaveforms(context, Dispatchers.IO)
        val waveform = assertNotNullAnd(waveforms.of(audio))
        assertEquals(SessionWaveforms.BARS, waveform.size)
        val quiet = waveform.slice(10..50).average()
        val loud = waveform.slice(70..110).average()
        assertEquals("a quarter of the level", 0.25, quiet / loud, 0.05)

        val kept = File(context.filesDir, "waveforms/test-wave.m4a.wave")
        assertTrue(kept.isFile)
        audio.delete() // the second time it is read, not reckoned
        assertEquals(waveform.toList(), waveforms.of(audio)!!.toList())

        waveforms.deleteOrphans(audioNames = setOf("another.m4a"))
        assertFalse(kept.exists())
        assertNull(waveforms.of(File(directory, "junk.m4a").apply { writeText("x") }))
    }

    private fun <T : Any> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
